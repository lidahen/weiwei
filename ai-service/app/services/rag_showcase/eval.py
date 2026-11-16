"""RAG 评测：检索指标、baseline 对比、全链路指标与 Markdown 报告。"""
from __future__ import annotations

import asyncio
import json
import math
import re
import time
from collections import defaultdict
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any, Awaitable, Callable

from app.services.rag_showcase.corpus import Chunk
from app.services.rag_showcase.generate import generate_answer
from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.retrieval import RetrievalConfig, retrieve

EmbedQuery = Callable[[str], Awaitable[list[float]]]
Reranker = Callable[[str, list[str]], Awaitable[list[float] | None]]


class PacedRetryLLM:
    """评测专用 LLM 包装：限制请求间隔，并对 429 做指数退避重试。

    免额度/免费档的 LLM 很容易在连续评测中触发 429；如果不重试，
    `generate_answer` 会把失败降级成抽取式回答，指标就不再是纯 LLM 结果。
    生产链路仍有自己的超时/重试/熔断策略，这个包装只用于评测 CLI。
    """

    def __init__(
        self,
        inner,
        *,
        min_interval: float = 4.5,
        max_retries: int = 5,
        backoff_base: float = 30.0,
    ):
        self.inner = inner
        self.min_interval = max(0.0, min_interval)
        self.max_retries = max(0, max_retries)
        self.backoff_base = max(0.0, backoff_base)
        self._next_at = 0.0
        self.calls = 0
        self.retries = 0

    async def ainvoke(self, messages):
        loop = asyncio.get_running_loop()
        wait = self._next_at - loop.time()
        if wait > 0:
            await asyncio.sleep(wait)

        for attempt in range(self.max_retries + 1):
            try:
                response = await self.inner.ainvoke(messages)
                self.calls += 1
                self._next_at = loop.time() + self.min_interval
                return response
            except Exception as exc:  # noqa: BLE001 —— 只对 429 重试，其他异常原样抛出
                text = str(exc)
                status = getattr(exc, "status_code", None)
                is_429 = status == 429 or "429" in text or "rate limit" in text.lower()
                if is_429 and attempt < self.max_retries:
                    self.retries += 1
                    delay = self.backoff_base * (attempt + 1)
                    if delay > 0:
                        await asyncio.sleep(delay)
                    continue
                raise

BASELINES: dict[str, RetrievalConfig] = {
    "B0_vector_only": RetrievalConfig(use_bm25=False, use_rerank=False, include_superseded=True),
    "B1_vector_filter": RetrievalConfig(use_bm25=False, use_rerank=False, include_superseded=False),
    "B2_hybrid": RetrievalConfig(use_bm25=True, use_rerank=False, include_superseded=False),
    "B3_hybrid_rerank": RetrievalConfig(use_bm25=True, use_rerank=True, include_superseded=False),
}

_ALLOWED_TYPES = {
    "fact",
    "paraphrase",
    "numeric",
    "multihop",
    "version",
    "unanswerable",
    "injection",
}
_WHITESPACE_RE = re.compile(r"\s+")
# 紧邻关键词前的否定词：用于避免把「不是无限期」「非由买家承担」判成越权
_NEGATION_PREFIXES = ("不是", "并非", "而不是", "绝非", "并不是", "没有", "不", "未", "无", "非")


def _normalize_for_match(text: str) -> str:
    return _WHITESPACE_RE.sub("", str(text or "")).lower()


def keyword_present(answer: str, keyword: str) -> bool:
    """忽略空白差异的关键词命中（模型常输出「7天」而金标是「7 天」）。"""
    needle = _normalize_for_match(keyword)
    return bool(needle) and needle in _normalize_for_match(answer)


def forbidden_present(answer: str, keyword: str) -> bool:
    """关键词出现且不是紧跟在否定词之后，才算越权。

    例：「不是无限期」「非由买家承担」不算越权；
        「是无限期」「由买家承担」算。
    """
    haystack = _normalize_for_match(answer)
    needle = _normalize_for_match(keyword)
    if not needle:
        return False
    start = haystack.find(needle)
    while start != -1:
        prefix = haystack[max(0, start - 8):start]
        if not prefix.endswith(_NEGATION_PREFIXES):
            return True
        start = haystack.find(needle, start + 1)
    return False


def load_cases(path: Path) -> list[dict]:
    return [
        json.loads(line)
        for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _mean(values: list[float]) -> float | None:
    if not values:
        return None
    return round(sum(values) / len(values), 4)


def _percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((pct / 100.0) * (len(ordered) - 1)))))
    return round(ordered[index], 2)


def recall_at_k(retrieved: list[str], expected: list[str], k: int) -> float:
    """expected 文档在 top-k 中的覆盖率；expected 为空时视为 1.0。"""
    if not expected:
        return 1.0
    top = set(retrieved[:k])
    return sum(1 for doc_id in expected if doc_id in top) / len(expected)


def mrr(retrieved: list[str], expected: list[str]) -> float:
    for rank, doc_id in enumerate(retrieved, start=1):
        if doc_id in expected:
            return 1.0 / rank
    return 0.0


def ndcg_at_k(retrieved: list[str], expected: list[str], k: int) -> float:
    if not expected:
        return 0.0
    dcg = sum(
        1.0 / math.log2(rank + 1)
        for rank, doc_id in enumerate(retrieved[:k], start=1)
        if doc_id in expected
    )
    idcg = sum(1.0 / math.log2(rank + 1) for rank in range(1, min(len(expected), k) + 1))
    return dcg / idcg if idcg else 0.0


def validate_cases(cases: list[dict], corpus_chunks: list[Chunk]) -> list[str]:
    """离线结构校验；返回人可读的错误列表，空列表表示通过。"""
    errors: list[str] = []
    doc_ids = {chunk.doc_id for chunk in corpus_chunks}
    active_doc_ids = {chunk.doc_id for chunk in corpus_chunks if chunk.status == "active"}
    seen: set[str] = set()
    coverage: dict[str, int] = defaultdict(int)

    for index, case in enumerate(cases):
        case_id = str(case.get("id") or f"#{index}")
        for field in ("id", "query", "type", "expected_doc_ids", "answerable"):
            if field not in case:
                errors.append(f"{case_id}: missing field {field}")
        if case_id in seen:
            errors.append(f"{case_id}: duplicate id")
        seen.add(case_id)

        case_type = case.get("type")
        if case_type not in _ALLOWED_TYPES:
            errors.append(f"{case_id}: unknown type {case_type!r}")
        expected = case.get("expected_doc_ids") or []
        if not isinstance(expected, list):
            errors.append(f"{case_id}: expected_doc_ids must be a list")
            expected = []
        for doc_id in expected:
            if doc_id not in doc_ids:
                errors.append(f"{case_id}: unknown doc {doc_id}")
            if doc_id in active_doc_ids:
                coverage[doc_id] += 1

        answerable = case.get("answerable")
        if case_type == "unanswerable":
            if answerable is not False or expected:
                errors.append(f"{case_id}: unanswerable cases need answerable=false and no expected docs")
        if answerable is True and not expected:
            errors.append(f"{case_id}: answerable case has no expected docs")
        if answerable is False and expected:
            errors.append(f"{case_id}: unanswerable case has expected docs")
        if case_type == "multihop" and len(expected) < 2:
            errors.append(f"{case_id}: multihop case needs 2+ expected docs")

        query = str(case.get("query") or "")
        if case_type == "version" and "returns-refund-v1" in expected:
            if not any(word in query for word in ("2025", "旧版", "以前", "历史", "去年")):
                errors.append(f"{case_id}: version case citing v1 must explicitly ask for history")

    for doc_id in sorted(active_doc_ids):
        if coverage[doc_id] < 2:
            errors.append(f"active doc {doc_id} covered {coverage[doc_id]} times (<2)")
    return errors


def _dedupe_doc_ids(candidates) -> list[str]:
    out: list[str] = []
    for candidate in candidates:
        doc_id = candidate.chunk.doc_id
        if doc_id not in out:
            out.append(doc_id)
    return out


def _aggregate_retrieval(rows: list[dict], cases: list[dict]) -> dict:
    answerable = [
        (row, case)
        for row, case in zip(rows, cases)
        if case.get("answerable") and case.get("expected_doc_ids")
    ]
    latencies = [row["latency_ms"] for row in rows]
    return {
        "case_count": len(rows),
        "answerable_case_count": len(answerable),
        "recall_at_1": _mean([recall_at_k(r["retrieved_doc_ids"], c["expected_doc_ids"], 1) for r, c in answerable]),
        "recall_at_3": _mean([recall_at_k(r["retrieved_doc_ids"], c["expected_doc_ids"], 3) for r, c in answerable]),
        "recall_at_5": _mean([recall_at_k(r["retrieved_doc_ids"], c["expected_doc_ids"], 5) for r, c in answerable]),
        "mrr": _mean([mrr(r["retrieved_doc_ids"], c["expected_doc_ids"]) for r, c in answerable]),
        "ndcg_at_5": _mean([ndcg_at_k(r["retrieved_doc_ids"], c["expected_doc_ids"], 5) for r, c in answerable]),
        "refusal_accuracy": _mean([
            1.0 if row["retrieval_answerable"] == bool(case.get("answerable")) else 0.0
            for row, case in zip(rows, cases)
        ]),
        "degraded_rate": _mean([1.0 if row["degraded"] else 0.0 for row in rows]),
        "p50_latency_ms": _percentile(latencies, 50),
        "p95_latency_ms": _percentile(latencies, 95),
    }


def _by_type(rows: list[dict], cases: list[dict]) -> dict:
    grouped_rows: dict[str, list[dict]] = defaultdict(list)
    grouped_cases: dict[str, list[dict]] = defaultdict(list)
    for row, case in zip(rows, cases):
        case_type = str(case.get("type") or "unknown")
        grouped_rows[case_type].append(row)
        grouped_cases[case_type].append(case)
    return {
        case_type: _aggregate_retrieval(grouped_rows[case_type], grouped_cases[case_type])
        for case_type in sorted(grouped_rows)
    }


async def run_retrieval_eval(
    index: RulebookIndex,
    cases: list[dict],
    configs: dict[str, RetrievalConfig] | None = None,
    *,
    embedder: EmbedQuery | None = None,
    reranker: Reranker | None = None,
    today: date | None = None,
) -> dict:
    configs = configs or BASELINES
    config_reports: dict[str, dict] = {}
    failures: list[dict] = []

    for name, config in configs.items():
        rows: list[dict] = []
        for case in cases:
            started = time.perf_counter()
            result = await retrieve(
                index,
                case["query"],
                config=config,
                embedder=embedder,
                reranker=reranker,
                today=today,
            )
            latency_ms = round((time.perf_counter() - started) * 1000, 2)
            retrieved = _dedupe_doc_ids(result.candidates)
            row = {
                "id": case.get("id"),
                "type": case.get("type"),
                "query": case["query"],
                "expected_doc_ids": case.get("expected_doc_ids") or [],
                "retrieved_doc_ids": retrieved,
                "retrieval_answerable": result.answerable,
                "refusal_reason": result.refusal_reason,
                "degraded": result.degraded,
                "latency_ms": latency_ms,
            }
            rows.append(row)
            expected = case.get("expected_doc_ids") or []
            if expected and recall_at_k(retrieved, expected, 5) < 1.0:
                failures.append({
                    "config": name,
                    "id": case.get("id"),
                    "type": case.get("type"),
                    "query": case["query"],
                    "expected_doc_ids": expected,
                    "retrieved_doc_ids": retrieved,
                    "kind": "retrieval_miss",
                })
            if result.answerable != bool(case.get("answerable")):
                failures.append({
                    "config": name,
                    "id": case.get("id"),
                    "type": case.get("type"),
                    "query": case["query"],
                    "expected_answerable": bool(case.get("answerable")),
                    "retrieval_answerable": result.answerable,
                    "refusal_reason": result.refusal_reason,
                    "kind": "refusal_mismatch",
                })

        config_reports[name] = {
            "metrics": _aggregate_retrieval(rows, cases),
            "by_type": _by_type(rows, cases),
            "cases": rows,
        }

    return {"case_count": len(cases), "configs": config_reports, "failures": failures}


def _full_metrics(rows: list[dict], cases: list[dict]) -> dict:
    answerable = [
        (row, case)
        for row, case in zip(rows, cases)
        if case.get("answerable") and case.get("expected_doc_ids")
    ]
    citation_precisions: list[float] = []
    keyword_recalls: list[float] = []
    for row, case in answerable:
        citations = row["citations"]
        expected = set(case.get("expected_doc_ids") or [])
        if citations:
            citation_precisions.append(
                sum(1 for doc_id in citations if doc_id in expected) / len(citations)
            )
        else:
            citation_precisions.append(0.0)
        keywords = case.get("answer_keywords") or []
        if keywords:
            keyword_recalls.append(
                sum(1 for keyword in keywords if keyword_present(row["answer"], keyword)) / len(keywords)
            )

    forbidden_cases = [
        (row, case) for row, case in zip(rows, cases) if case.get("forbidden_keywords")
    ]
    forbidden_hits = sum(
        1
        for row, case in forbidden_cases
        if any(forbidden_present(row["answer"], keyword) for keyword in case.get("forbidden_keywords") or [])
    )
    forbidden_violation_rate = (
        round(forbidden_hits / len(forbidden_cases), 4) if forbidden_cases else 0.0
    )

    latencies = [row["latency_ms"] for row in rows]
    return {
        "case_count": len(rows),
        "refusal_accuracy": _mean([
            1.0 if row["answerable"] == bool(case.get("answerable")) else 0.0
            for row, case in zip(rows, cases)
        ]),
        "citation_precision": _mean(citation_precisions),
        "citation_coverage": _mean([1.0 if row["citations"] else 0.0 for row, _ in answerable]),
        "keyword_recall": _mean(keyword_recalls),
        "forbidden_violation_rate": forbidden_violation_rate,
        "forbidden_case_count": len(forbidden_cases),
        "forbidden_hit_count": forbidden_hits,
        "p50_latency_ms": _percentile(latencies, 50),
        "p95_latency_ms": _percentile(latencies, 95),
    }


def _full_by_type(rows: list[dict], cases: list[dict]) -> dict:
    grouped_rows: dict[str, list[dict]] = defaultdict(list)
    grouped_cases: dict[str, list[dict]] = defaultdict(list)
    for row, case in zip(rows, cases):
        case_type = str(case.get("type") or "unknown")
        grouped_rows[case_type].append(row)
        grouped_cases[case_type].append(case)
    return {
        case_type: _full_metrics(grouped_rows[case_type], grouped_cases[case_type])
        for case_type in sorted(grouped_rows)
    }


async def run_full_eval(
    index: RulebookIndex,
    cases: list[dict],
    *,
    config: RetrievalConfig | None = None,
    embedder: EmbedQuery | None = None,
    reranker: Reranker | None = None,
    llm=None,
    generate: bool = True,
    today: date | None = None,
) -> dict:
    config = config or BASELINES["B3_hybrid_rerank"]
    rows: list[dict] = []
    for case in cases:
        started = time.perf_counter()
        result = await retrieve(
            index,
            case["query"],
            config=config,
            embedder=embedder,
            reranker=reranker,
            today=today,
        )
        answer = await generate_answer(case["query"], result, llm=llm) if generate else None
        latency_ms = round((time.perf_counter() - started) * 1000, 2)
        rows.append({
            "id": case.get("id"),
            "type": case.get("type"),
            "query": case["query"],
            "expected_doc_ids": case.get("expected_doc_ids") or [],
            "retrieved_doc_ids": _dedupe_doc_ids(result.candidates),
            "retrieval_answerable": result.answerable,
            "answerable": answer.answerable if answer is not None else result.answerable,
            "refusal_reason": answer.refusal_reason if answer is not None else result.refusal_reason,
            "answer": answer.answer if answer is not None else "",
            "citations": [citation.doc_id for citation in answer.citations] if answer else [],
            "degraded": {
                "retrieval": result.degraded,
                "generation": answer.degraded if answer else None,
            },
            "latency_ms": latency_ms,
        })

    metrics = _full_metrics(rows, cases)
    failures: list[dict] = []
    for row, case in zip(rows, cases):
        if row["answerable"] != bool(case.get("answerable")):
            failures.append({**{k: row[k] for k in ("id", "type", "query", "answerable")},
                             "kind": "refusal_mismatch",
                             "expected_answerable": bool(case.get("answerable"))})
            continue
        if case.get("answerable") and row["citations"]:
            expected = set(case.get("expected_doc_ids") or [])
            if any(doc_id not in expected for doc_id in row["citations"]):
                failures.append({**{k: row[k] for k in ("id", "type", "query", "citations")},
                                 "kind": "citation_mismatch",
                                 "expected_doc_ids": case.get("expected_doc_ids") or []})
        keywords = case.get("answer_keywords") or []
        if case.get("answerable") and keywords and not all(keyword_present(row["answer"], k) for k in keywords):
            failures.append({**{k: row[k] for k in ("id", "type", "query", "answer")},
                             "kind": "keyword_miss",
                             "answer_keywords": keywords})
    return {
        "metrics": metrics,
        "by_type": _full_by_type(rows, cases),
        "cases": rows,
        "failures": failures,
    }


def _format(value: Any) -> str:
    if value is None:
        return "—"
    if isinstance(value, float):
        return f"{value:.4f}".rstrip("0").rstrip(".")
    return str(value)


def render_markdown(report: dict) -> str:
    lines = [
        "# RAG 平台规则问答评测报告",
        "",
        f"- 生成时间：{report.get('generated_at', '—')}",
        f"- 用例数：{report.get('case_count', '—')}",
        f"- Embedding：{report.get('embedding_mode', '—')}",
        f"- Rerank：{report.get('rerank_mode', '—')}",
        "",
        "## 检索 baseline",
        "",
        "| 方案 | Recall@1 | Recall@3 | Recall@5 | MRR | nDCG@5 | 拒答准确率 | 降级率 | p50(ms) | p95(ms) |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for name, config in report.get("configs", {}).items():
        metrics = config.get("metrics", {})
        lines.append(
            "| {name} | {r1} | {r3} | {r5} | {mrr} | {ndcg} | {refusal} | {degraded} | {p50} | {p95} |".format(
                name=name,
                r1=_format(metrics.get("recall_at_1")),
                r3=_format(metrics.get("recall_at_3")),
                r5=_format(metrics.get("recall_at_5")),
                mrr=_format(metrics.get("mrr")),
                ndcg=_format(metrics.get("ndcg_at_5")),
                refusal=_format(metrics.get("refusal_accuracy")),
                degraded=_format(metrics.get("degraded_rate")),
                p50=_format(metrics.get("p50_latency_ms")),
                p95=_format(metrics.get("p95_latency_ms")),
            )
        )

    lines += ["", "## 分类型（按方案）", "", "| 方案 | 类型 | 用例 | Recall@5 | MRR | nDCG@5 | 拒答准确率 |", "|---|---|---:|---:|---:|---:|---:|"]
    for name, config in report.get("configs", {}).items():
        for case_type, metrics in config.get("by_type", {}).items():
            lines.append(
                "| {name} | {type} | {count} | {r5} | {mrr} | {ndcg} | {refusal} |".format(
                    name=name,
                    type=case_type,
                    count=metrics.get("case_count"),
                    r5=_format(metrics.get("recall_at_5")),
                    mrr=_format(metrics.get("mrr")),
                    ndcg=_format(metrics.get("ndcg_at_5")),
                    refusal=_format(metrics.get("refusal_accuracy")),
                )
            )

    full = report.get("full") or {}
    metrics = full.get("metrics") or {}
    if metrics:
        lines += [
            "",
            "## 全链路（B3 + 生成）",
            "",
            "| 指标 | 值 |",
            "|---|---:|",
            f"| 拒答准确率 | {_format(metrics.get('refusal_accuracy'))} |",
            f"| 引用精确率 | {_format(metrics.get('citation_precision'))} |",
            f"| 引用覆盖率 | {_format(metrics.get('citation_coverage'))} |",
            f"| 答案关键词召回 | {_format(metrics.get('keyword_recall'))} |",
            f"| 注入越权率（越低越好） | {_format(metrics.get('forbidden_violation_rate'))} |",
            f"| p50 延迟(ms) | {_format(metrics.get('p50_latency_ms'))} |",
            f"| p95 延迟(ms) | {_format(metrics.get('p95_latency_ms'))} |",
        ]

    failures = report.get("failures") or []
    if failures:
        lines += ["", "## 失败样例", "", "| 方案 | 用例 | 类型 | 查询 | 期望 | 实际 |", "|---|---|---|---|---|---|"]
        for failure in failures[:15]:
            expected = failure.get("expected_doc_ids") or failure.get("expected_answerable")
            actual = failure.get("retrieved_doc_ids") or failure.get("answerable")
            query = str(failure.get("query", "")).replace("|", "\\|")
            lines.append(
                f"| {failure.get('config', 'full')} | {failure.get('id')} | {failure.get('type')} | "
                f"{query} | {expected} | {actual} |"
            )

    lines += [
        "",
        "## 说明",
        "",
        "- 检索指标只统计有预期文档的用例；拒答准确率统计全部用例。",
        "- 引用精确率 = 引用文档命中预期文档的比例；无引用记 0。",
        "- 若 `embedding_mode` 为 `local-lsa-zh-v1`，表示离线 TF-IDF+SVD 替代，不应当与在线 bge-m3 数字混用。",
        "- 若 `rerank_mode` 显示不可用，B3 会降级为 RRF 顺序，结果应理解为「rerank 缺失」而非「rerank 无效」。",
    ]
    return "\n".join(lines) + "\n"
