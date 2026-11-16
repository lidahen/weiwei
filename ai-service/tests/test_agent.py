"""电商 Agent LangGraph 单元测试（方案 Phase 1 MVP）。

覆盖：图结构、入口安全过滤、路由条件边、澄清/闲聊分支、
L2/L3 工具两段式确认机制（首次调用不触 DB，无需外部依赖）。
"""
import json
from typing import Optional

import pytest
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from app.services.agent import build_graph
from app.services.agent.nodes import (
    CLARIFY_TEMPLATE,
    clarify_node,
    entry_node,
    route_after_router,
    router_node,
)
from app.services.agent.state import (
    CONFIDENCE_THRESHOLD,
    INTENT_DOMAIN,
)
from app.services.agent.tools import (
    add_to_favorite,
    confirm_order,
    create_dispute,
    pay_order,
)


def _state(**kw) -> dict:
    base: dict = {
        "messages": [HumanMessage(content="你好")],
        "user_id": "1",
        "intent": "",
        "confidence": 0.0,
        "domain": "general",
        "current_agent": "",
        "products": [],
        "tool_results": [],
        "pending_confirm": "",
        "pending_action": {},
        "idempotency_keys": {},
        "retry_count": 0,
        "error": "",
        "final_reply": "",
        "finished": False,
        "human_handoff": False,
    }
    base.update(kw)
    return base


def test_graph_structure():
    g = build_graph()
    nodes = set(g.get_graph().nodes.keys())
    assert "entry" in nodes
    assert "router" in nodes
    assert "pre_sale_subgraph" in nodes
    assert "in_sale_subgraph" in nodes
    assert "after_sale_subgraph" in nodes
    assert "clarify" in nodes
    assert "chitchat" in nodes
    assert "end" in nodes


def test_entry_blocks_empty_input():
    st = _state(messages=[HumanMessage(content="   ")])
    out = entry_node(st)
    assert out["error"] == "empty_input"
    assert out["intent"] == "other"


def test_entry_truncates_long_input():
    long_text = "a" * 2000
    st = _state(messages=[HumanMessage(content=long_text)])
    out = entry_node(st)
    msgs = out.get("messages") or st["messages"]
    assert len(msgs[-1].content) < 1100
    assert "已截断" in msgs[-1].content


def test_route_high_confidence_pre_sale():
    st = _state(intent="find_product", confidence=0.9)
    assert route_after_router(st) == "pre_sale_subgraph"


def test_route_high_confidence_in_sale():
    st = _state(intent="checkout", confidence=0.95)
    assert route_after_router(st) == "in_sale_subgraph"


def test_route_high_confidence_after_sale():
    st = _state(intent="after_sales", confidence=0.9)
    assert route_after_router(st) == "after_sale_subgraph"


def test_route_low_confidence_to_clarify():
    st = _state(intent="find_product", confidence=CONFIDENCE_THRESHOLD - 0.1)
    assert route_after_router(st) == "clarify"


def test_route_other_to_clarify():
    st = _state(intent="other", confidence=0.9)
    assert route_after_router(st) == "clarify"


def test_route_chitchat():
    st = _state(intent="chitchat", confidence=0.99)
    assert route_after_router(st) == "chitchat"


def test_route_empty_input_to_clarify():
    st = _state(intent="", confidence=0.0, error="empty_input")
    assert route_after_router(st) == "clarify"


def test_clarify_node_reply():
    st = _state()
    out = clarify_node(st)
    assert out["final_reply"] == CLARIFY_TEMPLATE
    assert out["finished"] is True


def test_intent_domain_mapping_complete():
    for intent, domain in INTENT_DOMAIN.items():
        assert domain in ("pre_sale", "in_sale", "after_sale", "community", "general")


@pytest.mark.asyncio
async def test_router_node_without_api_key():
    """未配置 AI_API_KEY 时路由应降级为 other，而非抛异常。"""
    import app.services.agent.nodes as nodes

    orig = nodes.settings.ai_api_key
    nodes.settings.ai_api_key = ""
    try:
        st = _state(messages=[HumanMessage(content="我想买手机")])
        out = await router_node(st)
        assert out["intent"] == "other"
        assert out["error"] == "ai_not_configured"
    finally:
        nodes.settings.ai_api_key = orig


@pytest.mark.asyncio
async def test_add_to_favorite_requires_confirm():
    """L2：首次调用返回 need_confirm，不落库。"""
    result = json.loads(await add_to_favorite.ainvoke(
        {"user_id": "1", "product_id": 100}
    ))
    assert result["need_confirm"] is True
    assert result["confirm_token"]


@pytest.mark.asyncio
async def test_confirm_order_requires_confirm():
    """L3：下单首次调用返回 need_confirm。"""
    result = json.loads(await confirm_order.ainvoke(
        {"user_id": "1", "product_id": 100, "price": 88.0}
    ))
    assert result["need_confirm"] is True
    assert result["confirm_token"]


@pytest.mark.asyncio
async def test_pay_order_requires_confirm():
    """L3：支付首次调用返回 need_confirm。"""
    result = json.loads(await pay_order.ainvoke(
        {"user_id": "1", "order_no": "TWT123"}
    ))
    assert result["need_confirm"] is True
    assert result["confirm_token"]


@pytest.mark.asyncio
async def test_create_dispute_requires_confirm():
    """L3：纠纷首次调用返回 need_confirm。"""
    result = json.loads(await create_dispute.ainvoke(
        {"user_id": "1", "order_no": "TWT123", "reason": "退款"}
    ))
    assert result["need_confirm"] is True
    assert result["confirm_token"]


@pytest.mark.asyncio
async def test_confirm_token_invalid_after_first_pass():
    """伪造 confirm_token 应被拒绝。"""
    result = json.loads(await add_to_favorite.ainvoke(
        {"user_id": "1", "product_id": 100, "confirm_token": "fake-token"}
    ))
    assert "error" in result


# ─────────────────────────────────────────────
# 修复回归测试：归属校验 / 幂等键 / 摘要兜底 / 输入防御
# ─────────────────────────────────────────────
def test_summarize_products_skips_missing_price():
    """_summarize_products 存在无价格商品时，"性价比最高"应选中真实最低价商品（防下标错位）。"""
    from app.services.agent.nodes import _summarize_products
    from app.schemas.chat import ProductCard

    products = [
        ProductCard(product_id=1, title="贵手机", price=5000.0, condition="全新", city="上海"),
        ProductCard(product_id=2, title="无价商品", price=None, condition="未知", city="北京"),
        ProductCard(product_id=3, title="便宜手机", price=999.0, condition="九成新", city="广州"),
    ]
    text = _summarize_products(products)
    assert "便宜手机" in text
    assert "999" in text


def test_idempotency_key_binds_params():
    """幂等键应绑定业务参数：不同商品互不干扰，同商品同窗口命中。"""
    from app.services.agent.tools import _idempotency_key

    k1 = _idempotency_key("1", "confirm_order", {"product_id": 100, "price": 88.0})
    k2 = _idempotency_key("1", "confirm_order", {"product_id": 101, "price": 88.0})
    k3 = _idempotency_key("1", "confirm_order", {"product_id": 100, "price": 88.0})
    k4 = _idempotency_key("2", "confirm_order", {"product_id": 100, "price": 88.0})
    assert k1 != k2, "不同商品不应共享幂等键"
    assert k1 == k3, "同用户同商品同价同窗口应命中同一幂等键"
    assert k1 != k4, "不同用户不应共享幂等键"


@pytest.mark.asyncio
async def test_pop_valid_confirm_binds_user(monkeypatch):
    """confirm_token 应绑定发起用户：他人冒用被拒绝，且令牌被消费（防反复试探）。"""
    from app.services import redis_client
    from app.services.agent import tools as agent_tools

    async def _no_redis():
        return None

    monkeypatch.setattr(redis_client, "get_redis", _no_redis)

    token = agent_tools._new_confirm_token("add_to_favorite", {"user_id": "1", "product_id": 5})
    await agent_tools._save_pending_confirm(token, "add_to_favorite", {"user_id": "1", "product_id": 5})
    # 过期令牌被拒绝
    agent_tools._pending_confirms[token]["ts"] = 0
    assert await agent_tools._pop_valid_confirm(token, "1") is None, "过期令牌应被拒绝"

    token2 = agent_tools._new_confirm_token("add_to_favorite", {"user_id": "1", "product_id": 6})
    await agent_tools._save_pending_confirm(token2, "add_to_favorite", {"user_id": "1", "product_id": 6})
    assert await agent_tools._pop_valid_confirm(token2, "2") is None, "他人冒用应被拒绝"
    # 冒用尝试同样消费令牌（与 Redis GETDEL 语义一致，防暴力枚举），本人再用也失效
    assert await agent_tools._pop_valid_confirm(token2, "1") is None


def test_to_lc_messages_tolerates_bad_input():
    """_to_lc_messages 对 dict/字符串/非法元素应跳过而非崩溃。"""
    from app.services.agent.runner import _to_lc_messages
    from langchain_core.messages import HumanMessage

    lc = _to_lc_messages(
        [
            {"role": "user", "content": "我想买手机"},   # dict 兼容
            "裸字符串",                                   # 非法元素跳过
            {"role": "assistant", "content": "好的"},     # dict assistant
        ],
        "system",
    )
    assert len(lc) == 3  # system + user + assistant
    assert isinstance(lc[1], HumanMessage)


# ─────────────────────────────────────────────
# 方案 3（多轮停留）：interrupt / checkpointer / InjectedToolArg
# ─────────────────────────────────────────────
class _FakeInterrupt:
    def __init__(self, value):
        self.value = value


def test_extract_interrupt_confirm():
    """_extract_interrupt 应从图结果中提取 confirm 挂起信息。"""
    from app.services.agent.runner import _extract_interrupt

    result = {"__interrupt__": (_FakeInterrupt({"type": "confirm", "tool": "confirm_order",
                                                "message": "确认下单?"}),)}
    pending = _extract_interrupt(result)
    assert pending is not None
    assert pending.type == "confirm"
    assert pending.tool == "confirm_order"
    assert pending.message == "确认下单?"


def test_extract_interrupt_ignores_other():
    """非 confirm 类型的 interrupt 应被忽略。"""
    from app.services.agent.runner import _extract_interrupt

    result = {"__interrupt__": (_FakeInterrupt({"type": "other"}),)}
    assert _extract_interrupt(result) is None
    assert _extract_interrupt({}) is None


class _FakeGraph:
    """内存版伪图：记录每次 ainvoke 的入参，验证增量/全量策略。"""

    def __init__(self, with_history: bool = True):
        self.calls = []
        self.with_history = with_history

    async def ainvoke(self, payload, config=None):
        self.calls.append((payload, config))
        return {"final_reply": "ok"}

    async def aget_state(self, config=None):
        # next 为空 = 图未被 interrupt 挂起；with_history 控制 checkpoint 是否命中历史
        values = {"messages": ["history"]} if self.with_history else {}
        return type("S", (), {"values": values, "next": ()})()


@pytest.mark.asyncio
async def test_run_graph_resume_uses_command():
    """resume 非空时应用 Command(resume=...) 恢复图，且带 thread_id 配置。"""
    from app.services.agent.runner import _run_graph
    from langgraph.types import Command

    g = _FakeGraph()
    await _run_graph(g, [], "", None, "sess-1", "确认，地址没问题")
    payload, config = g.calls[0]
    assert isinstance(payload, Command)
    assert payload.resume == "确认，地址没问题"
    assert config["configurable"] == {"thread_id": "sess-1"}
    assert config["recursion_limit"] >= 10  # 防死循环上限（设计文档 6.6）


@pytest.mark.asyncio
async def test_run_graph_incremental_with_history():
    """session_id 已有历史时，应只上送最新一条用户消息（避免消息重复累加）。"""
    from app.services.agent.runner import _run_graph
    from app.schemas.chat import ChatMsg

    g = _FakeGraph()
    await _run_graph(
        g,
        [ChatMsg(role="user", content="第一轮"), ChatMsg(role="assistant", content="好"),
         ChatMsg(role="user", content="我要下单")],
        "42", None, "sess-1", None,
    )
    payload, config = g.calls[0]
    assert config["configurable"] == {"thread_id": "sess-1"}
    assert config["recursion_limit"] >= 10  # 防死循环上限（设计文档 6.6）
    msgs = payload["messages"]
    assert len(msgs) == 1
    assert "我要下单" in msgs[0].content
    assert payload["user_id"] == "42"


@pytest.mark.asyncio
async def test_run_graph_full_without_session():
    """无 session_id 时全量执行：图挂了 checkpointer，必须给临时 thread_id（等效无跨轮状态）。"""
    from app.services.agent.runner import _run_graph
    from app.schemas.chat import ChatMsg

    g = _FakeGraph(with_history=False)
    await _run_graph(
        g,
        [ChatMsg(role="user", content="你好")],
        "7", None, None, None,
    )
    payload, config = g.calls[0]
    assert config["configurable"]["thread_id"].startswith("transient-")
    assert len(payload["messages"]) >= 2  # system + user


def test_user_id_hidden_from_llm_schema():
    """InjectedToolArg 标注的 user_id 应对 LLM 隐藏（bind_tools 后不可见），
    模型不会生成 user_id 参数，也就不会向用户索要 ID。"""
    from app.services.agent.tools import confirm_order

    from langchain_openai import ChatOpenAI

    llm = ChatOpenAI(base_url="http://unused", api_key="sk-x", model="x")
    bound = llm.bind_tools([confirm_order])
    tools = bound.kwargs.get("tools") or []
    params = tools[0]["function"]["parameters"]["properties"]
    assert "user_id" not in params, "user_id 必须对模型隐藏"
    assert "product_id" in params
    assert "confirm_token" in params


# ─────────────────────────────────────────────
# interestHint 全链路（spec §9.3）：画像提升为 state 字段，不再借道 messages
# ─────────────────────────────────────────────
def test_to_lc_messages_drops_system_role():
    """_to_lc_messages 不再把 role=system 转成 SystemMessage（画像改走 state 字段）。

    system 角色一律丢弃：画像已由 routers 显式传参，若仍保留 system 消息，
    模型会同时看到人设里的画像与这条"用户输入"，且外部可借此把服务端文本
    当用户输入注入。
    """
    from app.services.agent.runner import _to_lc_messages
    from langchain_core.messages import SystemMessage

    lc = _to_lc_messages([{"role": "system", "content": "画像"},
                          {"role": "user", "content": "你好"}], "sys")
    assert len(lc) == 2 and not any(isinstance(m, SystemMessage) for m in lc[1:])


def test_initial_input_carries_interest_hint():
    """新会话首轮走 _initial_input —— 漏了这条，画像恰好在最需要它的第一轮消失。"""
    from app.services.agent.runner import _initial_input

    assert _initial_input([], "7", None, "1", "常看数码")["interest_hint"] == "常看数码"


@pytest.mark.asyncio
async def test_run_graph_incremental_passes_interest_hint():
    """增量分支（已有 checkpoint）同样要带上本轮 interestHint（spec §9.3 现状 bug）。

    增量分支只上送最后一条用户消息，其余字段全靠这个 dict —— 漏掉 interest_hint
    就退化成"checkpoint 里第 1 轮那份旧画像"，此后每轮刷新都不生效。
    """
    from app.services.agent.runner import _run_graph
    from app.schemas.chat import ChatMsg

    g = _FakeGraph()
    await _run_graph(
        g,
        [ChatMsg(role="user", content="第一轮"), ChatMsg(role="assistant", content="好"),
         ChatMsg(role="user", content="有便宜点的吗")],
        "42", None, "sess-1", None, "", "常看数码",
    )
    payload, _config = g.calls[0]
    assert payload["interest_hint"] == "常看数码"


@pytest.mark.asyncio
async def test_run_graph_new_session_passes_interest_hint():
    """新会话（无 checkpoint）走 _initial_input：调用点必须透传，否则首轮无画像。"""
    from app.services.agent.runner import _run_graph
    from app.schemas.chat import ChatMsg

    g = _FakeGraph(with_history=False)
    await _run_graph(
        g,
        [ChatMsg(role="user", content="你好")],
        "42", None, "sess-1", None, "", "常看数码",
    )
    payload, _config = g.calls[0]
    assert payload["interest_hint"] == "常看数码"


@pytest.mark.asyncio
@pytest.mark.parametrize("endpoint", ["chat", "chat_stream"],
                         ids=["/ai/chat", "/ai/chat/stream"])
async def test_chat_endpoint_passes_interest_hint_as_field(monkeypatch, endpoint):
    """路由层不再把 interestHint 伪造成 ChatMsg(role="system")，改为显式入参。

    两个入口都要覆盖：Java 线上走的是 /ai/chat/stream（AiGuideController:136 → :151），
    只钉住 /ai/chat 的话，将来只在流式路径上重新塞 system 消息，全套测试仍然全绿。
    直接调端点（不起 HTTP 服务）：只替换 agent chat/chat_stream，租户为 0 时配额/落账均自动跳过。
    """
    from app.routers import chat as chat_router
    from app.schemas.chat import ChatResponse

    captured: dict = {}

    async def _fake_chat(messages, **kwargs):
        captured["messages"] = messages
        captured.update(kwargs)
        return ChatResponse(reply="ok")

    async def _fake_chat_stream(messages, **kwargs):
        captured["messages"] = messages
        captured.update(kwargs)
        yield 'data: {"type": "done", "usage": null}\n\n'

    monkeypatch.setattr(chat_router, "chat", _fake_chat)
    monkeypatch.setattr(chat_router, "chat_stream", _fake_chat_stream)

    class _FakeRequest:
        headers = {}

    body = {"messages": [{"role": "user", "content": "你好"}],
            "interestHint": "常看数码", "userId": "7"}

    if endpoint == "chat":
        assert (await chat_router.chat_endpoint(_FakeRequest(), body)).code == 200
    else:
        resp = await chat_router.chat_stream_endpoint(_FakeRequest(), body)
        # 流式端点返回 SSE 生成器：消费到底，确认真的走完端点函数体（而非绕过它）
        chunks = [c async for c in resp.body_iterator]
        assert '"done"' in "".join(chunks)

    assert captured["interest_hint"] == "常看数码"
    assert all(m.role != "system" for m in captured["messages"])


# ─────────────────────────────────────────────
# 行为证据 trace（spec §6.4）：白名单参数 + 每轮重置
# ─────────────────────────────────────────────
def test_trace_params_whitelists_by_param_name():
    """按参数名过滤 —— func_args 在调用点已被注入 user_id/tenant_id，
    只有按名字白名单才挡得住 PII（spec §6.4）。"""
    from app.services.agent import nodes

    got = nodes._trace_params({"price_max": 50, "cat_l1": "数码",
                               "user_id": "7", "tenant_id": "1",
                               "keyword": "张三 13800138000"})
    assert got == {"price_max": 50, "cat_l1": "数码"}


def test_trace_params_drops_defaults_and_blanks():
    """order_by="default" / None / "" 都不记 —— 它们不含行为信号。"""
    from app.services.agent import nodes

    assert nodes._trace_params({"order_by": "default", "condition": None, "city": ""}) == {}


def test_trace_params_degraded_logs_warning(caplog):
    """非 mapping 入参不得抛异常（trace 绝不拖垮主流程），但必须留下可 grep 的 warning。

    记忆链路"对用户静默、对日志不静默"（plan Global Constraints）：静默 return {}
    会让行为证据悄悄消失，事后无从定位。
    """
    import logging

    from app.services.agent import nodes

    with caplog.at_level(logging.WARNING):
        assert nodes._trace_params(["不是", "dict"]) == {}
    assert "trace params degraded" in caplog.text
    assert "AttributeError" in caplog.text


def test_trace_params_drops_pii_values(caplog):
    """参数值可能带手机号/门牌：trace 会进日志与 checkpoint，必须与写路径同口径硬拦。"""
    import logging

    from app.services.agent import nodes

    with caplog.at_level(logging.WARNING):
        got = nodes._trace_params({
            "city": "望京3号楼2单元501室",
            "cat_l1": "电脑配件",
            "condition": "13800138000",
        })
    assert got == {"cat_l1": "电脑配件"}
    assert "trace params dropped: pii=" in caplog.text
    assert "13800138000" not in caplog.text
    assert "望京3号楼" not in caplog.text


def test_entry_node_resets_trace():
    """entry 是本轮状态重置点，也要清 trace —— 否则 clarify/chitchat 轮会把上一轮
    的 tool_calls 当成本轮行为证据重放（spec §6.4.1）。"""
    from app.services.agent import nodes

    out = nodes.entry_node({"messages": [HumanMessage(content="你好")],
                            "trace": {"tool_calls": [{"tool": "search_products"}]}})
    assert out["trace"] == {}


class _TraceArgs(BaseModel):
    """假搜索工具入参：白名单参数 + 注入参数 + PII 原文同处一个 dict。"""

    price_max: Optional[float] = None
    cat_l1: Optional[str] = None
    keyword: Optional[str] = None
    user_id: str = ""
    tenant_id: str = ""


class _FakeSearchTool:
    """只实现 run_agent_tool_loop 用到的属性（name / args_schema / ainvoke）。"""

    name = "search_products"
    args_schema = _TraceArgs

    def __init__(self):
        # 记下工具实际收到的入参：只有它能证明"注入确实发生"。否则注入整段被删掉，
        # trace 断言（只覆盖白名单参数）照样全绿——测试钉不住真正要保的性质。
        self.captured: dict = {}

    async def ainvoke(self, func_args: dict):
        self.captured = dict(func_args)
        return json.dumps([{"product_id": 1, "title": "测试机", "cover": "c.jpg",
                            "price": 50.0, "condition": "九成新", "city": "上海"}])


class _TraceFakeLLM:
    """bind_tools 后原样返回自己（真正的调用被 ainvoke_or_stream 替身拦下）。"""

    def bind_tools(self, tools):
        return self


class _FakeSessionFactory:
    """`async with async_session() as db` 的替身（商品卡片字段齐全，不会回查商品表）。"""

    def __call__(self):
        return self

    async def __aenter__(self):
        return object()

    async def __aexit__(self, *exc):
        return False


async def test_tool_loop_trace_records_whitelisted_params_only(monkeypatch):
    """端到端钉死两个独立性质：注入确实发生 + 注入后的参数不进 trace。

    性质一（假工具收到的入参）证明 user_id/tenant_id 被真正注入——否则把注入整段
    删掉，只看 trace 断言是发现不了的（它们本就不在白名单里）。
    性质二（trace.params 白名单）证明注入参数不落日志：若为了省事直接记整个
    func_args，PII（以及 user_id/tenant_id）就进日志了。
    """
    import app.database as _db
    import app.services.agent.nodes as nodes
    from langchain_core.messages import AIMessage

    calls = {"n": 0}

    async def _fake_invoke(llm, msgs):
        calls["n"] += 1
        if calls["n"] == 1:
            return AIMessage(content="", tool_calls=[{
                "name": "search_products",
                "args": {"price_max": 50, "cat_l1": "数码", "keyword": "张三 13800138000"},
                "id": "t1",
            }]), {}
        return AIMessage(content="好的"), {}

    tool = _FakeSearchTool()
    monkeypatch.setattr(nodes, "ainvoke_or_stream", _fake_invoke)
    monkeypatch.setattr(nodes, "_get_llm", lambda: _TraceFakeLLM())
    monkeypatch.setattr(nodes, "AGENT_TOOLS", {"pre_sale": [tool]})
    monkeypatch.setattr(nodes.settings, "ai_api_key", "test-key")
    monkeypatch.setattr(_db, "async_session", _FakeSessionFactory())

    out = await nodes.run_agent_tool_loop(
        {"messages": [HumanMessage(content="找个便宜手机")], "user_id": "7", "tenant_id": "1"},
        "pre_sale",
    )
    # 性质一：state 里的 user_id/tenant_id 确实随参数进了工具（注入没被删）
    assert tool.captured["user_id"] == "7"
    assert tool.captured["tenant_id"] == "1"
    # 性质二：注入发生在取 trace 参数之前，而白名单把注入参数与 PII 一起挡住
    rec = out["trace"]["tool_calls"][0]
    assert rec["ok"] is True
    assert rec["params"] == {"price_max": 50, "cat_l1": "数码"}
    assert "keyword" not in rec["params"], "搜索词原文（可能含姓名/手机号）绝不入 trace"
    assert not ({"user_id", "tenant_id"} & set(rec["params"])), "注入参数绝不入 trace"


async def test_tool_loop_trace_omits_params_when_no_evidence(monkeypatch):
    """无白名单参数（或全是 default/空值）时不写 params 键，避免日志里堆空 dict。"""
    import app.database as _db
    import app.services.agent.nodes as nodes
    from langchain_core.messages import AIMessage

    calls = {"n": 0}

    async def _fake_invoke(llm, msgs):
        calls["n"] += 1
        if calls["n"] == 1:
            return AIMessage(content="", tool_calls=[{
                "name": "search_products",
                "args": {"order_by": "default"},
                "id": "t1",
            }]), {}
        return AIMessage(content="好的"), {}

    monkeypatch.setattr(nodes, "ainvoke_or_stream", _fake_invoke)
    monkeypatch.setattr(nodes, "_get_llm", lambda: _TraceFakeLLM())
    monkeypatch.setattr(nodes, "AGENT_TOOLS", {"pre_sale": [_FakeSearchTool()]})
    monkeypatch.setattr(nodes.settings, "ai_api_key", "test-key")
    monkeypatch.setattr(_db, "async_session", _FakeSessionFactory())

    out = await nodes.run_agent_tool_loop(
        {"messages": [HumanMessage(content="随便看看")], "user_id": "7", "tenant_id": "1"},
        "pre_sale",
    )
    assert "params" not in out["trace"]["tool_calls"][0]
