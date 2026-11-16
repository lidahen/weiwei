"""商品向量检索服务：首页推荐向量通道 + 详情页相似商品。

- 索引按租户持久化：data/faiss_indexes/product_t{tenant_id}.faiss + .meta.json + .npy
  （meta 记录 product_id 顺序与 update_time 映射；.npy 存向量矩阵，重建索引无需重嵌）
- 商品文本 = 标题 + 类目 + 描述截断 + 价格（bge-m3，复用 embedding_service）
- 增量同步：只重嵌新增/变更（update_time 变化）商品，剔除已下架/删除商品
- embedding 未配置时抛 ProductVectorUnavailable，路由层返回 code=503，Java 侧降级同类目热门
- FAISS 为同步库：落盘/检索统一 asyncio.to_thread，避免阻塞事件循环

后续扩展：将 _save/_load 替换为 Milvus / pgvector 实现即可（同 rag_service 惯例）。
"""
import asyncio
import json
import logging
import os
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.config import settings
from app.services.embedding_service import embed_texts, embedding_enabled

logger = logging.getLogger(__name__)

# 描述参与向量化的最大字符数（标题+类目已承载主要语义）
_DESC_MAX_CHARS = 200


class ProductVectorUnavailable(Exception):
    """embedding 未配置或索引不可用（调用方应降级为规则通道）。"""


def product_text(row: Dict) -> str:
    """拼装商品向量化文本（纯逻辑，可单测）。"""
    parts = [str(row.get("title") or "").strip()]
    cat = "/".join(str(row.get(k) or "").strip() for k in ("cat_l1", "cat_l2") if row.get(k))
    if cat:
        parts.append(f"类目：{cat}")
    desc = str(row.get("description") or "").strip().replace("\n", " ")
    if desc:
        parts.append(desc[:_DESC_MAX_CHARS])
    price = row.get("price")
    if price is not None:
        parts.append(f"价格{price}元")
    return "。".join(p for p in parts if p)


def diff_rows(rows: List[Dict], updated: Dict[int, str]) -> Tuple[List[Dict], List[Dict]]:
    """对比当前在售商品与索引元数据（纯逻辑，可单测）。

    返回 (changed, unchanged)：changed = 新增或 update_time 变化（需重嵌）；
    unchanged = 已在索引且未变化（复用旧向量）。
    """
    changed: List[Dict] = []
    unchanged: List[Dict] = []
    for row in rows:
        pid = int(row["product_id"])
        utime = row.get("update_time")
        utime_str = utime.isoformat() if hasattr(utime, "isoformat") else str(utime or "")
        if updated.get(pid) != utime_str:
            changed.append(row)
        else:
            unchanged.append(row)
    return changed, unchanged


class ProductVectorService:
    """每租户一套索引；进程内缓存 + 文件持久化。"""

    def __init__(self) -> None:
        self._cache: Dict[int, dict] = {}
        self._locks: Dict[int, asyncio.Lock] = {}

    # ---------- 存储路径 ----------

    def _paths(self, tenant_id: int) -> Tuple[str, str, str]:
        os.makedirs(settings.faiss_index_path, exist_ok=True)
        base = os.path.join(settings.faiss_index_path, f"product_t{tenant_id}")
        return (f"{base}.faiss", f"{base}.meta.json", f"{base}.npy")

    # ---------- 加载 / 保存 ----------

    def _load(self, tenant_id: int) -> Optional[dict]:
        """同步加载磁盘索引（调用方负责 to_thread / 缓存判断）。"""
        index_path, meta_path, npy_path = self._paths(tenant_id)
        if not (os.path.exists(index_path) and os.path.exists(meta_path) and os.path.exists(npy_path)):
            return None
        import faiss

        with open(meta_path, "r", encoding="utf-8") as f:
            meta = json.load(f)
        vectors = np.load(npy_path)
        return {
            "ids": [int(i) for i in meta.get("ids", [])],
            "updated": {int(k): v for k, v in meta.get("updated", {}).items()},
            "vectors": vectors,
            "index": faiss.read_index(index_path),
        }

    def _get(self, tenant_id: int) -> Optional[dict]:
        if tenant_id not in self._cache:
            loaded = self._load(tenant_id)
            if loaded is None:
                return None
            self._cache[tenant_id] = loaded
        return self._cache[tenant_id]

    def _save(self, tenant_id: int, ids: List[int], updated: Dict[int, str],
              vectors: np.ndarray) -> None:
        """同步构建并落盘 FAISS 索引（调用方负责 to_thread）。"""
        import faiss

        index_path, meta_path, npy_path = self._paths(tenant_id)
        mat = np.ascontiguousarray(vectors, dtype="float32")
        index = faiss.IndexFlatIP(mat.shape[1])
        faiss.normalize_L2(mat)
        index.add(mat)
        faiss.write_index(index, index_path)
        np.save(npy_path, mat)
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump({"ids": ids, "updated": {str(k): v for k, v in updated.items()}},
                      f, ensure_ascii=False)
        self._cache[tenant_id] = {
            "ids": ids, "updated": updated, "vectors": mat, "index": index,
        }

    # ---------- 数据获取 ----------

    async def _fetch_onsale(self, tenant_id: int) -> List[Dict]:
        from sqlalchemy import text

        from app.database import async_session

        async with async_session() as db:
            res = await db.execute(text(
                """
                SELECT product_id, title, cat_l1, cat_l2, description, price, update_time
                FROM product
                WHERE tenant_id = :tid AND status = 'onsale'
                  AND title IS NOT NULL AND title != ''
                """
            ), {"tid": tenant_id})
            return [dict(r._mapping) for r in res]

    async def _fetch_all_tenants(self) -> List[int]:
        from sqlalchemy import text

        from app.database import async_session

        async with async_session() as db:
            res = await db.execute(text(
                "SELECT DISTINCT tenant_id FROM product WHERE tenant_id IS NOT NULL AND status = 'onsale'"
            ))
            return [int(r[0]) for r in res]

    # ---------- 对外能力 ----------

    def _lock(self, tenant_id: int) -> asyncio.Lock:
        if tenant_id not in self._locks:
            self._locks[tenant_id] = asyncio.Lock()
        return self._locks[tenant_id]

    async def reindex(self, tenant_id: int) -> int:
        """增量同步单租户索引，返回索引内商品数。"""
        if not embedding_enabled():
            raise ProductVectorUnavailable("embedding 未配置（EMBEDDING_API_KEY）")
        async with self._lock(tenant_id):
            rows = await self._fetch_onsale(tenant_id)
            cached = self._get(tenant_id) or {"ids": [], "updated": {}, "vectors": None}
            changed, unchanged = diff_rows(rows, cached["updated"])
            kept_updated = {int(r["product_id"]): self._utime_str(r) for r in unchanged}

            vectors_by_id: Dict[int, np.ndarray] = {}
            old_ids = cached.get("ids") or []
            old_vecs = cached.get("vectors")
            if old_vecs is not None:
                for i, pid in enumerate(old_ids):
                    if pid in {int(r["product_id"]) for r in unchanged}:
                        vectors_by_id[pid] = np.asarray(old_vecs)[i]

            new_vecs: List[List[float]] = []
            if changed:
                new_vecs = await embed_texts([product_text(r) for r in changed])
            merged_ids: List[int] = [int(r["product_id"]) for r in unchanged]
            for row, vec in zip(changed, new_vecs):
                pid = int(row["product_id"])
                merged_ids.append(pid)
                vectors_by_id[pid] = np.asarray(vec, dtype="float32")
                kept_updated[pid] = self._utime_str(row)

            if not merged_ids:
                return 0
            dim = len(vectors_by_id[merged_ids[0]])
            mat = np.zeros((len(merged_ids), dim), dtype="float32")
            for i, pid in enumerate(merged_ids):
                mat[i] = vectors_by_id[pid]
            await asyncio.to_thread(self._save, tenant_id, merged_ids, kept_updated, mat)
            return len(merged_ids)

    async def reindex_all(self) -> int:
        """同步全部租户（Quartz 每日调用），返回累计索引商品数。"""
        tenant_ids = await self._fetch_all_tenants()
        total = 0
        for tid in tenant_ids:
            total += await self.reindex(tid)
        return total

    async def similar(self, tenant_id: int, product_id: int, limit: int = 6) -> List[Dict]:
        """详情页相似商品：[{productId, score}]，按相似度倒序，不含自身。"""
        data = await asyncio.to_thread(self._get, tenant_id)
        if data is None:
            raise ProductVectorUnavailable(f"租户 {tenant_id} 商品向量索引未构建")
        ids: List[int] = data["ids"]
        if product_id not in ids:
            return []
        idx = ids.index(product_id)
        query = np.asarray(data["vectors"])[idx:idx + 1]
        k = min(len(ids), limit + 1)
        scores, positions = await asyncio.to_thread(self._search, data["index"], query, k)
        hits: List[Dict] = []
        for score, pos in zip(scores[0], positions[0]):
            pid = ids[pos]
            if pid == product_id:
                continue
            hits.append({"productId": pid, "score": round(float(score), 4)})
            if len(hits) >= limit:
                break
        return hits

    async def candidates(self, tenant_id: int, seed_product_ids: List[int],
                         limit: int = 20) -> List[Dict]:
        """首页向量召回：seed 商品向量归一化均值 → top-N 候选，排除 seed 自身。"""
        data = await asyncio.to_thread(self._get, tenant_id)
        if data is None:
            raise ProductVectorUnavailable(f"租户 {tenant_id} 商品向量索引未构建")
        ids: List[int] = data["ids"]
        id_pos = {pid: i for i, pid in enumerate(ids)}
        seed_pos = [id_pos[p] for p in seed_product_ids if p in id_pos]
        if not seed_pos:
            return []
        vecs = np.asarray(data["vectors"])[seed_pos]
        query = vecs.mean(axis=0, dtype=np.float32).reshape(1, -1)
        k = min(len(ids), limit + len(seed_pos))
        scores, positions = await asyncio.to_thread(self._search, data["index"], query, k)
        seed_set = set(seed_product_ids)
        hits: List[Dict] = []
        for score, pos in zip(scores[0], positions[0]):
            pid = ids[pos]
            if pid in seed_set:
                continue
            hits.append({"productId": pid, "score": round(float(score), 4)})
            if len(hits) >= limit:
                break
        return hits

    # ---------- 内部 ----------

    @staticmethod
    def _search(index, query: np.ndarray, k: int) -> Tuple[np.ndarray, np.ndarray]:
        import faiss

        q = np.ascontiguousarray(query, dtype="float32")
        faiss.normalize_L2(q)
        return index.search(q, k)

    @staticmethod
    def _utime_str(row: Dict) -> str:
        utime = row.get("update_time")
        return utime.isoformat() if hasattr(utime, "isoformat") else str(utime or "")


service = ProductVectorService()
