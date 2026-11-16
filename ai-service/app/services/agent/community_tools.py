"""社区管家工具层 —— 互助悬赏 / 社区概况 / 热帖搜索。

全部为 [L1] 只读查询工具，按 tenant_id 强制隔离（InjectedToolArg，由
run_agent_tool_loop 从 state 注入，模型不可见、不向用户索要）。
无租户上下文时直接返回错误，绝不跨租户查询。
"""
import json
import logging
from typing import Annotated, Optional

from langchain_core.tools.base import InjectedToolArg
from langchain_core.tools import tool

from app.database import async_session

logger = logging.getLogger(__name__)

# 互助任务类型：自然语言/编号 → DB 枚举（twelve_runner_task.task_type）
RUNNER_TYPE_ALIASES = {
    "快递": 1, "代取快递": 1, "取快递": 1, "1": 1,
    "代买": 2, "买东西": 2, "代买东西": 2, "互助买": 2, "跑腿买": 2, "帮忙买": 2, "2": 2,
    "排队": 3, "代排队": 3, "3": 3,
    "其他": 4, "帮忙": 4, "4": 4,
}

RUNNER_TYPE_NAMES = {1: "代取快递", 2: "代买东西", 3: "代排队", 4: "其他"}


def parse_runner_type(value: Optional[str]) -> Optional[int]:
    """把用户口语化的互助类型映射为 DB 枚举（纯逻辑，可单测）；无法识别返回 None 表示不限类型。"""
    if not value:
        return None
    v = str(value).strip()
    if not v:
        return None
    if v in RUNNER_TYPE_ALIASES:
        return RUNNER_TYPE_ALIASES[v]
    for alias, code in RUNNER_TYPE_ALIASES.items():
        if alias in v:
            return code
    return None


def _require_tenant(tenant_id: Optional[str]) -> Optional[str]:
    """无租户上下文时返回错误 JSON（社区域数据绝不跨租户查询），有租户返回 None。"""
    if not str(tenant_id or "").strip() or not str(tenant_id).strip().isdigit() or int(tenant_id) <= 0:
        return json.dumps({"error": "无租户上下文，无法查询社区数据"}, ensure_ascii=False)
    return None


@tool
async def search_runner_tasks(
    task_type: Optional[str] = None,
    reward_min: Optional[float] = None,
    reward_max: Optional[float] = None,
    limit: int = 8,
    tenant_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 搜索进行中的互助悬赏任务。用户想找互助/接单/发布需求前先看看互助大厅时调用。
    参数：task_type 类型（代取快递/代买东西/代排队/其他，可空不限）、
    reward_min/reward_max 报酬区间（元）、limit 条数。
    返回 JSON：{tasks: [{task_id, task_no, task_type_name, title, description, price, deadline}], total}。
    """
    guard = _require_tenant(tenant_id)
    if guard:
        return guard
    type_code = parse_runner_type(task_type)
    try:
        async with async_session() as db:
            from sqlalchemy import text

            sql = text(
                """
                SELECT id, task_no, task_type, title, description, price, deadline
                FROM twelve_runner_task
                WHERE tenant_id = :tid AND status = 'pending' AND deadline > NOW()
                  AND (:type IS NULL OR task_type = :type)
                  AND (:rmin IS NULL OR price >= :rmin)
                  AND (:rmax IS NULL OR price <= :rmax)
                ORDER BY price DESC, create_time DESC
                LIMIT :lim
                """
            )
            rows = (await db.execute(sql, {
                "tid": int(tenant_id), "type": type_code,
                "rmin": reward_min, "rmax": reward_max,
                "lim": max(1, min(int(limit or 8), 20)),
            })).fetchall()
            tasks = [
                {
                    "task_id": r[0], "task_no": r[1],
                    "task_type_name": RUNNER_TYPE_NAMES.get(r[2], str(r[2])),
                    "title": r[3], "description": r[4],
                    "price": float(r[5]) if r[5] is not None else 0.0,
                    "deadline": r[6].isoformat() if r[6] is not None else None,
                }
                for r in rows
            ]
            return json.dumps(
                {"tasks": tasks, "total": len(tasks),
                 "message": "暂时没有符合条件的悬赏任务" if not tasks else ""},
                ensure_ascii=False,
            )
    except Exception as e:
        logger.error("search_runner_tasks error: %s", e)
        return json.dumps({"error": str(e)}, ensure_ascii=False)


@tool
async def get_community_overview(
    tenant_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 查询社区概况。用户问"社区近况/今天怎么样/有什么新鲜事"时先调用此工具。
    无参数。返回 JSON：{productsOnSale, newFeedsToday, runnerPending, runnerDoneTotal,
    latestReport: {reportDate, title}, today}。
    """
    guard = _require_tenant(tenant_id)
    if guard:
        return guard
    try:
        async with async_session() as db:
            from sqlalchemy import text

            tid = int(tenant_id)
            products = (await db.execute(text(
                "SELECT COUNT(*) FROM product WHERE tenant_id = :tid AND status = 'onsale'"),
                {"tid": tid})).scalar()
            new_feeds = (await db.execute(text(
                "SELECT COUNT(*) FROM feed WHERE tenant_id = :tid AND audit_status = 'approved' "
                "AND create_time >= CURDATE()"),
                {"tid": tid})).scalar()
            runner_pending = (await db.execute(text(
                "SELECT COUNT(*) FROM twelve_runner_task WHERE tenant_id = :tid "
                "AND status = 'pending' AND deadline > NOW()"),
                {"tid": tid})).scalar()
            runner_done = (await db.execute(text(
                "SELECT COUNT(*) FROM twelve_runner_task WHERE tenant_id = :tid AND status = 'completed'"),
                {"tid": tid})).scalar()
            report = (await db.execute(text(
                "SELECT report_date, title FROM twelve_daily_report "
                "WHERE tenant_id = :tid ORDER BY report_date DESC, id DESC LIMIT 1"),
                {"tid": tid})).fetchone()
            today = (await db.execute(text("SELECT CURDATE()"))).scalar()
            return json.dumps(
                {
                    "today": today.isoformat() if today else None,
                    "productsOnSale": int(products or 0),
                    "newFeedsToday": int(new_feeds or 0),
                    "runnerPending": int(runner_pending or 0),
                    "runnerDoneTotal": int(runner_done or 0),
                    "latestReport": (
                        {"reportDate": report[0].isoformat(), "title": report[1]}
                        if report else None
                    ),
                },
                ensure_ascii=False,
            )
    except Exception as e:
        logger.error("get_community_overview error: %s", e)
        return json.dumps({"error": str(e)}, ensure_ascii=False)


@tool
async def search_feeds(
    keyword: Optional[str] = None,
    limit: int = 5,
    tenant_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 搜索社区帖子。用户问"大家都在聊什么/有没有关于XX的帖子/热帖"时调用。
    参数：keyword 关键词（可空则返回最新热帖）、limit 条数。
    返回 JSON：{feeds: [{feed_id, summary, likes, comments, createTime}], total}。
    """
    guard = _require_tenant(tenant_id)
    if guard:
        return guard
    try:
        async with async_session() as db:
            from sqlalchemy import text

            sql = text(
                """
                SELECT feed_id, content, like_count, comment_count, create_time
                FROM feed
                WHERE tenant_id = :tid AND audit_status = 'approved' AND status = 'normal'
                  AND (:kw IS NULL OR content LIKE CONCAT('%', :kw, '%'))
                ORDER BY hot_score DESC, create_time DESC
                LIMIT :lim
                """
            )
            rows = (await db.execute(sql, {
                "tid": int(tenant_id), "kw": (keyword or "").strip() or None,
                "lim": max(1, min(int(limit or 5), 10)),
            })).fetchall()
            feeds = [
                {
                    "feed_id": r[0],
                    "summary": (r[1] or "")[:80],
                    "likes": int(r[2] or 0),
                    "comments": int(r[3] or 0),
                    "createTime": r[4].isoformat() if r[4] is not None else None,
                }
                for r in rows
            ]
            return json.dumps(
                {"feeds": feeds, "total": len(feeds),
                 "message": "没有找到相关帖子" if not feeds else ""},
                ensure_ascii=False,
            )
    except Exception as e:
        logger.error("search_feeds error: %s", e)
        return json.dumps({"error": str(e)}, ensure_ascii=False)
