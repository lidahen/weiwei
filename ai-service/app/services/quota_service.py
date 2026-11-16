"""AI 用量配额检查 + 调用明细落账（所有 AI 入口的每日配额单一账本）。

配额统一（2026-09）：本模块是唯一计数源。所有入口——聊天（/ai/chat、/ai/chat/stream）
与商品辅助（/ai/assist/*）——统一在 ai_usage_daily（租户 × 自然日）上事前检查并计数；
原 Java 侧 AiQuotaChecker 的 Redis（db1）按日 INCR 已停用（两套独立计数会把
sys_tenant.quota_ai_call_day 实际翻倍），类保留仅作口径迁移说明。

- sys_tenant.quota_ai_call_day = 0/NULL 表示不限（Pro），仍计数供用量展示；
- 套餐过期（expire_at < now）直接拒绝；
- 无租户上下文（X-Tenant-Id 缺失，匿名/平台视角直连调试）跳过检查；
- 计数在调用上游模型之前执行，失败调用不回滚（MVP 从简，防刷优先）。

调用明细（含用户/功能维度）落 ai_usage_log（DDL 见 docs/db 基线），
每次成功调用一行（feature=chat/rag-chat/product-copy/product-image），供后续按功能/用户计费分析。
计量均为旁路：失败只告警，不阻塞主流程。
"""
import logging
from datetime import date, datetime

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.services.usage_service import record_usage

logger = logging.getLogger(__name__)

# 配额超限/套餐过期的响应码（对齐 Java QuotaExceededException → HTTP 429 的语义）
QUOTA_EXCEEDED_CODE = 429


class QuotaExceededException(Exception):
    """AI 调用配额超限 / 套餐过期（msg 为可直接展示给用户的友好文案）。"""


_TENANT_SQL = text(
    "SELECT quota_ai_call_day, expire_at FROM sys_tenant WHERE id = :tid"
)

_CALLS_SQL = text(
    "SELECT calls FROM ai_usage_daily "
    "WHERE tenant_id = :tid AND stat_date = :stat_date"
)

_INSERT_LOG_SQL = text("""
    INSERT INTO ai_usage_log
        (tenant_id, user_id, feature, model_id, model_name, task_type, cost, create_time)
    VALUES
        (:tenant_id, :user_id, :feature, :model_id, :model_name, :task_type, :cost, :now)
""")


def tenant_id_from_request(request) -> int:
    """Java 代理层透传的租户 ID（X-Tenant-Id，来自 TenantContext）；无则 0（与 chat.py 惯例一致）。"""
    try:
        return int(request.headers.get("X-Tenant-Id") or 0)
    except (TypeError, ValueError):
        return 0


def user_id_from_request(request, body=None) -> str:
    """调用用户标识：优先 X-User-Id 头（Java 透传），兼容请求体 userId/user_id；无则空串。"""
    uid = (request.headers.get("X-User-Id") or "").strip()
    if not uid and body is not None:
        uid = str(getattr(body, "user_id", None) or "").strip()
    return uid[:64]


async def check_and_count(db: AsyncSession, tenant_id: int) -> None:
    """事前检查并预计数（ai_usage_daily.calls +1）。超限/过期抛 QuotaExceededException。

    - tenant_id <= 0（无租户上下文）或租户不存在：放行（对齐 Java billing 未识别租户跳过）；
    - 计数先于检查（对齐 Java：INCR 后比较，失败调用不回滚）；
    - 计量是旁路：record_usage 失败时读回旧值，检查退化为放行（宁可漏计不可误杀）；
    - 读回计数必须用新会话：请求级会话在 REPEATABLE READ 下持有先查 sys_tenant 的快照，
      看不到 record_usage（独立会话）刚提交的 +1，会把 51/50 误判为 50/50 放行。
    """
    if not tenant_id or tenant_id <= 0:
        return
    row = (await db.execute(_TENANT_SQL, {"tid": int(tenant_id)})).first()
    if row is None:
        return
    quota, expire_at = row
    if expire_at is not None and expire_at < datetime.now():
        raise QuotaExceededException("AI 套餐已过期，请续费后使用")
    try:
        await record_usage(int(tenant_id), calls=1)
    except Exception as e:  # noqa: BLE001 —— record_usage 自身兜底，这里再兜一层保证配额路径可用
        logger.warning("quota pre-count failed tenant=%s: %s", tenant_id, e)
    if not quota or int(quota) <= 0:
        return  # 不限（Pro），已计数供用量展示
    from app.database import async_session

    async with async_session() as fresh:
        calls = (await fresh.execute(
            _CALLS_SQL, {"tid": int(tenant_id), "stat_date": date.today()}
        )).scalar()
    if calls is not None and int(calls) > int(quota):
        raise QuotaExceededException(
            f"今日 AI 调用配额已用完（{int(calls)}/{int(quota)}），请升级套餐"
        )


async def log_usage(tenant_id: int, user_id: str = "", feature: str = "",
                    model_id=None, model_name: str = "",
                    task_type: str = "text", cost: int = 1) -> None:
    """事后计量：单次调用明细落 ai_usage_log（成功调用才记账）。失败只告警不阻塞。"""
    if not tenant_id or tenant_id <= 0:
        return
    try:
        from app.database import async_session

        async with async_session() as session:
            await session.execute(_INSERT_LOG_SQL, {
                "tenant_id": int(tenant_id),
                "user_id": (user_id or "")[:64],
                "feature": (feature or "")[:64],
                "model_id": model_id,
                "model_name": (model_name or "")[:128],
                "task_type": (task_type or "text")[:32],
                "cost": int(cost or 1),
                "now": datetime.now(),
            })
            await session.commit()
    except Exception as e:  # noqa: BLE001 —— 计量是旁路，不影响主流程
        logger.warning("log_usage failed tenant=%s feature=%s: %s", tenant_id, feature, e)
