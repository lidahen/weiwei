"""商品检索服务 —— 供导购 Agent 的 search_products 工具调用。

零结果自动放宽（分级降级）：严格条件搜不到时逐级放宽重试，
并在返回的 search_note 中告知模型放宽了什么，让回复对用户诚实：
  L0 严格：关键词(title/description LIKE) + 分类 + 城市 + 价格区间 + 成色
  L1 放宽成色：成色条件丢弃（同义词未命中库内取值时兜底）
  L2 关键词分词 OR：多词取 OR 模糊匹配（标题+描述）
  L3 热门兜底：丢弃关键词/价格，按热度推荐在售商品（浏览兜底，避免冷回复）
"""
import re
from typing import Any, Dict, List, Optional, Tuple

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.chat import ProductCard

_BASE_SQL = """
    SELECT p.product_id, p.title, p.cover, p.price, p.original_price,
           p.`condition`, p.city, u.nick_name as seller_nick_name
    FROM `product` p LEFT JOIN `sys_user` u ON p.seller_id = u.user_id
    WHERE p.`status` = 'onsale'
"""

# 成色数字等级 → 库内可能出现的写法（95新/九五新/95成新/九五成新…）
_CN_DIGITS = {"0": "零", "1": "一", "2": "二", "3": "三", "4": "四",
              "5": "五", "6": "六", "7": "七", "8": "八", "9": "九"}


def normalize_condition_terms(condition: Optional[str]) -> List[str]:
    """把模型可能传入的成色描述归一成一组同义词，用于 LIKE OR 匹配。

    例："95新" → ["%95新%", "%九五新%", "%95成新%", "%九五成新%"]；
    非等级类取值（如"全新"）原样返回。
    """
    raw = (condition or "").strip()
    if not raw:
        return []
    terms = [f"%{raw}%"]
    m = re.search(r"(\d{1,2})\s*成?新", raw)
    if m:
        n = m.group(1)
        cn = "".join(_CN_DIGITS.get(ch, ch) for ch in n)
        terms = [
            f"%{n}新%", f"%{cn}新%",
            f"%{n}成新%", f"%{cn}成新%",
        ]
    # 去重保序
    seen: set = set()
    return [t for t in terms if not (t in seen or seen.add(t))]


def split_keywords(keyword: Optional[str]) -> List[str]:
    """把搜索词拆成 OR 模糊匹配的词元（按空白/标点切分；中文长词原样保留）。"""
    raw = (keyword or "").strip()
    if not raw:
        return []
    tokens = [t for t in re.split(r"[\s,，、;；/\\|]+", raw) if t]
    return tokens or [raw]


def build_search_variants(
    keyword: Optional[str] = None,
    condition: Optional[str] = None,
    price_min: Optional[float] = None,
    price_max: Optional[float] = None,
) -> List[Tuple[Dict[str, Any], str]]:
    """生成分级放宽的查询变体列表（有序）：[(sql_fragment_and_params, note)]。

    每个元素返回追加在 _BASE_SQL 之后的 WHERE 片段与绑定参数、以及该级别的说明。
    """
    variants: List[Tuple[Dict[str, Any], str]] = []
    cond_terms = normalize_condition_terms(condition)
    tokens = split_keywords(keyword)

    def add(where: List[str], params: Dict[str, Any], note: str) -> None:
        variants.append(({"where": where, "params": params}, note))

    # L0 严格
    where: List[str] = []
    params: Dict[str, Any] = {}
    if keyword:
        where.append("(p.title LIKE :kw OR p.`description` LIKE :kw)")
        params["kw"] = f"%{keyword.strip()}%"
    if cond_terms:
        ors = " OR ".join(f"p.`condition` LIKE :cond{i}" for i in range(len(cond_terms)))
        where.append(f"({ors})")
        for i, t in enumerate(cond_terms):
            params[f"cond{i}"] = t
    if price_min is not None:
        where.append("p.price >= :price_min")
        params["price_min"] = price_min
    if price_max is not None:
        where.append("p.price <= :price_max")
        params["price_max"] = price_max
    add(list(where), dict(params), "")

    # L1 放宽成色（条件存在但同义词没匹配上时最有价值）
    if cond_terms:
        w = [x for x in where if ":cond" not in x]
        add(w, {k: v for k, v in params.items() if not k.startswith("cond")},
            "没有成色完全匹配的商品，已放宽成色要求")

    # L2 关键词分词 OR（多词搜索被整句 LIKE 拖死时生效）
    if len(tokens) > 1:
        w = [x for x in where if ":kw" not in x]
        ors = " OR ".join(
            f"(p.title LIKE :tkw{i} OR p.`description` LIKE :tkw{i})"
            for i in range(len(tokens))
        )
        w.append(f"({ors})")
        p2 = {k: v for k, v in params.items() if k != "kw"}
        for i, t in enumerate(tokens):
            p2[f"tkw{i}"] = f"%{t}%"
        add(w, p2, "没有与完整描述完全匹配的商品，已按关键词模糊匹配")

    # L3 热门兜底（仅当原本带关键词/价格条件时才有意义）
    if keyword or price_min is not None or price_max is not None:
        add([], {}, "没有找到与需求精确匹配的商品，为你推荐当前热门在售商品")

    return variants


async def search_products(
    db: AsyncSession,
    keyword: Optional[str] = None,
    cat_l1: Optional[str] = None,
    city: Optional[str] = None,
    price_min: Optional[float] = None,
    price_max: Optional[float] = None,
    condition: Optional[str] = None,
    order_by: Optional[str] = "default",
    tenant_id: Optional[int] = None,
    limit: int = 8,
) -> Dict[str, Any]:
    """分级放宽检索：命中即返回，全部未命中返回 L3 热门兜底。

    返回 {"products": [ProductCard...], "search_note": str}，
    search_note 非空表示发生过放宽，模型应向用户如实说明。
    """
    limit = max(1, min(limit or 8, 20))
    # 注意：`condition` 是 MySQL 保留字，所有列名都用反引号包裹。
    for variant, note in build_search_variants(
        keyword=keyword, condition=condition,
        price_min=price_min, price_max=price_max,
    ):
        sql = _BASE_SQL
        params: Dict[str, Any] = {}
        # cat_l1 / city 恒定条件（不参与放宽）
        where: List[str] = list(variant["where"])
        params.update(variant["params"])
        if cat_l1:
            where.append("p.cat_l1 = :cat_l1")
            params["cat_l1"] = cat_l1
        if city:
            where.append("p.city = :city")
            params["city"] = city
        if tenant_id and tenant_id > 0:
            where.append("p.tenant_id = :tid")
            params["tid"] = int(tenant_id)
        if where:
            sql += " AND " + " AND ".join(where)
        order_map = {
            "price_asc": "p.price ASC",
            "price_desc": "p.price DESC",
            "hot": "p.view_count DESC",
        }
        # 兜底级别按热度排，其余保持默认（置顶/最新优先）
        is_fallback = note.startswith("没有找到与需求精确匹配")
        order_key = "hot" if is_fallback else (order_by or "default")
        sql += " ORDER BY {}".format(
            order_map.get(order_key, "p.is_pinned DESC, p.publish_time DESC")
        )
        sql += " LIMIT :limit"
        params["limit"] = limit

        rows = (await db.execute(text(sql), params)).fetchall()
        if rows:
            products = [
                ProductCard(
                    product_id=r[0],
                    title=r[1],
                    cover=r[2],
                    price=float(r[3]) if r[3] is not None else None,
                    original_price=float(r[4]) if r[4] is not None else None,
                    condition=r[5],
                    city=r[6],
                    seller_nick_name=r[7],
                )
                for r in rows
            ]
            return {"products": products, "search_note": note}
    # 理论不可达（L3 无条件必命中空库除外）
    return {"products": [], "search_note": "当前没有在售商品"}
