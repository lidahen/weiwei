"""电商 Agent 主图 / 子图组装 —— 方案 4.2 三层结构 + 方案 3 多轮停留。

主图：entry → router（LLM 意图分类）→ 条件路由 → 域子图 / 澄清 / 闲聊 → end
子图：pre_sale / in_sale / after_sale 三个域各一个 agent 工具循环节点。

方案 3（多轮停留）：主图挂 checkpointer，调用方携带 thread_id（=session_id）调用。
  - 域子图内的 agent 工具循环遇到 need_confirm 时调用 interrupt() 挂起图，
    等待用户确认后通过 Command(resume=...) 恢复，实现"下单→确认地址→确认→完成"的多轮流程；
  - state（messages / pending_confirm / user_id / domain 等）由 checkpointer 跨轮保留。
  checkpointer 优先 AsyncRedisSaver（会话跨进程/跨重启，TTL 自动淘汰，需 Redis 带
  RediSearch 模块，如 redis-stack）；Redis 不可用或缺模块时自动回退 InMemorySaver
  （重启丢会话，不影响功能正确性）。
"""
import asyncio
import logging

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph

from app.config import settings
from app.services.agent.nodes import (
    chitchat_node,
    clarify_node,
    end_node,
    entry_node,
    make_agent_subgraph_node,
    memory_recall_node,
    route_after_router,
    rag_retrieval_node,
    router_node,
)
from app.services.agent.state import ShoppingState

logger = logging.getLogger(__name__)


def _build_domain_subgraph(agent_type: str) -> StateGraph:
    """构建一个域子图：START → agent_node → END。"""
    sg = StateGraph(ShoppingState)
    sg.add_node("agent", make_agent_subgraph_node(agent_type))
    sg.add_edge(START, "agent")
    sg.add_edge("agent", END)
    return sg.compile()


def build_graph(checkpointer=None):
    """构建并编译完整的 LangGraph 主图（含四个域子图）。

    checkpointer 缺省 InMemorySaver（测试/无 Redis 场景）。
    """
    subgraphs = {
        "pre_sale": _build_domain_subgraph("pre_sale"),
        "in_sale": _build_domain_subgraph("in_sale"),
        "after_sale": _build_domain_subgraph("after_sale"),
        "community": _build_domain_subgraph("community"),
    }

    g = StateGraph(ShoppingState)

    # 主图节点
    g.add_node("entry", entry_node)
    g.add_node("memory_recall", memory_recall_node)
    g.add_node("rag_retrieval", rag_retrieval_node)
    g.add_node("router", router_node)
    g.add_node("clarify", clarify_node)
    g.add_node("chitchat", chitchat_node)
    g.add_node("pre_sale_subgraph", subgraphs["pre_sale"])
    g.add_node("in_sale_subgraph", subgraphs["in_sale"])
    g.add_node("after_sale_subgraph", subgraphs["after_sale"])
    g.add_node("community_subgraph", subgraphs["community"])
    g.add_node("end", end_node)

    # 边
    g.add_edge(START, "entry")
    # 记忆召回排在 RAG 之前：人设里记忆与 RAG 的先后顺序由 _ensure_persona 决定，
    # 这里只保证 state.memory_context 在进入路由/子图前已就绪
    g.add_edge("entry", "memory_recall")
    g.add_edge("memory_recall", "rag_retrieval")
    g.add_edge("rag_retrieval", "router")

    g.add_conditional_edges(
        "router",
        route_after_router,
        {
            "pre_sale_subgraph": "pre_sale_subgraph",
            "in_sale_subgraph": "in_sale_subgraph",
            "after_sale_subgraph": "after_sale_subgraph",
            "community_subgraph": "community_subgraph",
            "clarify": "clarify",
            "chitchat": "chitchat",
            "end": "end",
        },
    )

    # 各域子图与澄清/闲聊统一汇入 end（各节点内部已自带异常兜底）
    for node_name in ("pre_sale_subgraph", "in_sale_subgraph", "after_sale_subgraph",
                      "community_subgraph", "clarify", "chitchat"):
        g.add_edge(node_name, "end")
    g.add_edge("end", END)

    # 方案 3：挂 checkpointer，支持 interrupt/resume 跨轮多轮停留（thread_id = session_id）
    return g.compile(checkpointer=checkpointer or InMemorySaver())


async def _build_checkpointer():
    """优先 AsyncRedisSaver（需 RediSearch 模块）；任何失败回退 InMemorySaver。"""
    try:
        from langgraph.checkpoint.redis.aio import AsyncRedisSaver

        saver = AsyncRedisSaver(
            settings.redis_url,
            ttl={
                "default_ttl": settings.agent_session_ttl_minutes,
                "refresh_on_read": True,
            },
            connection_args={
                "socket_connect_timeout": settings.redis_connect_timeout,
                "socket_timeout": settings.redis_connect_timeout,
            },
        )
        # setup() 建 RediSearch 索引：Redis 不通 / 纯净版 Redis 缺模块都在这里失败
        await saver.setup()
        logger.info("Agent checkpointer：Redis（AsyncRedisSaver，TTL %d 分钟）",
                    settings.agent_session_ttl_minutes)
        return saver
    except Exception as e:
        logger.warning(
            "Redis checkpointer 不可用（%s），回退 InMemorySaver（重启丢会话；"
            "生产部署建议使用 redis-stack 镜像）", e,
        )
        return InMemorySaver()


_graph_cache = None
_aget_graph_lock = asyncio.Lock()


def get_graph():
    """获取全局单例图（内存 checkpointer 版；兼容旧调用与测试）。"""
    global _graph_cache
    if _graph_cache is None:
        _graph_cache = build_graph()
    return _graph_cache


_graph_async_cache = None


async def aget_graph():
    """获取生产用全局单例图（首次调用构建 Redis/内存 checkpointer，带并发保护）。"""
    global _graph_async_cache
    if _graph_async_cache is not None:
        return _graph_async_cache
    async with _aget_graph_lock:
        if _graph_async_cache is None:
            _graph_async_cache = build_graph(await _build_checkpointer())
        return _graph_async_cache
