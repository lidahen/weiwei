import asyncio
import json
from pathlib import Path

import numpy as np

from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.retrieval import RetrievalResult
from app.services.rag_showcase.service import RagShowcaseService
from app.services.rag_showcase.trace import TraceStore


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
    for t in texts:
        v = np.zeros(8, dtype="float32")
        for i, ch in enumerate(t):
            v[ord(ch) % 8] += 1
        out.append((v / (np.linalg.norm(v) or 1)).tolist())
    return out


async def _fake_query_embed(text: str) -> list[float]:
    return (await _fake_embed([text]))[0]


async def _fake_rerank(query: str, docs: list[str]) -> list[float]:
    return [0.9 if "退款" in d else 0.0 for d in docs]


class _FakeLLM:
    async def ainvoke(self, messages):
        return type("R", (), {"content": "审核需要 5 个工作日 [1]。"})()


def _service(tmp_path: Path, **kwargs) -> RagShowcaseService:
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("returns", "退款审核 5 个工作日"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    asyncio.run(index.build(force=True))
    asyncio.run(index.load())
    return RagShowcaseService(
        index=index,
        trace_store=TraceStore(tmp_path / "traces.jsonl", limit=10),
        config=None,
        **kwargs,
    )


def test_query_returns_answer_citations_and_trace(tmp_path: Path, monkeypatch):
    service = _service(tmp_path)
    monkeypatch.setattr("app.services.rag_showcase.service.embed_query", _fake_query_embed)
    monkeypatch.setattr("app.services.rag_showcase.service._rerank_documents", _fake_rerank)
    monkeypatch.setattr("app.services.agent.llm._get_llm", lambda: _FakeLLM())
    result = asyncio.run(service.query("退款审核几天", debug=True))
    assert result["answerable"] is True
    assert result["citations"][0]["doc_id"] == "returns"
    assert "trace" in result
    assert service.trace_store.recent(1)


def test_corpus_lists_documents(tmp_path: Path):
    service = _service(tmp_path)
    docs = service.corpus()
    assert docs[0]["doc_id"] == "returns"
    assert docs[0]["chunk_count"] == 1


def test_query_uses_injected_embedder_and_reranker(tmp_path: Path, monkeypatch):
    service = _service(tmp_path, embedder=_fake_query_embed, reranker=_fake_rerank)
    monkeypatch.setattr("app.services.agent.llm._get_llm", lambda: _FakeLLM())
    result = asyncio.run(service.query("退款审核几天", debug=True))
    assert result["answerable"] is True
    assert result["citations"][0]["doc_id"] == "returns"


def test_query_uses_injected_llm_factory(tmp_path: Path):
    service = _service(
        tmp_path,
        embedder=_fake_query_embed,
        reranker=_fake_rerank,
        llm_factory=lambda: _FakeLLM(),
    )
    result = asyncio.run(service.query("退款审核几天", debug=True))
    assert result["answer"] == "审核需要 5 个工作日 [1]。"


def test_refusal_does_not_construct_llm(tmp_path: Path, monkeypatch):
    calls: list[int] = []

    def failing_factory():
        calls.append(1)
        raise AssertionError("refusal path must not construct an LLM")

    service = _service(
        tmp_path,
        embedder=_fake_query_embed,
        reranker=_fake_rerank,
        llm_factory=failing_factory,
    )

    async def fake_retrieve(*args, **kwargs):
        return RetrievalResult(
            candidates=[], answerable=False, refusal_reason="no_candidates"
        )

    monkeypatch.setattr("app.services.rag_showcase.service.retrieve", fake_retrieve)
    result = asyncio.run(service.query("完全无关的问题"))
    assert result["answerable"] is False
    assert result["refusal_reason"] == "no_candidates"
    assert calls == []


def test_trace_store_persists_jsonl(tmp_path: Path):
    store = TraceStore(tmp_path / "traces.jsonl", limit=2)
    store.add({"query": "a"})
    store.add({"query": "b"})
    store.add({"query": "c"})
    assert [t["query"] for t in store.recent(10)] == ["b", "c"]
    lines = (tmp_path / "traces.jsonl").read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 3
    assert json.loads(lines[-1])["query"] == "c"
