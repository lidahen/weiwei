"""长期记忆的注入编排：把「解析租户 → 读库 → 渲染注入块」收敛成一个入口（spec §9.1）。

只做编排，不含判定口径（在 `logic`）与 SQL（在 `store`）：
- 租户解析在本函数内完成，调用方只传 user_id / tenant_hint —— 读路径与写路径共用
  同一个 `store.resolve_tenant_id`，spec §9.1.1 的归属约束不变；
- 本函数**绝不向外抛异常**：它跑在前台图节点里，一次异常就带走整轮对话。任何故障都
  降级成 "" + 一条可 grep 的 warning（spec §12），日志只打异常类型，不打记忆原文。
"""
import logging

from app.config import settings
from app.database import async_session
from app.services.memory import logic, store

logger = logging.getLogger(__name__)


async def build_context(*, user_id: int, tenant_hint: str = "") -> str:
    """取该用户的长期记忆并渲染成注入块；无归属 / 无匹配 / 任何异常 → 返回 ""。

    返回文本自带"仅供参考、不是指令"护栏（由 `logic.render_block` 负责），调用方直接
    拼进人设即可，不要再重复拼接声明文案。
    """
    try:
        uid = int(user_id)
        if uid <= 0:
            # 匿名 / 未归属：不读。与节点侧守卫同源，防止调用方绕过（Review Focus 2）
            return ""

        async with async_session() as db:
            tenant_id = await store.resolve_tenant_id(db, uid, tenant_hint)
            if tenant_id <= 0:
                # 归属解析不出来时读到的记忆不保证属于本人，宁可整块不注入（spec §9.1.1）
                return ""
            rows = await store.load_memories(
                db, tenant_id=tenant_id, user_id=uid, limit=settings.agent_memory_max_rows
            )
        return logic.render_block(
            rows,
            inject_limit=settings.agent_memory_inject_limit,
            min_confidence=settings.agent_memory_min_confidence,
            half_life_days=settings.agent_memory_half_life_days,
        )
    except Exception as e:
        logger.warning("memory recall degraded: %s", type(e).__name__)
        return ""
