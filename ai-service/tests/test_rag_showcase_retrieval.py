import asyncio
from datetime import date
from pathlib import Path

import numpy as np

from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.retrieval import RetrievalConfig, retrieve


def _doc(doc_id: str, status: str, content: str, effective: str = "2026-01-01") -> str:
    return f"""---
doc_id: {doc_id}
title: {doc_id}
category: test
version: v1
effective_date: {effective}
status: {status}
---

## 一

{content}
"""


async def _fake_embed(texts: list[str]) -> list[list[float]]:
    out = []
    for t in texts:
        v = np.zeros(16, dtype="float32")
        for i, ch in enumerate(t):
            v[ord(ch) % 16] += 1
        out.append((v / (np.linalg.norm(v) or 1)).tolist())
    return out


async def _fake_query_embed(text: str) -> list[float]:
    return (await _fake_embed([text]))[0]


async def _fake_rerank(query: str, docs: list[str]) -> list[float]:
    return [1.0 if "退款" in d else 0.0 for d in docs]


def _build(tmp_path: Path) -> RulebookIndex:
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "v1.md").write_text(_doc("returns-v1", "superseded", "退款审核 3 个工作日"), encoding="utf-8")
    (corpus / "v2.md").write_text(_doc("returns-v2", "active", "退款审核 5 个工作日"), encoding="utf-8")
    (corpus / "other.md").write_text(_doc("other", "active", "禁售电子烟"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    asyncio.run(index.build(force=True))
    asyncio.run(index.load())
    return index


def test_version_filter_excludes_superseded(tmp_path: Path):
    index = _build(tmp_path)
    result = asyncio.run(retrieve(index, "退款审核几天", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=_fake_rerank, today=date(2026, 10, 26)))
    assert result.answerable is True
    assert all(c.chunk.status == "active" for c in result.candidates)


def test_historical_query_includes_superseded(tmp_path: Path):
    index = _build(tmp_path)
    result = asyncio.run(retrieve(index, "2025 年退款审核几天", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=_fake_rerank, today=date(2026, 10, 26)))
    docs = {c.chunk.doc_id for c in result.candidates}
    assert "returns-v1" in docs


def test_unanswerable_is_refused(tmp_path: Path):
    index = _build(tmp_path)

    async def low_rerank(query, docs):
        return [0.0 for _ in docs]

    result = asyncio.run(retrieve(index, "海外购关税怎么算", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=low_rerank, today=date(2026, 10, 26)))
    assert result.answerable is False
    assert result.refusal_reason


def test_embedding_failure_degrades_to_bm25(tmp_path: Path):
    index = _build(tmp_path)

    async def broken_embed(text):
        raise RuntimeError("embedding down")

    result = asyncio.run(retrieve(index, "退款审核 5 个工作日", config=RetrievalConfig(), embedder=broken_embed, reranker=_fake_rerank, today=date(2026, 10, 26)))
    assert "embedding_unavailable" in result.degraded
    assert result.candidates


def test_rerank_failure_keeps_rrf_order(tmp_path: Path):
    index = _build(tmp_path)

    async def broken_rerank(query, docs):
        return None

    result = asyncio.run(retrieve(index, "退款审核", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=broken_rerank, today=date(2026, 10, 26)))
    assert "rerank_unavailable" in result.degraded
    assert result.candidates
    assert all(c.rerank_score is None for c in result.candidates)
