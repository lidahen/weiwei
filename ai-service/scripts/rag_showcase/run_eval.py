"""RAG showcase 评测 CLI。

离线（默认）：只校验 golden set 的结构与覆盖率，不加载 embedding。
在线：构建/加载索引，跑 B0–B3 baseline；默认再跑一次 B3 + 生成的全链路指标。

示例：
    py scripts/rag_showcase/run_eval.py
    py scripts/rag_showcase/run_eval.py --online --local-embedding --no-generate
    py scripts/rag_showcase/run_eval.py --online --local-embedding --limit 10
    py scripts/rag_showcase/run_eval.py --online --llm-delay 4.5
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO_ROOT = ROOT.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.config import settings  # noqa: E402
from app.services.rag_showcase.corpus import load_corpus  # noqa: E402
from app.services.rag_showcase.eval import (  # noqa: E402
    BASELINES,
    PacedRetryLLM,
    load_cases,
    render_markdown,
    run_full_eval,
    run_retrieval_eval,
    validate_cases,
)
from app.services.rag_showcase.index import RulebookIndex  # noqa: E402
from app.services.rag_showcase.local_embedding import LocalLsaEmbedder  # noqa: E402

DEFAULT_CASES = ROOT / "tests" / "rag_eval" / "cases.jsonl"
DEFAULT_OUTPUT = REPO_ROOT / "docs" / "rag-showcase"


def _resolve(path: str | Path) -> Path:
    candidate = Path(path)
    return candidate if candidate.is_absolute() else (ROOT / candidate)


async def _no_rerank(query: str, documents: list[str]) -> None:
    return None


def _print_distribution(cases: list[dict], errors: list[str]) -> None:
    print(f"cases: {len(cases)}")
    for case_type, count in sorted(Counter(case.get("type") for case in cases).items()):
        print(f"  {case_type}: {count}")
    if errors:
        print("validation: FAILED")
        for error in errors:
            print(f"  - {error}")
    else:
        print("validation: OK")


async def _run_online(args, cases: list[dict]) -> dict:
    corpus_dir = _resolve(args.corpus_dir)
    index_dir = _resolve(args.index_dir)
    snapshot = load_corpus(corpus_dir)
    llm = None

    if args.local_embedding:
        if not snapshot.chunks:
            raise SystemExit("corpus is empty")
        local = LocalLsaEmbedder().fit([chunk.content for chunk in snapshot.chunks])
        embedding_mode = "local-lsa-zh-v1 (offline fallback, not bge-m3)"
        index_dir = index_dir / "local"
        index = RulebookIndex(
            corpus_dir,
            index_dir,
            embedder=local.aembed_documents,
            embedding_model="local-lsa-zh-v1",
        )
        embedder = local.aembed_query
        reranker = _no_rerank
        rerank_mode = "unavailable (offline fallback)"
    else:
        if not settings.embedding_api_key:
            print("EMBEDDING_API_KEY is missing; use --local-embedding for offline eval", file=sys.stderr)
            raise SystemExit(2)
        from app.services.embedding_service import embed_query

        embedding_mode = settings.embedding_model
        index = RulebookIndex(corpus_dir, index_dir)
        embedder = embed_query
        reranker = None
        rerank_mode = (
            settings.rerank_model
            if settings.rerank_enabled and settings.embedding_api_key
            else "unavailable"
        )

    # 限速包一层 LLM（离线 / 在线 embedding 都需要，离线模式同样会调生成）。
    if args.llm_delay > 0 and not args.no_generate:
        from app.services.agent.llm import _get_llm

        llm = PacedRetryLLM(
            _get_llm(),
            min_interval=args.llm_delay,
            max_retries=args.llm_max_retries,
        )

    manifest = await index.build(force=args.rebuild)
    await index.load()
    print(
        f"index: {index.index_dir} | chunks={manifest.get('chunk_count')} | "
        f"model={manifest.get('embedding_model')}"
    )

    retrieval = await run_retrieval_eval(
        index,
        cases,
        BASELINES,
        embedder=embedder,
        reranker=reranker,
    )

    full = None
    if not args.no_generate:
        full = await run_full_eval(
            index,
            cases,
            config=BASELINES["B3_hybrid_rerank"],
            embedder=embedder,
            reranker=reranker,
            llm=llm,
            generate=True,
        )

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "case_count": len(cases),
        "embedding_mode": embedding_mode,
        "rerank_mode": rerank_mode,
        "llm_delay_s": args.llm_delay,
        "llm_max_retries": args.llm_max_retries,
        "index_chunk_count": manifest.get("chunk_count"),
        "corpus_hash": manifest.get("corpus_hash"),
        "configs": retrieval["configs"],
        "full": full,
        "failures": retrieval["failures"] + (full["failures"] if full else []),
    }


def _print_summary(report: dict) -> None:
    print("\nretrieval baselines:")
    for name, block in report["configs"].items():
        metrics = block["metrics"]
        print(
            f"  {name}: R@1={metrics['recall_at_1']} R@5={metrics['recall_at_5']} "
            f"MRR={metrics['mrr']} nDCG@5={metrics['ndcg_at_5']} "
            f"refusal={metrics['refusal_accuracy']} p50={metrics['p50_latency_ms']}ms"
        )
    full = report.get("full")
    if full:
        metrics = full["metrics"]
        print(
            f"full: refusal={metrics['refusal_accuracy']} "
            f"citation_precision={metrics['citation_precision']} "
            f"keyword_recall={metrics['keyword_recall']} "
            f"injection_violation={metrics['forbidden_violation_rate']}"
        )
    print(f"failures: {len(report['failures'])}")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:  # noqa: BLE001 —— 老终端不支持时保持默认
        pass

    parser = argparse.ArgumentParser(description="RAG showcase evaluation")
    parser.add_argument("--online", action="store_true", help="build/load index and run baselines")
    parser.add_argument("--local-embedding", action="store_true", help="use offline TF-IDF+SVD embedder")
    parser.add_argument("--no-generate", action="store_true", help="skip B3 generation metrics")
    parser.add_argument("--limit", type=int, default=0, help="evaluate only the first N cases")
    parser.add_argument("--rebuild", action="store_true", help="force index rebuild")
    parser.add_argument(
        "--llm-delay",
        type=float,
        default=0.0,
        help="seconds between LLM calls (use 4.5+ when hitting free-tier 429)",
    )
    parser.add_argument("--llm-max-retries", type=int, default=5, help="429 retries for the eval LLM")
    parser.add_argument("--cases", default=str(DEFAULT_CASES))
    parser.add_argument("--corpus-dir", default=settings.rag_showcase_corpus_path)
    parser.add_argument("--index-dir", default=settings.rag_showcase_index_dir)
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    cases = load_cases(Path(args.cases))
    snapshot = load_corpus(_resolve(args.corpus_dir))
    errors = validate_cases(cases, snapshot.chunks)
    _print_distribution(cases, errors)
    if errors:
        return 1
    if args.limit > 0:
        cases = cases[: args.limit]
        print(f"limited to {len(cases)} cases")

    if not args.online:
        print("offline mode: validation only (pass --online to run baselines)")
        return 0

    report = asyncio.run(_run_online(args, cases))
    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = REPO_ROOT / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / "eval-report.json"
    md_path = output_dir / "eval-report.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    md_path.write_text(render_markdown(report), encoding="utf-8")
    _print_summary(report)
    print(f"\nwrote {json_path}\nwrote {md_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
