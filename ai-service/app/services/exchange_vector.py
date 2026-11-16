"""换物双向向量匹配服务：Square「为你推荐」正向召回 + 详情页/推送「可能想换的人」反向召回。

- 双索引按租户持久化：data/faiss_indexes/exswap_supply_t{tenant_id}.* 与 exswap_demand_t{tenant_id}.*
  （.faiss + .meta.json + .npy 三件套，meta 记录 item_id 顺序与 update_time 映射；.npy 存向量矩阵，
  重建索引无需重嵌——骨架整体克隆 product_vector，换成两条索引）
- 数据源：直读 MySQL twelve_exchange_item WHERE status='onswap'，SQL 写法镜像 _fetch_onsale
- 供给文本 = 标题 + 类目中文名 + 成色 + 描述；需求文本 = want_desc + want_cats 类目中文名
  （want 文本全空的物品不进需求索引——无有效需求语义，设计文档 3.1）
- 类目中文名：product_category_dict 内存缓存（code→name，首次使用时读一次），查不到退回 code 本身
- 增量同步：只重嵌新增/变更（update_time 变化）物品，剔除已下架/需求清空物品
- embedding 未配置时抛 ExchangeVectorUnavailable，路由层返回 code=503，Java 侧降级（空列表/隐藏/跳过）
- FAISS 索引主键一律 item_id（不是 user_id）：双向命中都落到物品，
  "想换我的人" = 需求命中物品的 owner，由 Java 侧 JOIN 反查（设计文档 3.1）
- FAISS 为同步库：落盘/检索统一 asyncio.to_thread，避免阻塞事件循环

后续扩展：将 _save/_load 替换为 Milvus / pgvector 实现即可（同 rag_service 惯例）。
"""
import asyncio
import json
import logging
import os
from typing import Callable, Dict, List, Optional, Set, Tuple

import numpy as np

from app.config import settings
from app.services.embedding_service import embed_texts, embedding_enabled
from app.services.product_recognition import CONDITION_LABELS

logger = logging.getLogger(__name__)

# 描述参与向量化的最大字符数（标题+类目已承载主要语义）
_DESC_MAX_CHARS = 200


class ExchangeVectorUnavailable(Exception):
    """embedding 未配置或索引不可用（调用方应降级为空结果/隐藏区块）。"""


def _cat_label(row: Dict, keys: Tuple[str, ...], cat_names: Dict[str, str]) -> str:
    """行内类目 code 串拼接中文名（纯逻辑）：查不到缓存退回 code 本身。"""
    return "/".join(
        cat_names.get(code, code)
        for code in (str(row.get(k) or "").strip() for k in keys)
        if code
    )


def supply_text(row: Dict, cat_names: Dict[str, str]) -> str:
    """拼装换物供给向量化文本（纯逻辑，可单测）：标题 + 类目中文名 + 成色 + 描述。"""
    parts = [str(row.get("title") or "").strip()]
    cat = _cat_label(row, ("cat_l1", "cat_l2"), cat_names)
    if cat:
        parts.append(f"类目：{cat}")
    condition = str(row.get("condition_level") or "").strip()
    if condition:
        parts.append(f"成色：{CONDITION_LABELS.get(condition, condition)}")
    desc = str(row.get("description") or "").strip().replace("\n", " ")
    if desc:
        parts.append(desc[:_DESC_MAX_CHARS])
    return "。".join(p for p in parts if p)


def parse_want_cats(raw: object) -> List[str]:
    """want_cats JSON 数组字符串 → 类目 code 列表（纯逻辑，可单测）。

    容错：空值/非法 JSON/非数组一律返回空列表（该物品需求文本只看 want_desc）。
    """
    if not raw:
        return []
    try:
        data = json.loads(raw) if isinstance(raw, (str, bytes)) else raw
    except (ValueError, TypeError):
        return []
    if not isinstance(data, list):
        return []
    return [str(c).strip() for c in data if str(c or "").strip()]


def demand_text(row: Dict, cat_names: Dict[str, str]) -> str:
    """拼装换物需求向量化文本（纯逻辑，可单测）：want_desc + want_cats 类目中文名。

    两者全空返回空串（调用方据此把该物品排除出需求索引，设计文档 3.1）。
    """
    parts: List[str] = []
    want_desc = str(row.get("want_desc") or "").strip().replace("\n", " ")
    if want_desc:
        parts.append(want_desc[:_DESC_MAX_CHARS])
    cat = "/".join(cat_names.get(c, c) for c in parse_want_cats(row.get("want_cats")))
    if cat:
        parts.append(f"期望类目：{cat}")
    return "。".join(p for p in parts if p)


def diff_rows(rows: List[Dict], updated: Dict[int, str]) -> Tuple[List[Dict], List[Dict]]:
    """对比当前在换物品与索引元数据（纯逻辑，可单测）。

    返回 (changed, unchanged)：changed = 新增或 update_time 变化（需重嵌）；
    unchanged = 已在索引且未变化（复用旧向量）。
    """
    changed: List[Dict] = []
    unchanged: List[Dict] = []
    for row in rows:
        iid = int(row["item_id"])
        utime = row.get("update_time")
        utime_str = utime.isoformat() if hasattr(utime, "isoformat") else str(utime or "")
        if updated.get(iid) != utime_str:
            changed.append(row)
        else:
            unchanged.append(row)
    return changed, unchanged


class _SideIndex:
    """单侧（供给/需求）租户索引：路径/加载/落盘/增量逻辑与 ProductVectorService 同构。

    数据源与文本构造由 ExchangeVectorService 注入（rows + text_of），文件前缀区分两侧。
    """

    def __init__(self, prefix: str, label: str) -> None:
        self._prefix = prefix
        self._label = label  # 供给/需求，用于降级文案区分
        self._cache: Dict[int, dict] = {}
        self._locks: Dict[int, asyncio.Lock] = {}

    # ---------- 存储路径 ----------

    def _paths(self, tenant_id: int) -> Tuple[str, str, str]:
        os.makedirs(settings.faiss_index_path, exist_ok=True)
        base = os.path.join(settings.faiss_index_path, f"{self._prefix}{tenant_id}")
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

    # ---------- 对外能力 ----------

    def _lock(self, tenant_id: int) -> asyncio.Lock:
        if tenant_id not in self._locks:
            self._locks[tenant_id] = asyncio.Lock()
        return self._locks[tenant_id]

    async def reindex(self, tenant_id: int, rows: List[Dict],
                      text_of: Callable[[Dict], str]) -> int:
        """增量同步单侧索引，返回索引内物品数（逻辑镜像 product_vector.reindex）。

        rows 为该侧应入索引的物品行（需求侧已过滤 want 文本全空的行）；
        不在 rows 中的旧物品（已下架/需求清空）随重建自然剔除。
        """
        async with self._lock(tenant_id):
            cached = self._get(tenant_id) or {"ids": [], "updated": {}, "vectors": None}
            changed, unchanged = diff_rows(rows, cached["updated"])
            kept_updated = {int(r["item_id"]): self._utime_str(r) for r in unchanged}

            vectors_by_id: Dict[int, np.ndarray] = {}
            old_ids = cached.get("ids") or []
            old_vecs = cached.get("vectors")
            if old_vecs is not None:
                for i, iid in enumerate(old_ids):
                    if iid in {int(r["item_id"]) for r in unchanged}:
                        vectors_by_id[iid] = np.asarray(old_vecs)[i]

            new_vecs: List[List[float]] = []
            if changed:
                new_vecs = await embed_texts([text_of(r) for r in changed])
            merged_ids: List[int] = [int(r["item_id"]) for r in unchanged]
            for row, vec in zip(changed, new_vecs):
                iid = int(row["item_id"])
                merged_ids.append(iid)
                vectors_by_id[iid] = np.asarray(vec, dtype="float32")
                kept_updated[iid] = self._utime_str(row)

            if not merged_ids:
                return 0
            dim = len(vectors_by_id[merged_ids[0]])
            mat = np.zeros((len(merged_ids), dim), dtype="float32")
            for i, iid in enumerate(merged_ids):
                mat[i] = vectors_by_id[iid]
            await asyncio.to_thread(self._save, tenant_id, merged_ids, kept_updated, mat)
            return len(merged_ids)

    async def search(self, tenant_id: int, query: np.ndarray, limit: int,
                     exclude_ids: Optional[Set[int]] = None) -> List[Dict]:
        """向量查询本侧索引：[{itemId, score}]，按相似度倒序，可排除指定物品。"""
        data = await asyncio.to_thread(self._get, tenant_id)
        if data is None:
            raise ExchangeVectorUnavailable(f"租户 {tenant_id} 换物{self._label}向量索引未构建")
        ids: List[int] = data["ids"]
        k = min(len(ids), limit + len(exclude_ids or ()))
        scores, positions = await asyncio.to_thread(self._search, data["index"], query, k)
        hits: List[Dict] = []
        for score, pos in zip(scores[0], positions[0]):
            iid = ids[pos]
            if exclude_ids and iid in exclude_ids:
                continue
            hits.append({"itemId": iid, "score": round(float(score), 4)})
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


class ExchangeVectorService:
    """每租户两套索引（供给/需求）；进程内缓存 + 文件持久化，主键均为 item_id。"""

    def __init__(self) -> None:
        self._supply = _SideIndex("exswap_supply_t", "供给")
        self._demand = _SideIndex("exswap_demand_t", "需求")
        self._cat_names: Optional[Dict[str, str]] = None

    # ---------- 数据获取 ----------

    async def _fetch_onswap(self, tenant_id: int) -> List[Dict]:
        from sqlalchemy import text

        from app.database import async_session

        async with async_session() as db:
            res = await db.execute(text(
                """
                SELECT id AS item_id, tenant_id, title, description, cat_l1, cat_l2,
                       condition_level, want_desc, want_cats, accept_any, update_time
                FROM twelve_exchange_item
                WHERE tenant_id = :tid AND status = 'onswap'
                  AND title IS NOT NULL AND title != ''
                """
            ), {"tid": tenant_id})
            return [dict(r._mapping) for r in res]

    async def _fetch_all_tenants(self) -> List[int]:
        from sqlalchemy import text

        from app.database import async_session

        async with async_session() as db:
            res = await db.execute(text(
                "SELECT DISTINCT tenant_id FROM twelve_exchange_item "
                "WHERE tenant_id IS NOT NULL AND status = 'onswap'"
            ))
            return [int(r[0]) for r in res]

    async def _category_names(self) -> Dict[str, str]:
        """product_category_dict 内存缓存（code→name，首次使用时读一次）。

        失败容忍：查询异常只告警并返回空映射（类目退回 code 本身），不阻塞索引构建；
        失败不写缓存，下次调用重试。
        """
        if self._cat_names is not None:
            return self._cat_names
        from sqlalchemy import text

        from app.database import async_session

        try:
            async with async_session() as db:
                res = await db.execute(text(
                    "SELECT code, name FROM product_category_dict"
                ))
                rows = [dict(r._mapping) for r in res]
        except Exception as e:  # noqa: BLE001 —— 类目名是增强语义，查不到退回 code，不阻塞
            logger.warning("加载类目字典失败（类目名退回 code）: %s", e)
            return {}
        self._cat_names = {
            str(r["code"]): str(r["name"]) for r in rows if r.get("code") and r.get("name")
        }
        return self._cat_names

    # ---------- 对外能力 ----------

    async def reindex(self, tenant_id: int) -> int:
        """增量同步单租户双索引（供给 + 需求），返回供给索引内物品数。

        want 文本全空的物品不进需求索引（无有效需求语义，设计文档 3.1）。
        """
        if not embedding_enabled():
            raise ExchangeVectorUnavailable("embedding 未配置（EMBEDDING_API_KEY）")
        rows = await self._fetch_onswap(tenant_id)
        cat_names = await self._category_names()
        supply_count = await self._supply.reindex(
            tenant_id, rows, lambda r: supply_text(r, cat_names))
        demand_rows = [r for r in rows if demand_text(r, cat_names)]
        await self._demand.reindex(
            tenant_id, demand_rows, lambda r: demand_text(r, cat_names))
        return supply_count

    async def reindex_all(self) -> int:
        """同步全部租户（Job 124 每 30 分钟调用），返回累计供给索引物品数。"""
        tenant_ids = await self._fetch_all_tenants()
        total = 0
        for tid in tenant_ids:
            total += await self.reindex(tid)
        return total

    async def match_for_demand(self, tenant_id: int, user_texts: List[str],
                               limit: int = 8) -> List[Dict]:
        """Square「为你推荐」：用户多条需求文本均值向量 ↔ 供给索引，[{itemId, score}]。

        user_texts = 该用户全部 onswap 物品的 want_desc 集合（Java 收集后透传），
        均值逻辑镜像 product_vector.candidates；不在此排除用户自有物品（Java 侧过滤）。
        """
        if not embedding_enabled():
            raise ExchangeVectorUnavailable("embedding 未配置（EMBEDDING_API_KEY）")
        texts = [str(t or "").strip() for t in user_texts]
        texts = [t for t in texts if t]
        if not texts:
            return []
        try:
            vecs = np.asarray(await embed_texts(texts), dtype="float32")
        except Exception as e:  # noqa: BLE001 —— 查询期 SiliconFlow 不可用按设计降级为 503
            raise ExchangeVectorUnavailable(f"需求文本向量化失败: {e}") from e
        query = vecs.mean(axis=0, dtype=np.float32).reshape(1, -1)
        return await self._supply.search(tenant_id, query, limit)

    async def match_wanters(self, tenant_id: int, item_id: int, limit: int = 6) -> List[Dict]:
        """详情页「可能想换的人」/ Job 125 推送扫描：物品供给向量 ↔ 需求索引。

        返回需求侧 [{itemId, score}]——命中物品即"想换的人"的物品，Java 侧反查 owner；
        排除物品自身（自身 want 文本命中不构成"别人想换"）。
        """
        data = await asyncio.to_thread(self._supply._get, tenant_id)
        if data is None:
            raise ExchangeVectorUnavailable(f"租户 {tenant_id} 换物供给向量索引未构建")
        ids: List[int] = data["ids"]
        if item_id not in ids:
            return []
        idx = ids.index(item_id)
        query = np.asarray(data["vectors"])[idx:idx + 1]
        return await self._demand.search(tenant_id, query, limit, exclude_ids={item_id})


service = ExchangeVectorService()
