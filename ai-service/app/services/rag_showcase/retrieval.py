"""检索管线：版本过滤 → 向量 + BM25 召回 → RRF → rerank → 拒答。"""
from __future__ import annotations

import logging
import time
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date
from typing import Awaitable, Callable

import numpy as np

from app.services.rag_showcase.bm25 import BM25
from app.services.rag_showcase.corpus import Chunk
from app.services.rag_showcase.index import RulebookIndex

logger = logging.getLogger(__name__)

EmbedQuery = Callable[[str], Awaitable[list[float]]]
Reranker = Callable[[str, list[str]], Awaitable[list[float] | None]]

# 明确的「问旧版」信号；命中才放开 superseded 文档
_HISTORICAL_KEYWORDS = ("以前", "旧版", "历史", "去年", "2025", "2024")


@dataclass
class RetrievalConfig:
    top_k: int = 5
    recall_k: int = 20
    rrf_k: int = 60
    use_bm25: bool = True
    use_rerank: bool = True
    include_superseded: bool = False
    min_rerank_score: float = 0.05
    min_vector_score: float = 0.30


@dataclass
class Candidate:
    chunk: Chunk
    vector_score: float | None = None
    bm25_score: float | None = None
    rrf_score: float | None = None
    rerank_score: float | None = None


@dataclass
class RetrievalResult:
    candidates: list[Candidate]
    answerable: bool
    refusal_reason: str | None
    degraded: list[str] = field(default_factory=list)
    trace: dict = field(default_factory=dict)


def _is_historical(query: str) -> bool:
    q = (query or "").lower()
    return any(word.lower() in q for word in _HISTORICAL_KEYWORDS)


async def _rerank_documents(query: str, documents: list[str]) -> list[float] | None:
    """SiliconFlow /rerank；任何失败返回 None，由调用方回退 RRF 顺序。"""
    try:
        from app.config import settings

        if not settings.rerank_enabled or not settings.embedding_api_key:
            return None
        import httpx

        base = settings.embedding_base_url.rstrip("/")
        async with httpx.AsyncClient(timeout=settings.rerank_timeout) as client:
            resp = await client.post(
                f"{base}/rerank",
                headers={"Authorization": f"Bearer {settings.embedding_api_key}"},
                json={
                    "model": settings.rerank_model,
                    "query": query,
                    "documents": documents,
                    "top_n": len(documents),
                },
            )
            resp.raise_for_status()
            data = resp.json()
        scores = [0.0] * len(documents)
        for item in data.get("results") or []:
            idx = int(item.get("index", -1))
            if 0 <= idx < len(scores):
                scores[idx] = float(item.get("relevance_score") or 0.0)
        return scores
    except Exception as e:  # noqa: BLE001 —— 重排是增强项，失败必须可降级
        logger.warning("rag rerank failed, fallback to rrf: %s", e)
        return None


def _rrf_rank(scores: dict[int, float], rrf_k: int, rrf: dict[int, float], recall_k: int) -> None:
    ranked = sorted(scores.items(), key=lambda item: (-item[1], item[0]))[:recall_k]
    for rank, (pos, _) in enumerate(ranked):
        rrf[pos] += 1.0 / (rrf_k + rank + 1)


async def retrieve(
    index: RulebookIndex,
    query: str,
    *,
    config: RetrievalConfig | None = None,
    embedder: EmbedQuery | None = None,
    reranker: Reranker | None = None,
    today: date | None = None,
) -> RetrievalResult:
    config = config or RetrievalConfig()
    today = today or date.today()
    started = time.perf_counter()
    latency: dict[str, float] = {}
    degraded: list[str] = []

    chunks = index.chunks
    include_superseded = config.include_superseded or _is_historical(query)
    allowed = [
        i for i, chunk in enumerate(chunks)
        if chunk.effective_date <= today
        and (include_superseded or chunk.status == "active")
    ]
    allowed_set = set(allowed)
    trace: dict = {
        "query": query,
        "filters": {
            "include_superseded": include_superseded,
            "status": "all" if include_superseded else "active",
            "today": today.isoformat(),
        },
        "candidate_count": len(allowed),
        "degraded": degraded,
    }

    # 1) 向量召回
    vector_scores: dict[int, float] = {}
    t0 = time.perf_counter()
    if allowed:
        try:
            if embedder is None:
                from app.services.embedding_service import embed_query as default_embed_query

                embedder = default_embed_query
            query_vector = np.asarray(await embedder(query), dtype="float32")
            raw_hits = index.search_vector(query_vector, min(len(chunks), max(config.recall_k * 4, 20)))
            for pos, score in raw_hits:
                if pos in allowed_set:
                    vector_scores[pos] = float(score)
        except Exception as e:  # noqa: BLE001 —— embedding 失败退化为 BM25
            logger.warning("rag embedding unavailable, fallback to bm25: %s", e)
            degraded.append("embedding_unavailable")
    latency["vector_ms"] = round((time.perf_counter() - t0) * 1000, 2)

    # 2) BM25 召回
    bm25_scores: dict[int, float] = {}
    t0 = time.perf_counter()
    if config.use_bm25 and allowed:
        bm25 = BM25([chunks[i].content for i in allowed])
        for local_pos, score in bm25.top_n(query, config.recall_k):
            bm25_scores[allowed[local_pos]] = float(score)
    latency["bm25_ms"] = round((time.perf_counter() - t0) * 1000, 2)

    # 3) RRF 融合
    t0 = time.perf_counter()
    rrf: dict[int, float] = defaultdict(float)
    _rrf_rank(vector_scores, config.rrf_k, rrf, config.recall_k)
    if config.use_bm25:
        _rrf_rank(bm25_scores, config.rrf_k, rrf, config.recall_k)
    fused = sorted(rrf.items(), key=lambda item: (-item[1], item[0]))
    latency["rrf_ms"] = round((time.perf_counter() - t0) * 1000, 2)

    # 4) rerank
    t0 = time.perf_counter()
    rerank_scores: dict[int, float] = {}
    rerank_applied = False
    try:
        from app.config import settings

        rerank_limit = settings.rerank_top_n
    except Exception:
        rerank_limit = 5
    if config.use_rerank and fused:
        head = fused[:max(config.top_k, rerank_limit)]
        docs = [chunks[pos].content for pos, _ in head]
        try:
            scores = await (reranker or _rerank_documents)(query, docs)
        except Exception as e:  # noqa: BLE001 —— 重排异常同样降级
            logger.warning("rag reranker raised, fallback to rrf: %s", e)
            scores = None
        if scores is None or len(scores) != len(head):
            degraded.append("rerank_unavailable")
        else:
            rerank_applied = True
            for (pos, _), score in zip(head, scores):
                rerank_scores[pos] = float(score)
            fused = sorted(
                head,
                key=lambda item: (-rerank_scores.get(item[0], 0.0), -item[1], item[0]),
            )
    latency["rerank_ms"] = round((time.perf_counter() - t0) * 1000, 2)

    # 5) 组装候选
    final_positions = [pos for pos, _ in fused[:config.top_k]]
    candidates = [
        Candidate(
            chunk=chunks[pos],
            vector_score=vector_scores.get(pos),
            bm25_score=bm25_scores.get(pos),
            rrf_score=rrf.get(pos),
            rerank_score=rerank_scores.get(pos),
        )
        for pos in final_positions
    ]

    answerable = bool(candidates)
    refusal_reason: str | None = None if candidates else "no_candidates"
    if candidates:
        top = candidates[0]
        if rerank_applied and top.rerank_score is not None:
            if top.rerank_score < config.min_rerank_score:
                answerable = False
                refusal_reason = "low_rerank_score"
        else:
            low_vector = top.vector_score is None or top.vector_score < config.min_vector_score
            low_bm25 = top.bm25_score is None or top.bm25_score <= 0
            if low_vector and low_bm25:
                answerable = False
                refusal_reason = "low_vector_score"

    trace.update({
        "vector_hits": [
            {"chunk_id": chunks[i].chunk_id, "doc_id": chunks[i].doc_id,
             "score": round(vector_scores[i], 6)}
            for i in sorted(vector_scores, key=lambda x: -vector_scores[x])[:10]
        ],
        "bm25_hits": [
            {"chunk_id": chunks[i].chunk_id, "doc_id": chunks[i].doc_id,
             "score": round(bm25_scores[i], 6)}
            for i in sorted(bm25_scores, key=lambda x: -bm25_scores[x])[:10]
        ],
        "fused": [
            {"chunk_id": chunks[pos].chunk_id, "doc_id": chunks[pos].doc_id,
             "rrf_score": round(rrf[pos], 6)}
            for pos, _ in fused[:10]
        ],
        "reranked": [
            {"chunk_id": chunks[pos].chunk_id, "doc_id": chunks[pos].doc_id,
             "rerank_score": rerank_scores.get(pos)}
            for pos, _ in fused[:10]
        ] if rerank_applied else [],
        "answerable": answerable,
        "refusal_reason": refusal_reason,
    })
    latency["total_ms"] = round((time.perf_counter() - started) * 1000, 2)
    trace["latency_ms"] = latency
    return RetrievalResult(
        candidates=candidates,
        answerable=answerable,
        refusal_reason=refusal_reason,
        degraded=degraded,
        trace=trace,
    )
