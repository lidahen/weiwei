"""平台规则 RAG 的服务层：索引生命周期、查询编排、trace。"""
from __future__ import annotations

import logging
from dataclasses import asdict, replace
from pathlib import Path
from typing import Any, Callable

from app.config import settings
from app.services.embedding_service import embed_query
from app.services.rag_showcase.generate import generate_answer
from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.retrieval import (
    EmbedQuery,
    RetrievalConfig,
    Reranker,
    _rerank_documents,
    retrieve,
)
from app.services.rag_showcase.trace import TraceStore

logger = logging.getLogger(__name__)


class RagShowcaseService:
    def __init__(
        self,
        index: RulebookIndex,
        trace_store: TraceStore,
        config: RetrievalConfig | None = None,
        embedder: EmbedQuery | None = None,
        reranker: Reranker | None = None,
        llm_factory: Callable[[], Any] | None = None,
    ) -> None:
        self.index = index
        self.trace_store = trace_store
        self._embedder = embedder
        self._reranker = reranker
        self._llm_factory = llm_factory
        self.config = config or RetrievalConfig(
            top_k=settings.rag_showcase_top_k,
            recall_k=settings.rag_showcase_recall_k,
            min_rerank_score=settings.rag_showcase_min_rerank_score,
            min_vector_score=settings.rag_showcase_min_vector_score,
        )

    async def ensure_ready(self) -> None:
        """懒加载索引；缺失或陈旧且允许自动重建时构建。"""
        if self.index.chunks:
            return
        loaded = await self.index.load()
        if not loaded or (settings.rag_showcase_auto_rebuild and self.index.is_stale()):
            await self.index.build()
            await self.index.load()

    async def query(
        self,
        query: str,
        *,
        include_superseded: bool | None = None,
        debug: bool = False,
    ) -> dict:
        await self.ensure_ready()
        config = self.config
        if include_superseded is not None:
            config = replace(config, include_superseded=include_superseded)

        result = await retrieve(
            self.index,
            query,
            config=config,
            embedder=self._embedder or embed_query,
            reranker=self._reranker or _rerank_documents,
        )
        # 拒答不需要 LLM；只有可能生成时才构造，避免 LLM 配置故障拖垮拒答路径。
        llm = (
            self._llm_factory()
            if result.answerable and self._llm_factory is not None
            else None
        )
        answer = await generate_answer(query, result, llm=llm)
        payload = {
            "query": query,
            "answer": answer.answer,
            "answerable": answer.answerable,
            "citations": [asdict(c) for c in answer.citations],
            "refusal_reason": answer.refusal_reason,
            "degraded": answer.degraded,
            "trace": result.trace if debug else {
                "degraded": result.degraded,
                "latency_ms": result.trace.get("latency_ms", {}),
            },
        }
        self.trace_store.add({
            "query": query,
            "answerable": answer.answerable,
            "citations": [c.doc_id for c in answer.citations],
            "degraded": answer.degraded,
            "retrieval": result.trace,
        })
        return payload

    async def rebuild(self, force: bool = False) -> dict:
        manifest = await self.index.build(force=force)
        await self.index.load()
        return manifest

    def corpus(self) -> list[dict]:
        docs: dict[str, dict] = {}
        for chunk in self.index.chunks:
            item = docs.setdefault(chunk.doc_id, {
                "doc_id": chunk.doc_id,
                "title": chunk.title,
                "category": chunk.category,
                "version": chunk.version,
                "effective_date": chunk.effective_date.isoformat(),
                "status": chunk.status,
                "chunk_count": 0,
                "sections": [],
            })
            item["chunk_count"] += 1
            section = " / ".join(chunk.section_path)
            if section and section not in item["sections"]:
                item["sections"].append(section)
        return list(docs.values())


_service: RagShowcaseService | None = None


def get_service() -> RagShowcaseService:
    global _service
    if _service is None:
        _service = RagShowcaseService(
            index=RulebookIndex(
                Path(settings.rag_showcase_corpus_path),
                Path(settings.rag_showcase_index_dir),
            ),
            trace_store=TraceStore(
                Path(settings.rag_showcase_trace_path),
                settings.rag_showcase_trace_limit,
            ),
        )
    return _service


def reset_service_for_tests() -> None:
    global _service
    _service = None
