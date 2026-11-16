"""电商 Agent 工具层 —— 方案 4.2 / 4.5。

分级约定（写在各工具 docstring 首行）：
  [L1] 查询类：工具直接执行，无需确认；
  [L2] 修改类：执行前必须由确认机制向用户复述，确认后才落库；
  [L3] 资金类：强制确认 + 幂等键防重复扣款。

确认机制（MVP 两段式，替代 interrupt 挂起）：
  - L2/L3 工具首次被调用时返回 {"need_confirm": true, "confirm_token": ...}，
    模型据此向用户复述"将执行 X，确认吗？"；
  - 用户确认后，模型携带 confirm_token 再次调用同一工具，此时真正执行。
  该机制对模型透明、无需 checkpointer，后续可平滑升级为 interrupt 方案。
"""
import hashlib
import json
import logging
import time
from datetime import datetime
from typing import Annotated, Any, Dict, List, Optional

import httpx
from langchain_core.tools import tool
from langchain_core.tools.base import InjectedToolArg

from app.config import settings
from app.database import async_session
from app.services import product_search

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────
# 确认状态 / 幂等键：Redis 优先（跨进程共享、TTL 自动过期），不可用时进程内兜底
# ─────────────────────────────────────────────
_pending_confirms: Dict[str, Dict[str, Any]] = {}
_executed_keys: Dict[str, float] = {}  # 幂等键 -> 时间戳，防重复执行（内存兜底用）

_PENDING_CONFIRM_TTL = 600  # 确认令牌有效期（秒）
_IDEM_WINDOW_TTL = 3600  # 幂等键保留窗口（秒）
_CONFIRM_KEY_PREFIX = "twt:ai:confirm:"
_IDEM_KEY_PREFIX = "twt:ai:idem:"
DEVIATION_RATIO = 1.4  # 估价偏离提醒阈值：成交/挂价与建议价比值超出 [1/1.4, 1.4] 即提示
RISK_DISPUTE_WINDOW_DAYS = 90  # 卖家纠纷统计窗口（天）
RISK_SELLER_INACTIVE_DAYS = 30  # 卖家超过该天数未登录则提示活跃度风险


class JavaInternalUnavailable(Exception):
    """调用 Java 内部接口失败（含服务间令牌未配置）。

    调用方必须据此向用户明确报错，**不得回退为直写数据库** —— 直写正是跨租户缺陷的成因。
    """


async def _java_internal_create_order(
    user_id: int, product_id: int, address_id: Optional[int] = None
) -> Dict[str, Any]:
    """经 Java 内部端点下单（订单的租户、运费、商品快照全部由 Java 侧负责）。

    ``address_id`` 为 express 商品必传（Java 侧校验链要求收货地址；缺失会被以
    "请选择收货地址" 拒绝）；self_pickup 商品可省略。

    为什么不能在 Python 侧直写 trade_order：
      trade_order.tenant_id 为 NOT NULL DEFAULT 1，而订单租户本应与商品同租户
      （见 Java 侧 TradeOrderServiceImpl.createOrder）。直写会把该列交给 DB 缺省值，
      在 tenant.enabled=true 下导致订单落进默认租户 —— 买家查不到自己的单，
      默认租户的订单列表里却混入了别人的单。
    """
    token = (settings.internal_api_token or "").strip()
    if not token:
        raise JavaInternalUnavailable(
            "服务间令牌未配置（INTERNAL_API_TOKEN），为避免跨租户写入已拒绝本次下单，请联系管理员"
        )
    url = f"{(settings.java_base_url or '').rstrip('/')}/internal/trade/order/create"
    headers = {"X-Internal-Token": token}
    payload: Dict[str, Any] = {"userId": user_id, "productId": product_id}
    if address_id:
        payload["addressId"] = int(address_id)
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(settings.java_internal_timeout)) as client:
            resp = await client.post(
                url, json=payload, headers=headers
            )
    except httpx.HTTPError as e:
        # 日志保留细节便于排障，但不把底层异常原文抛给用户
        logger.error("调用 Java 内部下单接口失败: %s", e)
        raise JavaInternalUnavailable("下单服务暂时不可用，请稍后重试") from e
    if resp.status_code == 403:
        raise JavaInternalUnavailable("下单服务鉴权失败，请联系管理员核对服务间令牌配置")
    try:
        payload = resp.json()
    except ValueError as e:
        raise JavaInternalUnavailable(f"下单服务返回了非预期内容（HTTP {resp.status_code}）") from e
    if resp.status_code != 200 or payload.get("code") != 200:
        raise JavaInternalUnavailable(payload.get("msg") or f"下单失败（HTTP {resp.status_code}）")
    data = payload.get("data") or {}
    if not data.get("orderNo"):
        raise JavaInternalUnavailable("下单服务未返回订单号")
    return data


def _new_confirm_token(action: str, params: dict) -> str:
    raw = f"{action}:{json.dumps(params, sort_keys=True, ensure_ascii=False)}:{time.time()}"
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]


def _idempotency_key(user_id: Optional[str], action: str, params: dict, window_sec: int = 60) -> str:
    """幂等键：user_id + 动作 + 业务参数指纹 + 短时间窗。

    同动作同参数在窗口内重复调用（并发重放/超时重试）返回已处理；
    不同业务参数（如不同商品、不同订单）互不干扰，不会误伤合法连续操作。
    """
    fp = hashlib.sha1(
        json.dumps(params, sort_keys=True, ensure_ascii=False).encode("utf-8")
    ).hexdigest()[:12]
    ts_window = int(time.time() // window_sec)
    return f"{user_id or 'anon'}:{action}:{fp}:{ts_window}"


async def _save_pending_confirm(token: str, action: str, params: dict) -> None:
    """保存确认令牌：Redis 可用时跨进程共享（TTL 自动过期），否则进程内字典。"""
    record = {"action": action, "params": params, "ts": time.time()}
    from app.services.redis_client import get_redis, json_set

    r = await get_redis()
    if r is not None:
        await json_set(_CONFIRM_KEY_PREFIX + token, record, _PENDING_CONFIRM_TTL)
        return
    _pending_confirms[token] = record


async def _pop_valid_confirm(token: str, user_id: str) -> Optional[Dict[str, Any]]:
    """校验并取出确认令牌（一次性）：不存在 / 已过期 / 发起用户与当前用户不一致 → None。

    Redis 路径用 GETDEL 原子读删；用户不符时令牌同样被消费（防反复试探），
    合法流程中令牌总是由发起用户本人确认，不受影响。
    """
    from app.services.redis_client import get_redis, json_getdel

    r = await get_redis()
    if r is not None:
        pending = await json_getdel(_CONFIRM_KEY_PREFIX + token)
    else:
        pending = _pending_confirms.pop(token, None)
    if not pending:
        return None
    if time.time() - pending.get("ts", 0) > _PENDING_CONFIRM_TTL:
        return None
    if str(pending["params"].get("user_id", "")) != str(user_id):
        return None
    return pending


def _already_executed(key: str) -> bool:
    now = time.time()
    for k in [k for k, t in _executed_keys.items() if now - t > 3600]:
        _executed_keys.pop(k, None)
    return key in _executed_keys


def _mark_executed(key: str) -> None:
    _executed_keys[key] = time.time()


async def _idem_already_executed(key: str) -> bool:
    """幂等键是否已执行（Redis EXISTS，兜底内存字典）。"""
    from app.services.redis_client import get_redis

    r = await get_redis()
    if r is not None:
        return bool(await r.exists(_IDEM_KEY_PREFIX + key))
    return _already_executed(key)


async def _idem_mark_executed(key: str) -> None:
    """标记幂等键已执行（Redis SET EX，兜底内存字典）。仅在业务成功后调用。"""
    from app.services.redis_client import get_redis

    r = await get_redis()
    if r is not None:
        await r.set(_IDEM_KEY_PREFIX + key, 1, ex=_IDEM_WINDOW_TTL)
        return
    _mark_executed(key)


# ─────────────────────────────────────────────
# L1 工具
# ─────────────────────────────────────────────
@tool
async def search_products(
    keyword: Optional[str] = None,
    cat_l1: Optional[str] = None,
    city: Optional[str] = None,
    price_min: Optional[float] = None,
    price_max: Optional[float] = None,
    condition: Optional[str] = None,
    order_by: Optional[str] = "default",
    limit: int = 8,
    tenant_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 检索在售商品。用户想找/买商品、想了解有什么在售时调用。
    参数：keyword 搜索词、cat_l1 一级分类、city 城市、price_min/price_max 价格区间、
    condition 成色（如 全新/95新/九成新）、order_by（default/price_asc/price_desc/hot）、limit 条数。
    返回 JSON：{products: 商品列表, search_note: 放宽说明}。
    search_note 非空表示严格条件没搜到、系统自动放宽了某些条件（如成色/价格/关键词），
    必须向用户如实说明放宽情况，不可声称结果完全满足原条件。
    """
    async with async_session() as db:
        try:
            results = await product_search.search_products(
                db=db, keyword=keyword, cat_l1=cat_l1, city=city,
                price_min=price_min, price_max=price_max,
                condition=condition, order_by=order_by, limit=limit,
                tenant_id=int(tenant_id) if str(tenant_id or "").strip().isdigit() else None,
            )
            return json.dumps(
                {
                    "products": [p.model_dump() for p in results["products"]],
                    "search_note": results.get("search_note") or "",
                },
                ensure_ascii=False,
            )
        except Exception as e:
            logger.error("search_products error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def recommend_products(
    cat_l1: Optional[str] = None,
    limit: int = 8,
    user_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 个性化商品推荐（平台六路召回：兴趣/热门/新品/向量/协同/同城，带推荐理由）。
    用户想买某类东西但没明确目标、说"随便看看/有什么推荐"时，与 search_products 同时调用：
    search 给精确匹配，本工具给"为你推荐"。reason 是推荐理由，必须如实转述给用户。
    参数：cat_l1 一级分类（可空）、limit 条数（默认 8，最大 20）。
    返回 JSON：{products: [{product_id, title, price, cover, condition, city, reason}], source: "recommend"}。
    调用失败返回 error，此时改用 search_products 即可，不要向用户编造推荐结果。
    """
    uid = str(user_id or "").strip()
    if not uid.isdigit():
        return json.dumps(
            {"error": "用户未登录，个性化推荐不可用；请使用商品搜索"}, ensure_ascii=False
        )
    token = (settings.internal_api_token or "").strip()
    if not token:
        return json.dumps(
            {"error": "服务间令牌未配置（INTERNAL_API_TOKEN），推荐服务不可用"}, ensure_ascii=False
        )
    page_size = max(1, min(int(limit or 8), 20))
    url = f"{(settings.java_base_url or '').rstrip('/')}/internal/recommend/for-user"
    params: Dict[str, Any] = {"userId": uid, "pageSize": page_size}
    if cat_l1:
        params["catL1"] = cat_l1
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(settings.java_internal_timeout)) as client:
            resp = await client.get(url, params=params, headers={"X-Internal-Token": token})
    except httpx.HTTPError as e:
        # 日志保留细节便于排障，但不把底层异常原文抛给用户
        logger.error("调用 Java 内部推荐接口失败: %s", e)
        return json.dumps({"error": "推荐服务暂时不可用，请稍后再试"}, ensure_ascii=False)
    try:
        payload = resp.json()
    except ValueError:
        payload = {}
    if resp.status_code != 200 or payload.get("code") != 200:
        msg = payload.get("msg") or f"推荐服务返回 HTTP {resp.status_code}"
        return json.dumps({"error": msg}, ensure_ascii=False)
    data = payload.get("data") or {}
    # Java TableDataInfo 的真实 JSON 形状是 data.records（Jackson 全局 Long→String、
    # 无 naming strategy）；保留 rows 回退只为容忍未来字段改名
    rows = data.get("records") or data.get("rows") or []
    products = [
        {
            # Java 全局 Long→String 序列化使 productId 实际是字符串 "1"，
            # 统一转 int 与 search_products 的 product_id 保持一致（SKILL 要求按 product_id 去重融合）
            "product_id": int(r["productId"]) if r.get("productId") is not None and str(r["productId"]).isdigit() else r.get("productId"),
            "title": r.get("title"),
            "price": float(r["price"]) if r.get("price") is not None else None,
            "cover": r.get("cover"),
            "condition": r.get("condition"),
            "city": r.get("city"),
            "reason": r.get("reason") or "",
        }
        for r in rows
    ]
    return json.dumps({"products": products, "source": "recommend"}, ensure_ascii=False)


@tool
async def estimate_price(
    product_id: Optional[int] = None,
    title: Optional[str] = None,
    cat_l1: Optional[str] = None,
    condition: Optional[str] = None,
    tenant_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 统计估价：基于真实成交分位数 × 成色系数（价格数字来自业务库统计，不来自模型臆造）。
    用户问"值多少钱/卖多少合适/这个价格亏不亏"，或下单前需要价格参考时调用。
    参数：product_id 商品 ID（优先，自动取商品真实类目/成色/标题估价，无需再传其他参数）；
    无商品 ID 时按 title + cat_l1 + condition 手动估价。
    返回 JSON：{sample_count, price_low, price_high, median, suggestion, explanation}。
    sample_count=0 表示无成交样本可参考，必须如实说明，不可编造价格数字。
    """
    from app.services.valuation import (
        L2_MIN_SAMPLES, compute_valuation, fetch_category_rows,
        fetch_onsale_prices, fetch_transactions, split_l2_samples,
    )
    from app.services.product_recognition import classify_category
    from app.services.model_resolver import resolve_default_model, build_chat_llm

    try:
        async with async_session() as db:
            from sqlalchemy import text as _text
            tenant = int(tenant_id) if str(tenant_id or "").strip().isdigit() else 0
            pid_price = None
            pid_title, pid_cat_l1, pid_cat_l2, pid_condition = None, None, None, None
            if product_id:
                # 商品行查询带租户过滤：跨租户商品对用户不可见 = 不存在
                row = (await db.execute(_text(
                    "SELECT title, price, cat_l1, cat_l2, `condition` FROM product "
                    "WHERE product_id = :pid AND `status` = 'onsale'"
                    + (" AND tenant_id = :tid" if tenant > 0 else ""),
                ), {"pid": product_id, **({"tid": tenant} if tenant > 0 else {})})).fetchone()
                if not row:
                    return json.dumps({"error": f"商品 {product_id} 不存在或已下架"})
                pid_title, pid_price, pid_cat_l1, pid_cat_l2, pid_condition = row

            # 商品 ID 命中时以库内真实值优先（模型传参只作无商品时的手动兜底）
            cat_l1_code = pid_cat_l1 or cat_l1
            cond = pid_condition or condition
            title_for_classify = pid_title or title

            # 类目缺失时 LLM 按标题分类兜底（与 /ai/assist/product-valuation 同口径）
            if not cat_l1_code:
                if not title_for_classify:
                    return json.dumps({"error": "请提供商品 ID 或标题/类目，才能估价"})
                llm_model = await resolve_default_model(db, "LLM")
                if not llm_model:
                    return json.dumps({"error": "缺少类目信息，无法估价；请先补充商品类目"})
                parsed = await classify_category(
                    build_chat_llm(llm_model, temperature=0.2),
                    title_for_classify, None, await fetch_category_rows(db),
                )
                cat_l1_code = parsed.get("catL1Code")
                if not cat_l1_code:
                    return json.dumps({"error": "未能识别商品类目，无法估价；请先补充类目"})

            if tenant > 0:
                rows = await fetch_transactions(db, tenant, cat_l1_code)
                l2_samples, _l1_rest = split_l2_samples(rows, pid_cat_l2)
                tx_prices = l2_samples if len(l2_samples) >= L2_MIN_SAMPLES else [
                    float(r["price"]) for r in rows
                ]
                ask_prices = await fetch_onsale_prices(db, tenant, cat_l1_code)
            else:
                tx_prices, ask_prices = [], []

            result = compute_valuation(tx_prices, ask_prices, cond)
            # 商品挂价与建议价对比：偏离超过 DEVIATION_RATIO 给出风险提示
            if pid_price and result.get("suggestion"):
                try:
                    ratio = float(pid_price) / float(result["suggestion"])
                    if ratio >= DEVIATION_RATIO or ratio <= 1 / DEVIATION_RATIO:
                        result["deviation_warning"] = (
                            f"价格 ¥{float(pid_price):.0f} 与同品类建议价 "
                            f"¥{float(result['suggestion']):.0f} 偏离超过 40%"
                        )
                except (TypeError, ValueError):
                    pass
            return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        logger.error("estimate_price error: %s", e)
        return json.dumps({"error": str(e)})


@tool
async def get_product_detail(
    product_id: int,
    tenant_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 查询单个商品的详细信息。用户追问某商品详情（描述、成色、卖家等）时调用。
    参数：product_id 商品 ID。返回商品详情 JSON，包含 description 描述。
    """
    async with async_session() as db:
        try:
            from sqlalchemy import text
            sql_text = """
                SELECT p.product_id, p.title, p.cover, p.price, p.original_price,
                       p.`condition`, p.city, p.cat_l1, p.`description`, p.view_count,
                       u.nick_name as seller_nick_name, u.user_id as seller_id
                FROM `product` p LEFT JOIN `sys_user` u ON p.seller_id = u.user_id
                WHERE p.product_id = :pid AND p.`status` = 'onsale'
            """
            params = {"pid": product_id}
            tid = int(tenant_id) if str(tenant_id or "").strip().isdigit() else 0
            if tid > 0:
                sql_text += " AND p.tenant_id = :tid"
                params["tid"] = tid
            sql = text(sql_text)
            row = (await db.execute(sql, params)).fetchone()
            if not row:
                return json.dumps({"error": f"商品 {product_id} 不存在或已下架"})
            keys = ["product_id", "title", "cover", "price", "original_price",
                    "condition", "city", "cat_l1", "description", "view_count",
                    "seller_nick_name", "seller_id"]
            data = {k: (float(row[i]) if k in ("price", "original_price") and row[i] is not None else row[i])
                    for i, k in enumerate(keys)}
            return json.dumps(data, ensure_ascii=False)
        except Exception as e:
            logger.error("get_product_detail error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def get_default_address(user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L1] 查询用户默认收货地址。下单前需要确认收货地址时调用，无需向用户索要地址。
    user_id 由系统自动注入。返回默认地址 JSON（无默认则返回最近一条）。
    """
    async with async_session() as db:
        try:
            from sqlalchemy import text
            sql = text(
                """
                SELECT address_id, receiver, mobile, province, city, district, detail, is_default
                FROM user_address WHERE user_id = :uid
                ORDER BY is_default DESC, update_time DESC, address_id DESC LIMIT 1
                """
            )
            row = (await db.execute(sql, {"uid": int(user_id) if str(user_id).isdigit() else -1})).fetchone()
            if not row:
                return json.dumps({"error": "未找到收货地址，请先添加地址"})
            keys = ["address_id", "receiver", "mobile", "province", "city", "district", "detail", "is_default"]
            return json.dumps(_row_to_dict(keys, row), ensure_ascii=False)
        except Exception as e:
            logger.error("get_default_address error: %s", e)
            return json.dumps({"error": str(e)})


def _serialize_value(v):
    """将数据库返回值转换为 JSON 可序列化类型。"""
    if v is None:
        return None
    if isinstance(v, (int, str, bool)):
        return v
    if isinstance(v, float):
        return v
    # Decimal → float
    if type(v).__name__ == "Decimal":
        return float(v)
    # datetime / date → isoformat
    if hasattr(v, "isoformat"):
        return v.isoformat()
    # bytes → hex
    if isinstance(v, (bytes, bytearray)):
        return v.hex()
    return str(v)


def _row_to_dict(keys, row):
    """将数据库行 + 列名转为 JSON 可序列化的 dict。"""
    return {k: _serialize_value(v) for k, v in zip(keys, row)}


@tool
async def query_order(user_id: Annotated[Optional[str], InjectedToolArg] = None, order_no: Optional[str] = None, status: Optional[str] = None) -> str:
    """[L1] 查询用户的订单列表。用户问"我的订单/我买的东西/订单状态"时调用。
    参数：order_no 订单号（可空，空则查列表）、status 订单状态（unpaid/unshipped/shipped/done 等，可空）。
    user_id 由系统自动注入。返回订单列表 JSON。
    """
    async with async_session() as db:
        try:
            from sqlalchemy import text
            where = "buyer_id = :uid"
            params: Dict[str, Any] = {"uid": int(user_id) if str(user_id).isdigit() else -1}
            if order_no:
                where += " AND order_no = :order_no"
                params["order_no"] = order_no
            if status:
                where += " AND status = :status"
                params["status"] = status
            sql = text(
                f"""
                SELECT order_id, order_no, product_id, product_snapshot, sku_price,
                       freight, total_amount, status, create_time, paid_at
                FROM trade_order WHERE {where}
                ORDER BY create_time DESC LIMIT 20
                """
            )
            rows = (await db.execute(sql, params)).fetchall()
            if not rows:
                return json.dumps({"error": "未查询到相关订单"})
            keys = ["order_id", "order_no", "product_id", "product_snapshot", "sku_price",
                    "freight", "total_amount", "status", "create_time", "paid_at"]
            return json.dumps([_row_to_dict(keys, r) for r in rows], ensure_ascii=False)
        except Exception as e:
            logger.error("query_order error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def track_logistics(order_no: str, user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L1] 查询订单物流信息。用户问"物流/快递到哪了/什么时候到"时调用。
    参数：order_no 订单号。根据订单状态返回物流轨迹（项目暂无独立物流表，按状态映射）。
    仅能查询本人订单。user_id 由系统自动注入。
    """
    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            sql = text(
                """
                SELECT order_id, order_no, status, shipped_at, done_at, expire_at
                FROM trade_order WHERE order_no = :on AND buyer_id = :u LIMIT 1
                """
            )
            row = (await db.execute(sql, {"on": order_no, "u": uid})).fetchone()
            if not row:
                return json.dumps({"error": f"订单 {order_no} 不存在或不属于当前用户"})
            status = row[2]
            status_text = {
                "unpaid": "订单待支付，暂无物流信息",
                "unshipped": "卖家已接单，待发货",
                "shipped": f"运输中（发货时间 {row[3]}），请耐心等待",
                "done": f"已签收（完成时间 {row[4]}）",
                "refunding": "订单售后处理中，物流暂停",
                "refunded": "订单已退款，交易关闭",
                "closed": "订单已关闭",
            }.get(status, f"订单状态：{status}")
            return json.dumps({"order_no": order_no, "status": status, "track": status_text},
                              ensure_ascii=False)
        except Exception as e:
            logger.error("track_logistics error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def search_knowledge(query: str) -> str:
    """[L1] 搜索知识库获取平台规则、政策、操作指南等信息。
    当用户问题涉及平台规则、退换货政策、操作流程等知识时调用。
    参数：query 搜索关键词。返回相关知识库文档片段。
    """
    try:
        from app.database import async_session
        from app.models.ai_knowledge import AiKnowledge
        from app.models.ai_doc_slice import AiDocSlice
        from app.services.rag_service import search as rag_search
        from sqlalchemy import select

        async with async_session() as db:
            kb = (
                await db.execute(
                    select(AiKnowledge)
                    .where(AiKnowledge.del_flag == 0)
                    .order_by(AiKnowledge.knowledge_id.desc())
                    .limit(1)
                )
            ).scalars().first()
            if not kb:
                return json.dumps({"message": "未找到知识库"})

            hits = await rag_search(
                db,
                int(kb.knowledge_id),
                query,
                top_k=kb.top_k or 3,
                score_threshold=kb.score if kb.score is not None else 0.5,
            )
            if not hits:
                return json.dumps({"message": "未找到相关知识"})
            ids = [h["slice_id"] for h in hits]
            result = await db.execute(
                select(AiDocSlice).where(AiDocSlice.slice_id.in_(ids))
            )
            slices = list(result.scalars().all())
            content = "\n\n".join(s.content or "" for s in slices if s.content)
            from app.services.agent.nodes import sanitize_rag_context

            return json.dumps(
                {"knowledge": sanitize_rag_context(content)[:3000]},
                ensure_ascii=False,
            )
    except Exception as e:
        logger.error("search_knowledge error: %s", e)
        return json.dumps({"error": str(e)})


def _get_rag_service():
    """延迟导入 RAG 服务，测试可 monkeypatch 本函数。"""
    from app.services.rag_showcase.service import get_service

    return get_service()


@tool
async def search_platform_rules(query: str) -> str:
    """[L1] 查询平台规则、售后政策、退款时效、运费、禁售清单、纠纷流程。
    用户问规则依据、政策条款、时效数字或“平台怎么规定”时优先调用。
    参数：query 用户问题的自然语言描述。
    返回 JSON：{answer, answerable, citations, refusal_reason, degraded}；
    citations 最多保留 3 条，转述时保留 [n] 引用；answerable=false 时如实告知没有依据，
    不得用平台外知识补全。
    """
    try:
        result = await _get_rag_service().query(query)
        citations = (result.get("citations") or [])[:3]
        return json.dumps(
            {
                "answer": result.get("answer") or "",
                "answerable": bool(result.get("answerable")),
                "citations": citations,
                "refusal_reason": result.get("refusal_reason"),
                "degraded": result.get("degraded"),
            },
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("search_platform_rules error: %s", e)
        return json.dumps(
            {"error": "平台规则查询暂时不可用", "answerable": False},
            ensure_ascii=False,
        )


# ─────────────────────────────────────────────
# L2 工具（需确认）
# ─────────────────────────────────────────────
@tool
async def add_to_favorite(product_id: int, confirm_token: Optional[str] = None, user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L2] 收藏商品（平台无购物车，收藏替代加购）。用户说"收藏/加收藏"时调用。
    首次调用返回确认请求，用户确认后携带 confirm_token 再次调用才真正收藏。
    参数：product_id 商品 ID、confirm_token 确认令牌（用户确认后由模型回传）。
    user_id 由系统自动注入，无需向用户索要。
    """
    action = f"收藏商品 {product_id}"
    if not confirm_token:
        token = _new_confirm_token("add_to_favorite", {"user_id": user_id, "product_id": product_id})
        await _save_pending_confirm(token, "add_to_favorite",
                                    {"user_id": user_id, "product_id": product_id})
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}？请向用户复述并等待其确认。",
        }, ensure_ascii=False)
    pending = await _pop_valid_confirm(confirm_token, user_id)
    if not pending:
        return json.dumps({"error": "确认令牌无效、已过期或与当前用户不符，请重新发起操作"})
    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            # uk_user_product 唯一约束，重复收藏自动忽略
            await db.execute(
                text("INSERT IGNORE INTO product_favorite (user_id, product_id, fav_time) VALUES (:u, :p, NOW())"),
                {"u": uid, "p": pending["params"]["product_id"]},
            )
            await db.commit()
            return json.dumps({"success": True, "message": f"已收藏商品 {product_id}"}, ensure_ascii=False)
        except Exception as e:
            logger.error("add_to_favorite error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def list_favorites(user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L1] 查询用户的收藏商品列表。用户说"看看我的收藏/我收藏了什么"时调用。
    user_id 由系统自动注入。返回收藏商品列表 JSON（含商品卡片字段，可直接展示）。
    """
    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            sql = text(
                """
                SELECT f.product_id, p.title, p.cover, p.price, p.original_price,
                       p.`condition`, p.city, u.nick_name as seller_nick_name
                FROM product_favorite f
                JOIN `product` p ON f.product_id = p.product_id
                LEFT JOIN `sys_user` u ON p.seller_id = u.user_id
                WHERE f.user_id = :uid
                ORDER BY f.fav_time DESC, f.product_id DESC LIMIT 20
                """
            )
            rows = (await db.execute(sql, {"uid": uid})).fetchall()
            if not rows:
                return json.dumps({"products": [], "message": "收藏夹是空的"}, ensure_ascii=False)
            products = [
                {
                    "product_id": r[0], "title": r[1], "cover": r[2],
                    "price": float(r[3]) if r[3] is not None else None,
                    "original_price": float(r[4]) if r[4] is not None else None,
                    "condition": r[5], "city": r[6], "seller_nick_name": r[7],
                }
                for r in rows
            ]
            return json.dumps(
                {"products": products, "message": f"共 {len(products)} 件收藏"},
                ensure_ascii=False,
            )
        except Exception as e:
            logger.error("list_favorites error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def remove_favorite(product_id: int, confirm_token: Optional[str] = None, user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L2] 取消收藏商品。用户说"取消收藏/不要收藏这个了"时调用。
    首次调用返回确认请求，用户确认后携带 confirm_token 再次调用才真正取消。
    参数：product_id 商品 ID、confirm_token 确认令牌（用户确认后由模型回传）。
    user_id 由系统自动注入。
    """
    action = f"取消收藏商品 {product_id}"
    if not confirm_token:
        token = _new_confirm_token("remove_favorite", {"user_id": user_id, "product_id": product_id})
        await _save_pending_confirm(token, "remove_favorite",
                                    {"user_id": user_id, "product_id": product_id})
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}？请向用户复述并等待其确认。",
        }, ensure_ascii=False)
    pending = await _pop_valid_confirm(confirm_token, user_id)
    if not pending:
        return json.dumps({"error": "确认令牌无效、已过期或与当前用户不符，请重新发起操作"})
    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            result = await db.execute(
                text("DELETE FROM product_favorite WHERE user_id = :u AND product_id = :p"),
                {"u": uid, "p": pending["params"]["product_id"]},
            )
            await db.commit()
            if result.rowcount == 0:
                return json.dumps({"message": "该商品本来就不在你的收藏中"}, ensure_ascii=False)
            return json.dumps({"success": True, "message": f"已取消收藏商品 {product_id}"}, ensure_ascii=False)
        except Exception as e:
            logger.error("remove_favorite error: %s", e)
            return json.dumps({"error": str(e)})


# ─────────────────────────────────────────────
# L3 工具（确认 + 幂等键）
# ─────────────────────────────────────────────
@tool
async def update_address(receiver: str, mobile: str, province: str, city: str, district: Optional[str] = "",
                         detail: str = "", user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L1] 更新/新增用户默认收货地址。用户反馈"地址有误/地址错了/改成新地址"时调用，先更新地址再继续下单。
    参数：receiver 收货人、mobile 手机号、province 省份、city 城市、district 区县、detail 详细地址。
    更新后该地址成为默认地址，后续下单自动使用。
    """
    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            if uid <= 0:
                return json.dumps({"error": "用户未登录，无法更新地址"})
            # 查找该用户已有地址：存在则更新为默认，不存在则插入
            row = (await db.execute(
                text("SELECT address_id FROM user_address WHERE user_id = :u ORDER BY is_default DESC, address_id DESC LIMIT 1"),
                {"u": uid})).fetchone()
            if row:
                await db.execute(
                    text("UPDATE user_address SET receiver=:r, mobile=:m, province=:p, city=:c, "
                         "district=:d, detail=:dt, is_default=1 WHERE address_id=:aid"),
                    {"r": receiver, "m": mobile, "p": province, "c": city, "d": district or "",
                     "dt": detail, "aid": row[0]},
                )
            else:
                await db.execute(
                    text("INSERT INTO user_address (user_id, receiver, mobile, province, city, district, detail, is_default) "
                         "VALUES (:u, :r, :m, :p, :c, :d, :dt, 1)"),
                    {"u": uid, "r": receiver, "m": mobile, "p": province, "c": city,
                     "d": district or "", "dt": detail},
                )
            await db.commit()
            return json.dumps(
                {"success": True, "message": f"收货地址已更新：{province}{city}{district or ''}{detail}（{receiver} {mobile}）"},
                ensure_ascii=False,
            )
        except Exception as e:
            logger.error("update_address error: %s", e)
            return json.dumps({"error": str(e)})


async def _pre_order_risk_check(db, product_id: int, buyer_id: int, seller_id: Optional[int]) -> List[str]:
    """下单确认前的只读风险检查（L1，永不抛异常、永不阻塞下单）。

    三项独立查询，任何一项失败记 warning 并跳过（与 confirm_order 估价提示的降级模式一致）。
    返回面向用户的风险文案列表，空列表 = 无风险。
    """
    risks: List[str] = []
    from sqlalchemy import text

    # ① 买家在该商品上的进行中议价
    try:
        row = (await db.execute(
            text("SELECT current_price FROM trade_bargain "
                 "WHERE product_id = :pid AND buyer_id = :bid AND status = 'pending' "
                 "ORDER BY update_time DESC LIMIT 1"),
            {"pid": product_id, "bid": buyer_id},
        )).fetchone()
        if row and row[0] is not None:
            risks.append(f"你有一笔 ¥{float(row[0]):.0f} 的议价进行中（卖家尚未回复），可等待议价结果再下单")
    except Exception as e:
        logger.warning("risk_check bargain skipped: %s", e)

    # ② 卖家近 N 天未结纠纷数
    if seller_id:
        try:
            row = (await db.execute(
                text("SELECT COUNT(*) FROM trade_dispute WHERE seller_id = :sid "
                     "AND status IN ('pending', 'in_progress') "
                     "AND create_time >= DATE_SUB(NOW(), INTERVAL :days DAY)"),
                {"sid": int(seller_id), "days": RISK_DISPUTE_WINDOW_DAYS},
            )).fetchone()
            if row and row[0]:
                risks.append(f"该卖家近 {RISK_DISPUTE_WINDOW_DAYS} 天有 {row[0]} 笔纠纷未结，请留意")
        except Exception as e:
            logger.warning("risk_check dispute skipped: %s", e)

    # ③ 卖家活跃度（login_date 为 NULL 视为未知，跳过不提示）
    if seller_id:
        try:
            row = (await db.execute(
                text("SELECT login_date FROM sys_user WHERE user_id = :sid"),
                {"sid": int(seller_id)},
            )).fetchone()
            if row and row[0] is not None:
                days = (datetime.now() - row[0]).days
                if days >= RISK_SELLER_INACTIVE_DAYS:
                    risks.append(f"卖家已 {days} 天未登录，回复可能较慢")
        except Exception as e:
            logger.warning("risk_check login skipped: %s", e)

    return risks


@tool
async def confirm_order(product_id: int, price: Optional[float] = None, address_id: Optional[int] = None,
                        confirm_token: Optional[str] = None,
                        user_id: Annotated[Optional[str], InjectedToolArg] = None,
                        tenant_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L3] 确认下单（模拟下单，MVP 阶段生成占位订单，后续接入 Java createOrder）。
    资金操作：必须确认 + 幂等键防重复。首次调用返回确认请求，用户确认后携带 confirm_token 再次调用。
    参数：product_id 商品 ID、price 成交价（可空，空则由系统取商品实时售价）、
    address_id 收货地址 ID（可空，空则用默认地址）、confirm_token 确认令牌。
    user_id / tenant_id 由系统自动注入，无需向用户索要；下单前若已知用户要买某商品，可先调用 search_products 找到商品再下单。
    """
    # price 可选：二次调用（confirm_token）路径若未传，兜底查询商品实时售价。
    # 首次确认分支的兜底并入下方单会话（商品行一次取齐），避免多开一次会话。
    if price is None and confirm_token:
        try:
            async with async_session() as db:
                from sqlalchemy import text
                # 二次确认分支的价格兜底同样带租户过滤：跨租户商品 = 暂无价格
                tid = int(tenant_id) if str(tenant_id or "").strip().isdigit() else 0
                row = (await db.execute(
                    text("SELECT price FROM product WHERE product_id = :pid AND status = 'onsale'"
                         + (" AND tenant_id = :tid" if tid > 0 else "")),
                    {"pid": product_id, **({"tid": tid} if tid > 0 else {})})).fetchone()
                if row:
                    price = float(row[0])
        except Exception as e:
            logger.error("confirm_order query price error: %s", e)
        if price is None:
            return json.dumps({"error": f"商品 {product_id} 暂无价格，无法下单"})
    if not confirm_token:
        # 首次确认分支：单会话一次取齐——商品行（价格兜底/seller_id/类目/成色）、
        # 默认地址、估价偏离、风险检查。各段独立 try/except：任何一段失败只降级自己，
        # 不影响确认流程。原实现此处开 4 个独立会话（价格/地址/估价/风险）。
        addr_text = ""
        valuation_warning = ""
        risk_text = ""
        buyer = int(user_id) if str(user_id).isdigit() else -1
        tid = int(tenant_id) if str(tenant_id or "").strip().isdigit() else 0
        try:
            async with async_session() as db:
                from sqlalchemy import text
                # ① 商品行（一次取齐）：price 为空时仅对 onsale 行兜底取价（保持原
                #    价格查询语义）；模型已传价仍取行，供估价/风险检查使用。
                prow = (await db.execute(
                    text("SELECT price, seller_id, cat_l1, cat_l2, `condition`, `status` FROM product "
                         "WHERE product_id = :pid" + (" AND tenant_id = :tid" if tid > 0 else "")),
                    {"pid": product_id, **({"tid": tid} if tid > 0 else {})},
                )).fetchone()
                seller_id = cat_l1 = cat_l2 = cond = None
                if prow:
                    db_price = float(prow[0]) if prow[0] is not None else None
                    seller_id, cat_l1, cat_l2, cond = prow[1], prow[2], prow[3], prow[4]
                    if price is None and prow[5] == "onsale":
                        price = db_price
                # ② 默认地址（独立降级）：拼进确认消息，让用户核对收货地址是否正确
                try:
                    if buyer > 0:
                        addr = (await db.execute(
                            text("SELECT receiver, mobile, province, city, district, detail FROM user_address "
                                 "WHERE user_id = :u ORDER BY is_default DESC, address_id DESC LIMIT 1"),
                            {"u": buyer})).fetchone()
                        if addr:
                            addr_text = f"；收货地址：{addr[2]}{addr[3]}{addr[4] or ''}{addr[5]}（{addr[0]} {addr[1]}）"
                        else:
                            addr_text = "；未找到收货地址"
                except Exception as e:
                    logger.error("confirm_order query address error: %s", e)
                # ③ 估价偏离（独立降级，只读统计，不阻塞下单）：偏离超 40% 提示
                try:
                    if tid > 0 and prow and cat_l1 and price and float(price) > 0:
                        from app.services.valuation import (
                            L2_MIN_SAMPLES, compute_valuation, fetch_onsale_prices,
                            fetch_transactions, split_l2_samples,
                        )
                        rows = await fetch_transactions(db, tid, cat_l1)
                        l2_samples, _l1_rest = split_l2_samples(rows, cat_l2)
                        tx_prices = l2_samples if len(l2_samples) >= L2_MIN_SAMPLES else [
                            float(r["price"]) for r in rows
                        ]
                        ask_prices = await fetch_onsale_prices(db, tid, cat_l1)
                        val = compute_valuation(tx_prices, ask_prices, cond)
                        if val.get("suggestion"):
                            ratio = float(price) / float(val["suggestion"])
                            if ratio >= DEVIATION_RATIO:
                                valuation_warning = (f"；⚠ 该价格比同品类建议价 ¥{float(val['suggestion']):.0f} 高出"
                                                     f"{round((ratio - 1) * 100)}%，请确认是否愿意以此价购买")
                            elif ratio <= 1 / DEVIATION_RATIO:
                                valuation_warning = (f"；提示：该价格比同品类建议价 ¥{float(val['suggestion']):.0f} 低"
                                                     f"{round((1 - ratio) * 100)}%，疑似明显低价，请留意商品成色与卖家信誉")
                except Exception as e:
                    logger.warning("confirm_order valuation hint skipped: %s", e)
                # ④ 风险检查（独立降级，只读不阻塞下单，复用同一会话）
                try:
                    if buyer > 0 and prow:
                        risks = await _pre_order_risk_check(db, product_id, buyer, seller_id)
                        if risks:
                            risk_text = "；" + "；".join(risks)
                except Exception as e:
                    logger.warning("confirm_order risk hint skipped: %s", e)
        except Exception as e:
            logger.warning("confirm_order confirm-branch db error: %s", e)
        if price is None:
            return json.dumps({"error": f"商品 {product_id} 暂无价格，无法下单"})
        action = f"下单购买商品 {product_id}，金额 ¥{price}"
        token = _new_confirm_token("confirm_order", {"user_id": user_id, "product_id": product_id, "price": price})
        await _save_pending_confirm(token, "confirm_order",
                                    {"user_id": user_id, "product_id": product_id, "price": price,
                                     "address_id": address_id})
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}{addr_text}{risk_text}{valuation_warning}？"
                       f"地址无误请回复确认；地址有误请直接发送新地址。",
        }, ensure_ascii=False)
    pending = await _pop_valid_confirm(confirm_token, user_id)
    if not pending:
        return json.dumps({"error": "确认令牌无效、已过期或与当前用户不符，请重新发起操作"})

    uid = int(user_id) if str(user_id).isdigit() else -1
    pid = pending["params"]["product_id"]

    key = _idempotency_key(user_id, "confirm_order", {"product_id": pid})
    if await _idem_already_executed(key):
        return json.dumps({"error": "操作已处理（幂等键命中），请勿重复下单"})

    # 收货地址前置校验（只读）：express 商品下单必须携带地址（Java 侧校验链要求），
    # 这里顺带解析出地址 ID 透传给 Java——此前只作 UX 守卫不传值，Agent 买 express
    # 商品会被 Java 以"请选择收货地址"拒绝（全量回归）。
    # self_pickup 商品 Java 侧自动跳过地址校验，传默认地址也无害（归属校验兜底）。
    resolved_address_id = pending["params"].get("address_id")
    async with async_session() as db:
        try:
            from sqlalchemy import text
            addr = (await db.execute(
                text("SELECT address_id FROM user_address WHERE user_id = :u ORDER BY is_default DESC LIMIT 1"),
                {"u": uid})).fetchone()
        except Exception as e:
            logger.error("confirm_order query address error: %s", e)
            addr = None
    if not addr and not resolved_address_id:
        return json.dumps({"error": "未找到收货地址，请先添加地址后再下单"})
    if addr and not resolved_address_id:
        resolved_address_id = addr[0]

    # 下单改为调用 Java 内部端点：租户/运费/商品快照/订单号全部由 Java 侧负责。
    # 修复前此处是直接 INSERT trade_order（列清单不含 tenant_id），会落到 DB DEFAULT 1，
    # 在 tenant.enabled=true 下造成跨租户混入；因此这里绝不保留任何直写回退。
    try:
        data = await _java_internal_create_order(uid, pid, address_id=resolved_address_id)
    except JavaInternalUnavailable as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)

    await _idem_mark_executed(key)
    amount = data.get("totalAmount")
    try:
        amount_text = f"¥{float(amount):.2f}" if amount is not None else "以订单页为准"
    except (TypeError, ValueError):
        amount_text = "以订单页为准"
    return json.dumps(
        {"success": True, "order_no": data.get("orderNo"), "order_id": data.get("orderId"),
         "status": data.get("status") or "unpaid",
         "message": f"下单成功，订单号 {data.get('orderNo')}，待支付 {amount_text}"
                    f"（价格与运费以系统实时计算为准）"},
        ensure_ascii=False,
    )


@tool
async def pay_order(order_no: str, confirm_token: Optional[str] = None, user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L3] 模拟支付订单（已从 Agent 工具注册表移除，保留函数供测试与未来接入真实支付回调）。
    HITL 决策（2026-10-10）：支付由用户本人在订单页完成，agent 不执行任何扣款动作。
    资金操作：必须确认 + 幂等键防重复扣款。
    参数：order_no 订单号、confirm_token 确认令牌。user_id 由系统自动注入。
    """
    action = f"支付订单 {order_no}"
    if not confirm_token:
        token = _new_confirm_token("pay_order", {"user_id": user_id, "order_no": order_no})
        await _save_pending_confirm(token, "pay_order",
                                    {"user_id": user_id, "order_no": order_no})
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}？请向用户复述并等待其确认。",
        }, ensure_ascii=False)
    pending = await _pop_valid_confirm(confirm_token, user_id)
    if not pending:
        return json.dumps({"error": "确认令牌无效、已过期或与当前用户不符，请重新发起操作"})

    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            on = pending["params"]["order_no"]
            key = _idempotency_key(user_id, "pay_order", {"order_no": on})
            if await _idem_already_executed(key):
                return json.dumps({"error": "支付已处理（幂等键命中），请勿重复支付"})
            # 归属校验：只能支付本人名下未支付订单
            result = await db.execute(
                text("UPDATE trade_order SET status = 'unshipped', paid_at = NOW() "
                     "WHERE order_no = :on AND status = 'unpaid' AND buyer_id = :u"),
                {"on": on, "u": uid},
            )
            await db.commit()
            if result.rowcount == 0:
                return json.dumps({"error": f"订单 {on} 不存在、不属于当前用户或不可支付"})
            await _idem_mark_executed(key)
            return json.dumps({"success": True, "order_no": on,
                               "message": "支付成功（模拟）"}, ensure_ascii=False)
        except Exception as e:
            logger.error("pay_order error: %s", e)
            return json.dumps({"error": str(e)})


@tool
async def create_dispute(order_no: str, reason: str, confirm_token: Optional[str] = None,
                         user_id: Annotated[Optional[str], InjectedToolArg] = None) -> str:
    """[L3] 创建售后纠纷单（退款/退货/未收到货等），提交人工/仲裁处理。
    资金相关：必须确认 + 幂等键。
    参数：order_no 订单号、reason 纠纷原因（退款/退货/未收到货/假货等）、confirm_token 确认令牌。
    user_id 由系统自动注入。
    """
    action = f"为订单 {order_no} 发起售后纠纷（原因：{reason}）"
    if not confirm_token:
        token = _new_confirm_token("create_dispute", {"user_id": user_id, "order_no": order_no, "reason": reason})
        await _save_pending_confirm(token, "create_dispute",
                                    {"user_id": user_id, "order_no": order_no, "reason": reason})
        return json.dumps({
            "need_confirm": True,
            "confirm_token": token,
            "message": f"确认要执行：{action}？请向用户复述并等待其确认。",
        }, ensure_ascii=False)
    pending = await _pop_valid_confirm(confirm_token, user_id)
    if not pending:
        return json.dumps({"error": "确认令牌无效、已过期或与当前用户不符，请重新发起操作"})

    async with async_session() as db:
        try:
            from sqlalchemy import text
            uid = int(user_id) if str(user_id).isdigit() else -1
            on = pending["params"]["order_no"]
            reason = pending["params"]["reason"]
            key = _idempotency_key(user_id, "create_dispute", {"order_no": on, "reason": reason})
            if await _idem_already_executed(key):
                return json.dumps({"error": "该纠纷已提交（幂等键命中），请勿重复提交"})
            # 归属校验：只能为本人订单发起纠纷
            row = (await db.execute(
                text("SELECT order_id, seller_id, total_amount FROM trade_order "
                     "WHERE order_no = :on AND buyer_id = :u"),
                {"on": on, "u": uid})).fetchone()
            if not row:
                return json.dumps({"error": f"订单 {on} 不存在、不属于当前用户或无权操作"})
            type_map = {"退款": "refund", "退货": "return", "未收到货": "no_received", "假货": "fake"}
            dispute_type = type_map.get(reason, "other")
            await db.execute(
                text(
                    """
                    INSERT INTO trade_dispute (order_id, buyer_id, seller_id, type, amount,
                        status, priority, create_time)
                    VALUES (:oid, :u, :s, :t, :amt, 'pending', 'normal', NOW())
                    """
                ),
                {"oid": row[0], "u": uid, "s": row[1], "t": dispute_type, "amt": row[2]},
            )
            await db.commit()
            await _idem_mark_executed(key)
            return json.dumps({"success": True, "order_no": on,
                               "message": "售后纠纷已提交，平台客服将尽快介入处理"}, ensure_ascii=False)
        except Exception as e:
            logger.error("create_dispute error: %s", e)
            return json.dumps({"error": str(e)})


# ─────────────────────────────────────────────
# 工具注册表：域 → (工具列表)
# ─────────────────────────────────────────────
PRE_SALE_TOOLS = [search_products, recommend_products, get_product_detail, estimate_price, search_knowledge, search_platform_rules]
# in_sale 域也保留 search_products / get_product_detail：
# 用户在购买阶段常需再确认商品（价格/成色/卖家），下单前可先查商品再 confirm_order
# HITL 决策（2026-10-10 用户要求）：支付类敏感操作由用户本人执行，agent 不再持有
# pay_order 工具——下单成功（unpaid）后由导购话术引导用户到订单页自行支付。
IN_SALE_TOOLS = [
    search_products, get_product_detail, estimate_price,
    add_to_favorite, list_favorites, remove_favorite,
    get_default_address, update_address, confirm_order,
]
AFTER_SALE_TOOLS = [query_order, track_logistics, create_dispute, search_platform_rules]
# 社区管家域：社区数据工具（租户隔离，见 community_tools）+ 商品检索
# （用户问"最近有什么好货"时既给概况又能带出商品卡片）
from app.services.agent.community_tools import (  # noqa: E402
    get_community_overview,
    search_feeds,
    search_runner_tasks,
)

COMMUNITY_TOOLS = [get_community_overview, search_runner_tasks, search_feeds,
                   search_products, get_product_detail]

AGENT_TOOLS = {
    "pre_sale": PRE_SALE_TOOLS,
    "in_sale": IN_SALE_TOOLS,
    "after_sale": AFTER_SALE_TOOLS,
    "community": COMMUNITY_TOOLS,
}
