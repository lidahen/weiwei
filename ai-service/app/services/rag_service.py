"""RAG 检索服务：文本切片 + FAISS 向量索引构建/检索。

- 索引按知识库粒度持久化：data/faiss_indexes/kb_{knowledge_id}.faiss + .json（slice_id 顺序）
- embedding 未配置时，检索退化为 SQL 全文匹配（LIKE），保证功能可用
- FAISS 为同步库，耗时操作统一放入 asyncio.to_thread，避免阻塞事件循环

后续扩展：将 _save_index / _load_index 替换为 Milvus / pgvector 实现即可。
"""
import asyncio
import json
import logging
import os
from typing import Dict, List, Optional, Tuple

import numpy as np
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.ai_doc_slice import AiDocSlice
from app.services.embedding_service import embed_query, embed_texts, embedding_enabled

logger = logging.getLogger(__name__)

# 切片重叠字符数（保证上下文连贯）
_OVERLAP_RATIO = 0.08


def split_text(content: str, slice_size: int = 3000) -> List[str]:
    """按字符长度切分文本，尽量在段落边界断开，相邻切片保留少量重叠。"""
    content = (content or "").strip()
    if not content:
        return []
    if len(content) <= slice_size:
        return [content]

    overlap = min(200, max(20, int(slice_size * _OVERLAP_RATIO)))
    chunks: List[str] = []
    start = 0
    while start < len(content):
        # 剩余内容不超过重叠区大小时停止，避免切出碎片残片
        if len(content) - start <= overlap:
            break
        end = min(start + slice_size, len(content))
        if end < len(content):
            # 优先在段落边界断开
            boundary = content.rfind("\n", start + slice_size // 2, end)
            if boundary > start:
                end = boundary + 1
        chunk = content[start:end].strip()
        if chunk:
            chunks.append(chunk)
        start = max(end - overlap, start + 1)
    return chunks


def _index_paths(knowledge_id: int) -> Tuple[str, str]:
    os.makedirs(settings.faiss_index_path, exist_ok=True)
    return (
        os.path.join(settings.faiss_index_path, f"kb_{knowledge_id}.faiss"),
        os.path.join(settings.faiss_index_path, f"kb_{knowledge_id}.json"),
    )


def _save_index(knowledge_id: int, vectors: List[List[float]], slice_ids: List[int]) -> None:
    """同步构建并落盘 FAISS 索引（调用方负责 to_thread）。"""
    index_path, meta_path = _index_paths(knowledge_id)
    mat = np.array(vectors, dtype="float32")
    import faiss

    index = faiss.IndexFlatIP(mat.shape[1])
    faiss.normalize_L2(mat)
    index.add(mat)
    faiss.write_index(index, index_path)
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump({"slice_ids": slice_ids}, f, ensure_ascii=False)
    logger.info("FAISS index saved: kb=%s slices=%s", knowledge_id, len(slice_ids))


def _load_index(knowledge_id: int) -> Optional[Tuple[object, List[int]]]:
    """同步加载 FAISS 索引，返回 (index, slice_ids)。"""
    index_path, meta_path = _index_paths(knowledge_id)
    if not os.path.exists(index_path) or not os.path.exists(meta_path):
        return None
    import faiss

    index = faiss.read_index(index_path)
    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)
    return index, meta.get("slice_ids", [])


def _remove_index_files(knowledge_id: int) -> None:
    index_path, meta_path = _index_paths(knowledge_id)
    for p in (index_path, meta_path):
        if os.path.exists(p):
            os.remove(p)


async def rebuild_index(db: AsyncSession, knowledge_id: int) -> None:
    """重建某个知识库的 FAISS 索引。无切片或未配置 embedding 时删除/跳过索引。"""
    result = await db.execute(
        select(AiDocSlice)
        .where(AiDocSlice.knowledge_id == knowledge_id)
        .order_by(AiDocSlice.slice_id)
    )
    slices = result.scalars().all()
    if not slices:
        await asyncio.to_thread(_remove_index_files, knowledge_id)
        return

    if not embedding_enabled():
        for s in slices:
            s.vector_status = "pending"
        await db.commit()
        return

    texts = [s.content or "" for s in slices]
    try:
        vectors = await embed_texts(texts)
    except Exception as e:
        logger.error("rebuild_index embedding error: %s", e)
        for s in slices:
            s.vector_status = "pending"
        await db.commit()
        return

    slice_ids = [int(s.slice_id) for s in slices]
    await asyncio.to_thread(_save_index, knowledge_id, vectors, slice_ids)
    for s in slices:
        s.vector_status = "completed"
    await db.commit()


async def delete_index(knowledge_id: int) -> None:
    await asyncio.to_thread(_remove_index_files, knowledge_id)


async def search(
    db: AsyncSession,
    knowledge_id: int,
    query: str,
    top_k: int = 3,
    score_threshold: float = 0.5,
) -> List[Dict]:
    """向量检索知识库切片，返回 [{"slice_id": int, "score": float}, ...]。

    embedding 未配置时退化为全文匹配（LIKE），score 固定为 1.0。
    rerank 开启时：FAISS 先粗排（recall = top_n×4），再 cross-encoder 精排取 top_k；
    重排失败自动回退粗排原序（检索可用性优先）。
    """
    if not embedding_enabled():
        return await _search_fallback(db, knowledge_id, query, top_k)

    loaded = await asyncio.to_thread(_load_index, knowledge_id)
    if not loaded:
        return []
    index, slice_ids = loaded
    if index.ntotal == 0 or not slice_ids:
        return []

    try:
        qvec = await embed_query(query)
    except Exception as e:
        logger.error("search embed_query error: %s", e)
        return await _search_fallback(db, knowledge_id, query, top_k)

    qmat = np.array([qvec], dtype="float32")
    import faiss

    faiss.normalize_L2(qmat)
    recall_k = max(1, min(_recall_k(top_k), index.ntotal))
    scores, idxs = index.search(qmat, recall_k)

    results: List[Dict] = []
    for score, idx in zip(scores[0], idxs[0]):
        if idx < 0 or idx >= len(slice_ids):
            continue
        if float(score) < score_threshold:
            continue
        results.append({"slice_id": int(slice_ids[idx]), "score": float(score)})

    if not results:
        return results

    reranked = await _rerank(db, query, results, top_k)
    return reranked if reranked is not None else results[:top_k]


def _recall_k(top_k: int) -> int:
    """重排开启时粗排多召回候选，给精排留选择空间。"""
    if not settings.rerank_enabled:
        return top_k
    return max(top_k, settings.rerank_top_n * 4)


async def _rerank(
    db: AsyncSession, query: str, results: List[Dict], top_k: int
) -> Optional[List[Dict]]:
    """cross-encoder 精排：返回 top_k 结果；失败返回 None（调用方回退粗排原序）。"""
    import httpx

    if not settings.rerank_enabled or len(results) <= 1:
        return results[:top_k] if results else results
    api_key = settings.embedding_api_key
    if not api_key:
        return None

    ids = [r["slice_id"] for r in results]
    result = await db.execute(
        select(AiDocSlice).where(AiDocSlice.slice_id.in_(ids))
    )
    slice_map = {int(s.slice_id): (s.content or "") for s in result.scalars().all()}
    documents = [slice_map.get(i, "") for i in ids]
    pairs = [(i, d) for i, d in zip(ids, documents) if d]
    if not pairs:
        return None

    try:
        base = settings.embedding_base_url.rstrip("/")
        async with httpx.AsyncClient(timeout=settings.rerank_timeout) as client:
            resp = await client.post(
                f"{base}/rerank",
                headers={"Authorization": f"Bearer {api_key}"},
                json={
                    "model": settings.rerank_model,
                    "query": query,
                    "documents": [d for _, d in pairs],
                    "top_n": min(top_k, len(pairs)),
                },
            )
            resp.raise_for_status()
            data = resp.json()
    except Exception as e:
        logger.warning("rerank failed, fallback to vector order: %s", e)
        return None

    score_by_id = {ids[i]: float(item.get("relevance_score") or 0.0)
                   for i, item in enumerate(data.get("results") or [])}
    ranked = [
        {"slice_id": sid, "score": score_by_id[sid]}
        for sid in ids if sid in score_by_id
    ]
    ranked.sort(key=lambda r: r["score"], reverse=True)
    return ranked[:top_k]


async def _search_fallback(
    db: AsyncSession, knowledge_id: int, query: str, top_k: int
) -> List[Dict]:
    """无 embedding 时的全文匹配检索。"""
    kw = f"%{query.strip()}%"
    result = await db.execute(
        select(AiDocSlice)
        .where(AiDocSlice.knowledge_id == knowledge_id, AiDocSlice.content.like(kw))
        .limit(max(1, top_k))
    )
    return [{"slice_id": int(s.slice_id), "score": 1.0} for s in result.scalars().all()]
