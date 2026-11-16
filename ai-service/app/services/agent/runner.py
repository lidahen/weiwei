"""电商 Agent 对外入口：chat / chat_stream。

保持与旧版 llm_service.chat / chat_stream 相同的签名与 SSE 协议，
使 app/routers/chat.py 与 Java AiGuideController 无需改动。

方案 3（多轮停留）：
  - 调用方传入 session_id（=LangGraph thread_id），图内状态（messages/user_id/domain）
    由 checkpointer 跨轮保留；
  - 工具请求确认时图 interrupt 挂起，本轮返回 pending_confirm（或 SSE confirm 事件）；
    用户确认/修改后，调用方携带 session_id + resume（用户回复）再次调用，
    runner 用 Command(resume=...) 恢复图继续执行。
"""
import asyncio
import json
import logging
import time
from typing import AsyncGenerator, List, Optional

from app.config import settings
from app.schemas.chat import ChatMsg, ChatResponse, PendingConfirm, ProductCard
from app.services.agent.graph import aget_graph
from app.services.agent.llm import _diagnose_llm_error
from app.services.agent.state import INTENT_DOMAIN
from app.services.agent.stream import TokenSink, bind_sink, reset_sink
from app.services.memory import schedule_extract
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.types import Command

logger = logging.getLogger(__name__)

# 业务域意图（pre_sale / in_sale / after_sale / community）：只有真的进了业务域，
# 本轮 trace 里的 tool_calls 才是本轮产生的行为证据
_DOMAIN_INTENTS = tuple(k for k, v in INTENT_DOMAIN.items() if v != "general")


def _trace_for_extract(result: dict) -> dict:
    """只有本轮真的进了业务域才认 trace —— clarify/chitchat 轮里留的是上一轮的
    tool_calls，当成本轮行为证据会被反复重放（spec §6.4.1 第 1 道防线）。"""
    return (result.get("trace") or {}) if result.get("intent") in _DOMAIN_INTENTS else {}


def _to_camel_product(p: ProductCard) -> dict:
    """把商品卡片序列化为前端/Java ProductCardDTO 约定的 camelCase 字段。"""
    d = p.model_dump()
    return {
        "productId": d.get("product_id"),
        "title": d.get("title"),
        "cover": d.get("cover"),
        "price": d.get("price"),
        "originalPrice": d.get("original_price"),
        "condition": d.get("condition"),
        "city": d.get("city"),
        "sellerNickName": d.get("seller_nick_name"),
    }


def _to_lc_messages(messages: List[ChatMsg], system_prompt: str) -> list:
    """把 ChatMsg 列表转换为 LangChain 消息（首条注入导购 SystemMessage）。

    兼容 dict（如 {"role": "...", "content": "..."}）与非法元素（跳过），
    避免外部调用方误传字符串/裸 dict 导致图执行崩溃。

    role=system 一律丢弃：用户兴趣画像（interestHint）已提升为 state 字段
    （spec §9.3），不再借道 messages；继续转换 system 消息会让模型看到两份画像，
    且等于允许把服务端文本当用户输入注入。
    """
    lc = [SystemMessage(content=system_prompt)]
    for msg in messages:
        if isinstance(msg, dict):
            try:
                msg = ChatMsg(**msg)
            except Exception:
                continue
        if not isinstance(msg, ChatMsg) or not getattr(msg, "content", None):
            continue
        if msg.role == "assistant":
            lc.append(AIMessage(content=msg.content))
        elif msg.role == "system":
            continue
        else:
            lc.append(HumanMessage(content=msg.content))
    return lc


SYSTEM_PROMPT = (
    "你是二手交易商城的 AI 导购助手。请根据用户用自然语言描述的需求，"
    "在需要时调用工具检索在售商品、查询订单物流、处理售后，"
    "并依据真实工具返回结果用简洁友好的中文向用户推荐或解答。\n"
    "规则：\n"
    "1. 只能引用工具返回的真实商品/订单数据，绝不可臆造；\n"
    "2. 若用户提到了价格、成色、分类、城市等约束，尽量映射为工具参数；\n"
    "3. 若没有匹配的商品，请如实告知并建议用户调整需求；\n"
    "4. 回复保持简短。"
)


def _initial_input(messages: list, user_id: str, knowledge_id: Optional[int],
                   tenant_id: str = "", interest_hint: str = "") -> dict:
    """构造每轮全量执行的初始输入（无 session 时的兼容路径）。

    interest_hint 必须在这里出现：新会话首轮走的就是这条路径，
    漏掉参数等于画像"恰好在最需要它的第一轮"消失（spec §9.3）。
    """
    return {
        "messages": messages,
        "user_id": user_id,
        "tenant_id": tenant_id,
        "intent": "",
        "confidence": 0.0,
        "domain": "general",
        "products": [],
        "tool_results": [],
        "retry_count": 0,
        "error": "",
        "final_reply": "",
        "finished": False,
        "human_handoff": False,
        "knowledge_id": knowledge_id,
        "rag_context": "",
        "interest_hint": interest_hint,
        "history_summary": "",
        "summary_upto": 0,
    }


def _extract_interrupt(result: dict) -> Optional[PendingConfirm]:
    """从图执行结果中提取 interrupt 挂起信息（无则返回 None）。"""
    try:
        interrupts = result.get("__interrupt__") or ()
        for it in interrupts:
            value = getattr(it, "value", None) or {}
            if isinstance(value, dict) and value.get("type") == "confirm":
                return PendingConfirm(
                    type="confirm",
                    tool=value.get("tool"),
                    message=value.get("message") or "",
                )
    except Exception:
        pass
    return None


def _log_agent_trace(result: dict, session_id: str, user_id: str,
                     usage: Optional[dict], latency_ms: float) -> None:
    """输出单次运行的结构化 trace（单行 JSON，便于 grep/采集）。

    汇总：意图路由结果、工具调用明细（nodes 采集）、LLM 迭代数与耗时、
    token 用量、总延迟。trace 缺失或序列化失败绝不影响主流程。
    """
    try:
        trace = result.get("trace") or {}
        payload = {
            "event": "agent_trace",
            "session_id": session_id,
            "user_id": user_id,
            "intent": result.get("intent") or "",
            "confidence": result.get("confidence"),
            "domain": result.get("domain") or "",
            "finished": result.get("finished"),
            "error": result.get("error") or "",
            "llm_calls": trace.get("llm_calls"),
            "llm_ms": trace.get("llm_ms"),
            "tool_calls": trace.get("tool_calls") or [],
            "final_reply_len": len(result.get("final_reply") or ""),
            "product_count": len(result.get("products") or []),
            "usage": usage,
            "latency_ms": round(latency_ms),
            "interrupted": bool(result.get("__interrupt__")),
        }
        logger.info("agent_trace %s", json.dumps(payload, ensure_ascii=False, default=str))
    except Exception:
        logger.debug("agent_trace serialization failed", exc_info=True)


async def _ainvoke_with_deadline(graph, inp, config: dict) -> dict:
    """整图执行带总超时（防 LLM/工具/checkpointer 整体卡死导致 SSE 无限挂起）。

    超时返回结构化兜底结果（不抛异常），由上层按正常失败路径回复用户；
    interrupt 挂起是正常返回，不受影响。
    """
    thread_id = (config.get("configurable") or {}).get("thread_id", "")
    try:
        return await asyncio.wait_for(graph.ainvoke(inp, config=config),
                                      timeout=settings.agent_graph_timeout)
    except asyncio.TimeoutError:
        logger.error("agent graph timeout after %ss (thread=%s)",
                     settings.agent_graph_timeout, thread_id)
        return {
            "final_reply": "抱歉，本次处理耗时过长已中止。请稍后重试，或换个问法试试。",
            "finished": True,
            "error": "graph_timeout",
            "intent": "", "confidence": 0.0, "domain": "",
            "products": [], "trace": {"fatal": "graph_timeout"},
        }


def _graph_config(session_id: str) -> dict:
    """图执行 config：thread_id + recursion_limit（防死循环，设计文档 6.6 要求放 config）。"""
    return {
        "configurable": {"thread_id": session_id},
        "recursion_limit": settings.agent_recursion_limit,
    }


async def _run_graph(
    graph,
    messages: List[ChatMsg],
    user_id: str,
    knowledge_id: Optional[int],
    session_id: Optional[str],
    resume: Optional[str],
    tenant_id: str = "",
    interest_hint: str = "",
) -> dict:
    """执行 LangGraph 主图。

    session_id 非空时走方案 3（checkpointer + interrupt/resume）：
      - resume 非空：Command(resume=...) 恢复被 interrupt 挂起的图；
      - 该 thread 已有历史（checkpointer 命中）：只上送最新一条用户消息做增量，
        user_id/对话历史等由 checkpoint 跨轮保留（避免消息重复累加）；
      - 新 thread / 无 session_id：全量执行（旧行为，每轮无跨轮状态）。

    interest_hint 三条路径都要带（spec §9.3）：增量分支丢弃它会让 checkpoint 里
    第 1 轮那份旧画像永久生效，而 user_interest_stat 每 30 分钟就重建一次。
    """
    lc_messages = _to_lc_messages(messages, SYSTEM_PROMPT)
    # 图挂了 checkpointer，thread_id 必填：无 session 的调用给临时 thread（内存版、等效无跨轮状态），
    # 否则 LangGraph 直接抛 "Checkpointer requires thread_id"
    if not session_id:
        import uuid

        session_id = "transient-" + uuid.uuid4().hex
    config = _graph_config(session_id)

    if resume is not None:
        # 方案 3：恢复被 interrupt 挂起的图
        return await _ainvoke_with_deadline(graph, Command(resume=str(resume)), config)

    if session_id:
        try:
            prev = await graph.aget_state(config)
            has_history = bool(prev and prev.values and prev.values.get("messages"))
            # 检查图是否处于被 interrupt 挂起状态（next 非空=有待执行的节点）
            is_interrupted = bool(prev and prev.next)
        except Exception:
            has_history = False
            is_interrupted = False

        if is_interrupted:
            # 图被 interrupt 挂起（如等待用户确认下单），用户回复应作为 resume 恢复图
            # 而非重新从 START 执行——否则丢失 confirm_token 等上下文
            from langchain_core.messages import HumanMessage
            last_user = next(
                (m for m in reversed(messages) if getattr(m, "role", "") != "assistant"),
                None,
            )
            if last_user is not None:
                return await _ainvoke_with_deadline(
                    graph, Command(resume=str(last_user.content)), config,
                )

        if has_history:
            # 增量：只传最新用户消息，其余由 checkpointer 保留
            last_user = next(
                (m for m in reversed(messages) if getattr(m, "role", "") != "assistant"),
                None,
            )
            if last_user is None:
                return await _ainvoke_with_deadline(
                    graph,
                    _initial_input(lc_messages, user_id, knowledge_id, tenant_id, interest_hint),
                    config,
                )
            from langchain_core.messages import HumanMessage
            return await _ainvoke_with_deadline(
                graph,
                {"messages": [HumanMessage(content=last_user.content)],
                 "user_id": user_id, "knowledge_id": knowledge_id, "tenant_id": tenant_id,
                 "interest_hint": interest_hint},
                config,
            )

    return await _ainvoke_with_deadline(
        graph,
        _initial_input(lc_messages, user_id, knowledge_id, tenant_id, interest_hint),
        config,
    )


async def chat(
    messages: List[ChatMsg],
    user_id: str = "",
    knowledge_id: Optional[int] = None,
    session_id: Optional[str] = None,
    resume: Optional[str] = None,
    tenant_id: str = "",
    interest_hint: str = "",
) -> ChatResponse:
    """同步聊天：跑完整 LangGraph，返回最终回复 + 商品卡片。

    session_id 非空时走方案 3 多轮停留（checkpointer + interrupt/resume）：
      - 首次调用（无历史）：thread_id=session_id，执行图；
      - 工具确认挂起：返回 pending_confirm，前端展示确认信息；
      - 用户回复确认/修改：再次调用并携带 resume=用户回复，图从挂起点恢复。
    无 session_id 时保持旧行为（每轮全量执行，无跨轮状态）。

    interest_hint：Java 每轮注入的用户兴趣画像，经 state 字段进人设（spec §9.3）。
    """
    sink = TokenSink(queue=None)
    sink_token = bind_sink(sink)
    _t0 = time.perf_counter()
    try:
        graph = await aget_graph()
        # 尾参用关键字传递：tenant_id 与 interest_hint 同为 str 且相邻，
        # 位置实参一旦在中间插入新参数就会静默错位（人设文本会被当成租户 ID 送进租户作用域工具）
        result = await _run_graph(graph, messages, user_id, knowledge_id, session_id, resume,
                                  tenant_id=tenant_id, interest_hint=interest_hint)
        _log_agent_trace(result, session_id or "", user_id, sink.usage,
                         (time.perf_counter() - _t0) * 1000)

        pending = _extract_interrupt(result)
        if pending is not None:
            # 图被挂起等待用户确认/修改
            return ChatResponse(
                reply="",
                products=[],
                pending_confirm=PendingConfirm(
                    type="confirm",
                    tool=pending.tool,
                    message=pending.message,
                    session_id=session_id,
                ),
            )

        reply = (result.get("final_reply") or "").strip()
        products = [
            ProductCard(**p) for p in (result.get("products") or [])
            if isinstance(p, dict)
        ]
        if not reply:
            reply = "抱歉，我暂时没有理解您的需求，请换个说法试试。"
        # 长期记忆提取（spec §6.1）：异步起任务、不阻塞本轮返回。
        # session_id 传调用方原值（`or ""`）—— _run_graph 内部造的 transient id
        # 每轮一变，用它会把一次对话拆成 N 个假会话、当场凑满特质升格门槛；
        # 空串则由 run_extract 直接拒绝（宁可不提取，也不制造假会话 / 冻结计数）。
        schedule_extract(messages=messages, final_reply=reply, user_id=user_id,
                         tenant_hint=tenant_id, session_id=session_id or "",
                         trace=_trace_for_extract(result), resume=resume,
                         interrupted=False, error=str(result.get("error") or ""))
        return ChatResponse(reply=reply, products=[_to_camel_product(p) for p in products],
                            usage=sink.usage or None)
    except Exception as e:
        logger.error("agent chat error: %s", e)
        return ChatResponse(
            reply="AI 服务暂时不可用：" + _diagnose_llm_error(e),
            products=[],
        )
    finally:
        reset_sink(sink_token)


async def chat_stream(
    messages: List[ChatMsg],
    user_id: str = "",
    knowledge_id: Optional[int] = None,
    session_id: Optional[str] = None,
    resume: Optional[str] = None,
    tenant_id: str = "",
    interest_hint: str = "",
) -> AsyncGenerator[str, None]:
    """流式聊天（真流式）：SSE 事件（text 增量 + products + confirm + error + done）。

    真流式：图在后台任务执行，节点内最终回答的 LLM astream 文本增量经 TokenSink
    队列实时转发——首 token 延迟 = 首个 LLM token，而非全图执行时间。
    协议在旧版（text/products/confirm/error）基础上新增 done 收尾事件（含 usage）。
    """
    import asyncio

    from app.schemas.chat import ProductCard

    def _sse(obj: dict) -> str:
        return "data: {}\n\n".format(json.dumps(obj, ensure_ascii=False))

    graph = await aget_graph()
    queue: asyncio.Queue = asyncio.Queue()
    sink = TokenSink(queue=queue)
    sink_token = bind_sink(sink)
    _t0 = time.perf_counter()
    task = asyncio.create_task(
        _run_graph(graph, messages, user_id, knowledge_id, session_id, resume,
                   tenant_id=tenant_id, interest_hint=interest_hint)
    )
    try:
        # 边执行边推：节点内 LLM astream 的文本增量实时到达；
        # status 事件为工具执行状态提示（"正在查询…"），透传给前端展示
        # 看门狗：整图超过 agent_graph_timeout 仍未完成 → 取消任务并下发 error 事件，
        # 防止前端无限等待（长时间没有回答的场景兜底）
        deadline = time.monotonic() + settings.agent_graph_timeout + 5
        while not task.done():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                task.cancel()
                logger.error("agent chat_stream watchdog fired (thread=%s)", session_id)
                yield _sse({"type": "error", "message": "处理超时，请稍后重试或换个问法。"})
                return
            try:
                kind, payload = await asyncio.wait_for(queue.get(), timeout=min(0.2, remaining))
                yield _sse({"type": kind, "content": payload})
            except asyncio.TimeoutError:
                continue
        while not queue.empty():
            kind, payload = queue.get_nowait()
            yield _sse({"type": kind, "content": payload})

        result = task.result()
        _log_agent_trace(result, session_id or "", user_id, sink.usage,
                         (time.perf_counter() - _t0) * 1000)

        pending = _extract_interrupt(result)
        if pending is not None:
            # 图挂起：发送 confirm 事件，等待用户确认/修改
            yield _sse({"type": "confirm", "message": pending.message, "tool": pending.tool})
            return

        reply = (result.get("final_reply") or "").strip()
        products = [
            ProductCard(**p) for p in (result.get("products") or [])
            if isinstance(p, dict)
        ]
        # 长期记忆提取：放在开始 yield **之前** —— 客户端中途断连（生成器被取消）
        # 也不影响提取，且 schedule_extract 只建任务、不增加首字节延迟（spec §6.1/§6.5）。
        # session_id 口径同 chat()：只传调用方原值，transient id 不外泄。
        schedule_extract(messages=messages, final_reply=reply, user_id=user_id,
                         tenant_hint=tenant_id, session_id=session_id or "",
                         trace=_trace_for_extract(result), resume=resume,
                         interrupted=False, error=str(result.get("error") or ""))
        # 兜底：最终回答未走流式（商品汇总/模板文案等，或 reply_streamed 标志在域子图
        # 映射中丢失）时补发文本；按已推送全文判重，防止已流式回答被重复发送
        if reply and reply not in sink.emitted_text:
            for i in range(0, len(reply), 2):
                yield _sse({"type": "text", "content": reply[i:i + 2]})
        if products:
            yield _sse({"type": "products", "products": [_to_camel_product(p) for p in products]})
        yield _sse({"type": "done", "usage": sink.usage or None})
    except Exception as e:
        logger.error("agent chat_stream error: %s", e)
        yield _sse({"type": "error", "message": "AI 服务暂时不可用：" + _diagnose_llm_error(e)})
    finally:
        reset_sink(sink_token)
