"""AI 估价服务：基于真实成交与在售数据的统计估价（价格数字绝不来自 LLM）。

数据源（直读业务库）：
- trade_order(status='done', 近 180 天) JOIN product 取类目 → 成交价 sku_price 样本
- product(status='onsale') 同类目挂价 → 当前市场参考

口径：
- 同 cat_l2 成交样本 < 5 时回退同 cat_l1（由路由层先取 l1 全量、Python 侧切分，避免两次查询）
- 建议区间 = p25/p75 × 成色系数；p50 × 成色系数 = 建议价
- 无任何样本时返回 sample_count=0，说明文案如实标注，不编造数字
"""

import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

logger = logging.getLogger(__name__)

# 成色系数：95新为基准 1.0
CONDITION_FACTORS = {
    "brand_new": 1.15,
    "99": 1.08,
    "95": 1.00,
    "90": 0.92,
    "80": 0.80,
    "70_lower": 0.60,
}
DEFAULT_CONDITION = "90"

# 二级类目样本不足时回退一级类目的阈值
L2_MIN_SAMPLES = 5
# 单次统计最多拉取的样本行数（防大租户全表扫描）
MAX_ROWS = 2000
# 成交样本统计窗口
LOOKBACK_DAYS = 180

_TX_SQL = text("""
    SELECT o.sku_price AS price, p.cat_l2 AS cat_l2
    FROM trade_order o
    JOIN product p ON o.product_id = p.product_id
    WHERE o.tenant_id = :tid AND o.status = 'done'
      AND o.create_time >= :since
      AND p.cat_l1 = :cat1
    LIMIT :max_rows
""")

_ASK_SQL = text("""
    SELECT price FROM product
    WHERE tenant_id = :tid AND status = 'onsale' AND cat_l1 = :cat1
    LIMIT :max_rows
""")

_CATEGORY_SQL = text(
    "SELECT code, name, parent_code, level FROM product_category_dict ORDER BY sort"
)


def _round_price(v: float) -> float:
    """≥100 取整便于阅读，<100 保留两位。"""
    return float(round(v)) if v >= 100 else round(v, 2)


def percentile(sorted_values: List[float], p: float) -> Optional[float]:
    """线性插值分位数（纯逻辑，可单测）。p∈[0,100]，空列表返回 None。"""
    if not sorted_values:
        return None
    if len(sorted_values) == 1:
        return float(sorted_values[0])
    k = (len(sorted_values) - 1) * (p / 100.0)
    f = int(k)
    c = min(f + 1, len(sorted_values) - 1)
    return float(sorted_values[f] + (sorted_values[c] - sorted_values[f]) * (k - f))


def condition_factor(condition: Optional[str]) -> float:
    return CONDITION_FACTORS.get(condition or "", CONDITION_FACTORS[DEFAULT_CONDITION])


def compute_valuation(tx_prices: List[float], ask_prices: List[float],
                      condition: Optional[str]) -> Dict:
    """由成交样本与在售挂价计算估价（纯逻辑，可单测）。

    返回 {sampleCount, priceLow, priceHigh, median, p25, p75, suggestion, explanation, source}。
    """
    factor = condition_factor(condition)
    tx = sorted(float(p) for p in tx_prices if p and float(p) > 0)
    asks = sorted(float(p) for p in ask_prices if p and float(p) > 0)

    result: Dict = {
        "sample_count": len(tx),
        "price_low": None,
        "price_high": None,
        "median": None,
        "p25": None,
        "p75": None,
        "suggestion": None,
        "explanation": "",
        "source": "transaction_stats",
    }
    if tx:
        p25 = percentile(tx, 25) or 0.0
        p50 = percentile(tx, 50) or 0.0
        p75 = percentile(tx, 75) or 0.0
        result.update({
            "price_low": _round_price(max(p25 * factor, 0.01)),
            "price_high": _round_price(max(p75 * factor, 0.01)),
            "median": _round_price(p50),
            "p25": _round_price(p25),
            "p75": _round_price(p75),
            "suggestion": _round_price(max(p50 * factor, 0.01)),
        })
        cond_note = "" if not condition or condition == "95" else f"（已按成色「{condition}」调整）"
        result["explanation"] = (
            f"基于近{LOOKBACK_DAYS}天同品类 {len(tx)} 件真实成交，"
            f"中间价位约 ¥{result['median']}{cond_note}。"
        )
        if asks:
            result["explanation"] += f"当前同类目在售 {len(asks)} 件，挂价区间 ¥{_round_price(asks[0])} ~ ¥{_round_price(asks[-1])}。"
        return result

    if asks:
        result["source"] = "onsale_reference"
        result["price_low"] = _round_price(max(asks[0] * factor, 0.01))
        result["price_high"] = _round_price(max(asks[min(len(asks), 10) - 1] * factor, 0.01))
        result["suggestion"] = _round_price(max(asks[len(asks) // 2] * factor, 0.01))
        result["explanation"] = (
            f"近{LOOKBACK_DAYS}天该品类暂无成交记录，参考当前在售 {len(asks)} 件挂价"
            f"（取前 10 件中位）给出建议区间。"
        )
        return result

    result["explanation"] = (
        f"近{LOOKBACK_DAYS}天该品类暂无成交记录，也暂无在售商品可参考；"
        "建议先参考同类目其他商品定价。"
    )
    return result


def split_l2_samples(rows: List[Dict], cat_l2: Optional[str]) -> Tuple[List[float], List[float]]:
    """把一级类目成交样本切分为 (二级样本, 一级其余样本)（纯逻辑）。

    cat_l2 未指定时二级样本为空，直接用一级全量。
    """
    if not cat_l2:
        return [], [float(r["price"]) for r in rows]
    l2 = [float(r["price"]) for r in rows if r.get("cat_l2") == cat_l2]
    l1_rest = [float(r["price"]) for r in rows if r.get("cat_l2") != cat_l2]
    return l2, l1_rest


async def fetch_category_rows(db: AsyncSession) -> List[Dict]:
    result = await db.execute(_CATEGORY_SQL)
    return [
        {"code": r.code, "name": r.name, "parent_code": r.parent_code, "level": r.level}
        for r in result
    ]


async def fetch_transactions(db: AsyncSession, tenant_id: int, cat_l1: str) -> List[Dict]:
    since = datetime.now() - timedelta(days=LOOKBACK_DAYS)
    result = await db.execute(_TX_SQL, {
        "tid": tenant_id, "since": since, "cat1": cat_l1, "max_rows": MAX_ROWS,
    })
    return [{"price": float(r.price), "cat_l2": r.cat_l2} for r in result]


async def fetch_onsale_prices(db: AsyncSession, tenant_id: int, cat_l1: str) -> List[float]:
    result = await db.execute(_ASK_SQL, {"tid": tenant_id, "cat1": cat_l1, "max_rows": MAX_ROWS})
    return [float(r.price) for r in result]
