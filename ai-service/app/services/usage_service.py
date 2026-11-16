"""AI 用量落账：聊天完成时按租户×自然日 upsert（calls/tokens 累加）。

计费口径收口点：Java 代理层透传 X-Tenant-Id（来自 TenantFilter 的租户上下文），
Python 在聊天完成后落账；无租户头（匿名/平台视角）跳过。落账失败只告警，不影响聊天主流程。
"""
import logging
from datetime import date, datetime

from sqlalchemy import text

from app.database import async_session

logger = logging.getLogger(__name__)

_UPSERT_SQL = text("""
    INSERT INTO ai_usage_daily (tenant_id, stat_date, calls, prompt_tokens, completion_tokens, update_time)
    VALUES (:tenant_id, :stat_date, :calls, :prompt_tokens, :completion_tokens, :now)
    ON DUPLICATE KEY UPDATE
        calls = calls + VALUES(calls),
        prompt_tokens = prompt_tokens + VALUES(prompt_tokens),
        completion_tokens = completion_tokens + VALUES(completion_tokens),
        update_time = VALUES(update_time)
""")


async def record_usage(tenant_id: int, calls: int = 1,
                       prompt_tokens: int = 0, completion_tokens: int = 0) -> None:
    """累加当日用量。任何异常只记日志（计量是旁路，不阻塞聊天）。"""
    if not tenant_id or tenant_id <= 0:
        return
    try:
        async with async_session() as session:
            await session.execute(_UPSERT_SQL, {
                "tenant_id": int(tenant_id),
                "stat_date": date.today(),
                "calls": int(calls or 0),
                "prompt_tokens": int(prompt_tokens or 0),
                "completion_tokens": int(completion_tokens or 0),
                "now": datetime.now(),
            })
            await session.commit()
    except Exception as e:
        logger.warning("record_usage failed tenant=%s: %s", tenant_id, e)
