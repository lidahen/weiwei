import asyncio
from datetime import date
from pathlib import Path
from types import SimpleNamespace

import numpy as np

from app.services.rag_showcase.corpus import load_corpus
from app.services.rag_showcase.eval import (
    PacedRetryLLM,
    forbidden_present,
    keyword_present,
    mrr,
    ndcg_at_k,
    recall_at_k,
    render_markdown,
    run_full_eval,
    run_retrieval_eval,
    validate_cases,
)
from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.retrieval import RetrievalConfig


def _doc(doc_id: str, content: str) -> str:
    return f"""---
doc_id: {doc_id}
title: {doc_id}
category: test
version: v1
effective_date: 2026-01-01
status: active
---

## 一

{content}
"""


async def _fake_embed(texts: list[str]) -> list[list[float]]:
    out = []
    for text in texts:
        vector = np.zeros(16, dtype="float32")
        for i, ch in enumerate(text):
            vector[ord(ch) % 16] += 1
        out.append((vector / (np.linalg.norm(vector) or 1)).tolist())
    return out


async def _fake_query_embed(text: str) -> list[float]:
    return (await _fake_embed([text]))[0]


async def _selective_rerank(query: str, docs: list[str]) -> list[float]:
    return [1.0 if "退款" in query else 0.0 for _ in docs]


def _build_index(tmp_path: Path) -> RulebookIndex:
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "returns.md").write_text(_doc("returns-v2", "退款审核 5 个工作日"), encoding="utf-8")
    (corpus / "other.md").write_text(_doc("other", "禁售电子烟"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    asyncio.run(index.build(force=True))
    asyncio.run(index.load())
    return index


def test_recall_at_k_doc_level():
    assert recall_at_k(["a", "b", "c"], ["a"], 1) == 1.0
    assert recall_at_k(["b", "a"], ["a"], 1) == 0.0
    assert recall_at_k(["b", "a"], ["a"], 2) == 1.0


def test_recall_multihop_coverage():
    assert recall_at_k(["a", "x", "b"], ["a", "b"], 3) == 1.0
    assert recall_at_k(["a", "x", "y"], ["a", "b"], 3) == 0.5


def test_mrr_first_relevant_rank():
    assert mrr(["x", "a", "b"], ["a", "b"]) == 0.5
    assert mrr(["a"], ["a"]) == 1.0
    assert mrr(["x"], ["a"]) == 0.0


def test_ndcg_at_k_binary_relevance():
    # 标准 nDCG：两个相关文档分别在第 1、3 位，理想排序为第 1、2 位
    assert round(ndcg_at_k(["a", "x", "b"], ["a", "b"], 3), 4) == 0.9197
    assert ndcg_at_k(["a", "b", "x"], ["a", "b"], 3) == 1.0
    assert round(ndcg_at_k(["x", "a"], ["a"], 2), 4) > 0
    assert ndcg_at_k(["x", "y"], ["a"], 2) == 0.0


def test_keyword_present_normalizes_spaces():
    assert keyword_present("退款审核需要 7天。", "7 天") is True
    assert keyword_present("赔付上限 12元", "12 元") is True
    assert keyword_present("审核需要 3 个工作日", "5 个工作日") is False


def test_forbidden_present_ignores_negated_claims():
    assert forbidden_present("退款审核不是无限期的。", "无限期") is False
    assert forbidden_present("退款审核并非没有期限。", "没有期限") is False
    assert forbidden_present("退款审核是无限期的。", "无限期") is True
    assert forbidden_present("质量问题退货运费非由买家承担。", "由买家承担") is False
    assert forbidden_present("质量问题退货运费由买家承担。", "由买家承担") is True
    # q064：金标禁止的是「质量问题由买家承担」这个注入结论；
    # 正确答案里的「非质量问题…由买家承担」是政策规定的例外，不能误判为越权。
    assert (
        forbidden_present(
            "质量问题退货运费由卖家承担；非质量问题退货运费由买家承担。",
            "质量问题退货运费由买家承担",
        )
        is False
    )
    assert (
        forbidden_present(
            "质量问题退货运费由买家承担。",
            "质量问题退货运费由买家承担",
        )
        is True
    )


def test_paced_retry_llm_retries_429_without_real_sleep():
    class _Flaky:
        def __init__(self):
            self.calls = 0

        async def ainvoke(self, messages):
            self.calls += 1
            if self.calls == 1:
                exc = RuntimeError("429 rate limit")
                exc.status_code = 429
                raise exc
            return SimpleNamespace(content="ok")

    async def run():
        inner = _Flaky()
        llm = PacedRetryLLM(inner, min_interval=0, max_retries=1, backoff_base=0)
        response = await llm.ainvoke([])
        return response, inner.calls, llm.retries

    response, calls, retries = asyncio.run(run())
    assert response.content == "ok"
    assert calls == 2
    assert retries == 1


def test_validate_cases_reports_unknown_doc_and_bad_multihop(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核"), encoding="utf-8")
    chunks = load_corpus(corpus).chunks
    cases = [
        {
            "id": "q1",
            "query": "退款",
            "type": "fact",
            "expected_doc_ids": ["nope"],
            "answerable": True,
        },
        {
            "id": "q2",
            "query": "退款和禁售",
            "type": "multihop",
            "expected_doc_ids": ["a"],
            "answerable": True,
        },
    ]
    errors = validate_cases(cases, chunks)
    assert any("unknown doc" in e for e in errors)
    assert any("multihop" in e for e in errors)


def test_run_retrieval_eval_reports_baselines(tmp_path: Path):
    index = _build_index(tmp_path)
    cases = [
        {
            "id": "q1",
            "query": "退款审核 5 个工作日",
            "type": "fact",
            "expected_doc_ids": ["returns-v2"],
            "answer_keywords": ["5 个工作日"],
            "answerable": True,
        },
        {
            "id": "q2",
            "query": "海外购关税怎么算",
            "type": "unanswerable",
            "expected_doc_ids": [],
            "answer_keywords": [],
            "answerable": False,
        },
    ]
    configs = {
        "B1_vector": RetrievalConfig(use_bm25=False, use_rerank=False),
        "B2_hybrid": RetrievalConfig(use_bm25=True, use_rerank=False),
    }
    report = asyncio.run(
        run_retrieval_eval(
            index,
            cases,
            configs,
            embedder=_fake_query_embed,
            today=date(2026, 10, 26),
        )
    )
    assert report["case_count"] == 2
    assert set(report["configs"]) == {"B1_vector", "B2_hybrid"}
    assert report["configs"]["B2_hybrid"]["metrics"]["recall_at_1"] == 1.0
    assert report["configs"]["B2_hybrid"]["cases"][0]["retrieved_doc_ids"][0] == "returns-v2"


def test_render_markdown_contains_config_table():
    report = {
        "generated_at": "2026-10-26T00:00:00+00:00",
        "case_count": 1,
        "embedding_mode": "local-lsa-zh-v1",
        "configs": {
            "B2_hybrid": {
                "metrics": {
                    "recall_at_1": 1.0,
                    "recall_at_3": 1.0,
                    "recall_at_5": 1.0,
                    "mrr": 1.0,
                    "ndcg_at_5": 1.0,
                    "refusal_accuracy": 1.0,
                    "p50_latency_ms": 3.0,
                    "p95_latency_ms": 5.0,
                },
                "by_type": {},
            }
        },
        "full": {"metrics": {"refusal_accuracy": 1.0, "citation_precision": 1.0}},
        "failures": [],
    }
    markdown = render_markdown(report)
    assert "B2_hybrid" in markdown
    assert "Recall@1" in markdown
    assert "local-lsa-zh-v1" in markdown


def test_run_full_eval_scores_refusal_citation_and_keywords(tmp_path: Path):
    index = _build_index(tmp_path)
    cases = [
        {
            "id": "q1",
            "query": "退款审核几天",
            "type": "fact",
            "expected_doc_ids": ["returns-v2"],
            "answer_keywords": ["5 个工作日"],
            "answerable": True,
            "forbidden_keywords": [],
        },
        {
            "id": "q2",
            "query": "海外购关税怎么算",
            "type": "unanswerable",
            "expected_doc_ids": [],
            "answer_keywords": [],
            "answerable": False,
            "forbidden_keywords": [],
        },
    ]

    class _FakeLLM:
        async def ainvoke(self, messages):
            return SimpleNamespace(content="退款审核需要 5 个工作日 [1]。")

    report = asyncio.run(
        run_full_eval(
            index,
            cases,
            embedder=_fake_query_embed,
            reranker=_selective_rerank,
            llm=_FakeLLM(),
            today=date(2026, 10, 26),
        )
    )
    metrics = report["metrics"]
    assert metrics["refusal_accuracy"] == 1.0
    assert metrics["citation_precision"] == 1.0
    assert metrics["keyword_recall"] == 1.0
    assert metrics["forbidden_violation_rate"] == 0.0
