import asyncio
from pathlib import Path

import numpy as np

from app.services.rag_showcase.index import RulebookIndex


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
            v[i % 8] += ord(ch) % 17
        norm = np.linalg.norm(v) or 1.0
        out.append((v / norm).tolist())
    return out


def test_build_load_and_search(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核 5 个工作日"), encoding="utf-8")
    (corpus / "b.md").write_text(_doc("b", "禁售电子烟"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)

    manifest = asyncio.run(index.build(force=True))
    assert manifest["chunk_count"] == 2
    assert (tmp_path / "index" / "rulebook.faiss").exists()

    loaded = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    assert asyncio.run(loaded.load()) is True
    assert [c.doc_id for c in loaded.chunks] == ["a", "b"]
    assert loaded.is_stale() is False

    q = np.zeros(8, dtype="float32")
    q[0] = 1.0
    hits = loaded.search_vector(q, 2)
    assert len(hits) == 2
    assert all(isinstance(i, int) for i, _ in hits)


def test_build_reuses_vectors_for_unchanged_chunks(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "第一版内容"), encoding="utf-8")
    calls = []

    async def counting_embed(texts):
        calls.append(list(texts))
        return await _fake_embed(texts)

    index = RulebookIndex(corpus, tmp_path / "index", embedder=counting_embed)
    asyncio.run(index.build(force=True))
    assert len(calls) == 1

    (corpus / "b.md").write_text(_doc("b", "新增文档"), encoding="utf-8")
    asyncio.run(index.build(force=False))
    assert len(calls) == 2
    assert calls[1] == ["## 一\n\n新增文档"]

    asyncio.run(index.build(force=False))
    assert len(calls) == 2


def test_build_rejects_empty_corpus(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    try:
        asyncio.run(index.build(force=True))
    except ValueError as e:
        assert "empty" in str(e).lower()
    else:
        raise AssertionError("expected ValueError")


def test_missing_index_load_returns_false(tmp_path: Path):
    index = RulebookIndex(tmp_path / "corpus", tmp_path / "index", embedder=_fake_embed)
    assert asyncio.run(index.load()) is False


def test_build_records_explicit_embedding_model(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核 5 个工作日"), encoding="utf-8")
    index = RulebookIndex(
        corpus,
        tmp_path / "index",
        embedder=_fake_embed,
        embedding_model="local-lsa-zh-v1",
    )
    manifest = asyncio.run(index.build(force=True))
    assert manifest["embedding_model"] == "local-lsa-zh-v1"


def test_build_reembeds_when_embedding_model_changes(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核 5 个工作日"), encoding="utf-8")
    calls: list[list[str]] = []

    async def counting_embed(texts):
        calls.append(list(texts))
        return await _fake_embed(texts)

    first = RulebookIndex(
        corpus, tmp_path / "index", embedder=counting_embed, embedding_model="model-a"
    )
    first_manifest = asyncio.run(first.build(force=True))
    assert first_manifest["embedding_model"] == "model-a"
    assert len(calls) == 1

    second = RulebookIndex(
        corpus, tmp_path / "index", embedder=counting_embed, embedding_model="model-b"
    )
    second_manifest = asyncio.run(second.build(force=False))
    assert second_manifest["embedding_model"] == "model-b"
    assert len(calls) == 2
    assert second.is_stale() is False


def test_index_is_stale_when_embedding_model_changes(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核 5 个工作日"), encoding="utf-8")
    first = RulebookIndex(
        corpus, tmp_path / "index", embedder=_fake_embed, embedding_model="model-a"
    )
    asyncio.run(first.build(force=True))

    second = RulebookIndex(
        corpus, tmp_path / "index", embedder=_fake_embed, embedding_model="model-b"
    )
    assert second.is_stale() is True


def test_build_reembeds_when_index_version_changes(tmp_path: Path, monkeypatch):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核 5 个工作日"), encoding="utf-8")
    calls: list[list[str]] = []

    async def counting_embed(texts):
        calls.append(list(texts))
        return await _fake_embed(texts)

    first = RulebookIndex(corpus, tmp_path / "index", embedder=counting_embed)
    asyncio.run(first.build(force=True))
    assert len(calls) == 1

    monkeypatch.setattr("app.services.rag_showcase.index.INDEX_VERSION", "next")
    second = RulebookIndex(corpus, tmp_path / "index", embedder=counting_embed)
    manifest = asyncio.run(second.build(force=False))
    assert manifest["index_version"] == "next"
    assert len(calls) == 2
