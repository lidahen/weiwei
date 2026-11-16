import json
import logging
from datetime import datetime
from typing import AsyncGenerator, List, Optional

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import async_session, get_db
from app.models.ai_chat_history import AiChatHistory
from app.models.ai_doc_slice import AiDocSlice
from app.models.ai_knowledge import AiKnowledge
from app.schemas.chat import ChatMsg
from app.schemas.common import ApiResponse
from app.services.llm_service import chat, chat_stream
from app.services.agent.llm import _extract_usage
from app.services.rag_service import search as rag_search
from app.services.quota_service import QuotaExceededException, check_and_count, log_usage
from app.services.usage_service import record_usage

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai/chat", tags=["AI对话"])


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _sse(obj: dict) -> str:
    return "data: {}\n\n".format(json.dumps(obj, ensure_ascii=False))


def _get_llm():
    """知识库 RAG 对话的 LLM（与导购 Agent 同一套取值：优先 ai_model 表默认 LLM）。"""
    from app.services.model_resolver import build_chat_llm

    return build_chat_llm(temperature=settings.ai_temperature)


def _build_rag_system_prompt(kb: AiKnowledge, contexts: List[str]) -> str:
    """构建知识库问答的 System Prompt。"""
    welcome = kb.welcome_msg or f"你是「{kb.knowledge_name}」知识库的智能问答助手。"
    lines = [welcome, "", "请严格依据下面提供的知识库内容回答用户问题：", "---"]
    if contexts:
        for i, c in enumerate(contexts, 1):
            lines.append(f"[资料{i}]\n{c}")
    else:
        lines.append("（未检索到相关知识内容，请如实告知用户未找到答案，不要编造。）")
    lines.append("---")
    lines.append("回答要求：\n1. 优先引用资料原文作答；\n2. 资料不足时如实说明；\n3. 使用简体中文，回答简洁。")
    return "\n".join(lines)


def _tenant_id_from(request) -> int:
    """Java 代理层透传的租户 ID（X-Tenant-Id，来自 TenantFilter 上下文）；无则 0。"""
    try:
        return int(request.headers.get("X-Tenant-Id") or 0)
    except (TypeError, ValueError):
        return 0


async def _check_quota(tenant_id: int) -> None:
    """聊天入口的事前配额检查：与 /ai/assist/* 共用 ai_usage_daily 单一账本。

    背景（配额统一改造）：原 Java 侧 AiQuotaChecker 在 Redis（db1）按日 INCR 独立计数，
    与 Python 侧 ai_usage_daily 形成两套互不相通的每日额度池，quota_ai_call_day 实际被
    翻倍。现统一收口到 quota_service.check_and_count（ai_usage_daily 租户 × 自然日），
    聊天预计 +1 次，成功调用结束后仅补记 token（calls=0），避免双计。

    - 超限/套餐过期抛 QuotaExceededException（路由层转 code=429 友好错误，与 assist 一致）；
    - 无租户上下文（X-Tenant-Id 缺失 → 0）时 check_and_count 内部跳过；
    - 独立短会话执行，不依赖请求级 get_db 会话。
    """
    from app.database import async_session

    async with async_session() as db:
        await check_and_count(db, tenant_id)


def _done_usage(raw_event: str) -> dict:
    """从 SSE done 事件行解析 usage（自有协议，解析失败返回空）。"""
    line = (raw_event or "").strip()
    if line.startswith("data: "):
        try:
            payload = json.loads(line[len("data: "):])
            if payload.get("type") == "done":
                return payload.get("usage") or {}
        except json.JSONDecodeError:
            pass
    return {}


async def _record_after_stream(gen, tenant_id: int, feature: str = "chat", user_id: str = ""):
    """透传流式事件，结束时按 done 事件的 usage 落账（旁路，不阻塞主流程）。

    calls 次数已由 _check_quota 事前计入 ai_usage_daily，此处只补记 token（calls=0）
    避免双计；同时落一条 ai_usage_log 明细（feature=chat/rag-chat），失败只告警。
    """
    usage: dict = {}
    try:
        async for event in gen:
            if isinstance(event, str) and '"done"' in event:
                usage = _done_usage(event)
            yield event
    finally:
        if tenant_id > 0:
            await record_usage(
                tenant_id, calls=0,
                prompt_tokens=usage.get("promptTokens") or 0,
                completion_tokens=usage.get("completionTokens") or 0,
            )
            await log_usage(tenant_id, user_id=user_id, feature=feature,
                            model_name=settings.ai_model, task_type="text")


@router.post("")
async def chat_endpoint(request: Request, body: dict):
    """统一聊天入口，按请求体区分两种模式：

    - {"messages": [...], "sessionId": "...", "userId": "...", "resume": "..."} → 商品导购聊天（AiGuideController 也调用此模式）
    - {"knowledgeId": ..., "content": ...} → 知识库 RAG 聊天（SSE 流式）

    两种模式都先走 _check_quota（ai_usage_daily 单一账本），超限返回 code=429 友好错误
    （与 /ai/assist/* 一致；导购流式模式下由 Java 代理层转为 SSE error 事件透传前端）。
    """
    tenant_id = _tenant_id_from(request)
    if body.get("messages"):
        messages = [ChatMsg(**m) for m in body["messages"]]
        if not messages:
            return ApiResponse(code=400, msg="messages 不能为空")
        # AI 导购个性化：Java 注入的用户兴趣画像（interestHint）显式入参，
        # 由 runner 写入 state.interest_hint 注入人设——不再伪造成 role=system 消息
        # （那种做法在增量分支会被丢弃，且 system 角色已由 _to_lc_messages 统一丢弃）
        interest_hint = body.get("interestHint") or body.get("interest_hint")
        try:
            await _check_quota(tenant_id)
        except QuotaExceededException as e:
            return ApiResponse(code=429, msg=str(e))
        user_id = body.get("userId") or body.get("user_id") or ""
        knowledge_id = body.get("knowledgeId") or body.get("knowledge_id")
        session_id = body.get("sessionId") or body.get("session_id") or None
        resume = body.get("resume") or None
        result = await chat(messages, user_id=user_id, knowledge_id=knowledge_id,
                            session_id=session_id, resume=resume, tenant_id=str(tenant_id or ""),
                            interest_hint=str(interest_hint or ""))
        usage = result.usage or {}
        # calls 次数已由 _check_quota 事前计入，此处只补记 token，避免双计
        await record_usage(tenant_id, calls=0,
                           prompt_tokens=usage.get("promptTokens") or 0,
                           completion_tokens=usage.get("completionTokens") or 0)
        await log_usage(tenant_id, user_id=str(user_id), feature="chat",
                        model_name=settings.ai_model, task_type="text")
        return ApiResponse(data=result.model_dump())
    if body.get("knowledgeId"):
        try:
            await _check_quota(tenant_id)
        except QuotaExceededException as e:
            return ApiResponse(code=429, msg=str(e))
        return StreamingResponse(
            _record_after_stream(_rag_chat_stream(body), tenant_id, feature="rag-chat"),
            media_type="text/event-stream",
        )
    return ApiResponse(code=400, msg="参数错误：缺少 messages 或 knowledgeId")


@router.post("/stream")
async def chat_stream_endpoint(request: Request, body: dict):
    if not body.get("messages"):
        return ApiResponse(code=400, msg="messages 不能为空")
    messages = [ChatMsg(**m) for m in body["messages"]]
    # AI 导购个性化：用户兴趣画像显式入参（见 chat_endpoint 注释）
    interest_hint = body.get("interestHint") or body.get("interest_hint")
    user_id = body.get("userId") or body.get("user_id") or ""
    knowledge_id = body.get("knowledgeId") or body.get("knowledge_id")
    session_id = body.get("sessionId") or body.get("session_id") or None
    resume = body.get("resume") or None
    tenant_id = _tenant_id_from(request)
    try:
        await _check_quota(tenant_id)
    except QuotaExceededException as e:
        # 返回 JSON（非 SSE）：Java 代理层（AiGuideController/AiChatController）检测到
        # 非 text/event-stream 响应后转为 {"type":"error"} SSE 事件透传前端
        return ApiResponse(code=429, msg=str(e))
    return StreamingResponse(
        _record_after_stream(
            chat_stream(messages, user_id=user_id, knowledge_id=knowledge_id,
                        session_id=session_id, resume=resume, tenant_id=str(tenant_id or ""),
                        interest_hint=str(interest_hint or "")),
            tenant_id, feature="chat", user_id=str(user_id),
        ),
        media_type="text/event-stream",
    )


async def _rag_chat_stream(body: dict) -> AsyncGenerator[str, None]:
    """知识库 RAG 聊天（SSE 流式）。

    流程：向量检索相关切片 → 组装上下文与历史 → LLM 流式回答 → 落库聊天历史。
    """
    knowledge_id = body.get("knowledgeId")
    content = (body.get("content") or "").strip()
    carry_context = bool(body.get("carryContextFlag", False))

    if not knowledge_id or not content:
        yield _sse({"type": "text", "content": "参数错误：缺少知识库或问题"})
        return
    if not settings.ai_api_key:
        yield _sse({"type": "text", "content": "AI 服务未配置，请先在 .env 中设置 AI_API_KEY"})
        return

    async with async_session() as db:
        kb = await db.get(AiKnowledge, int(knowledge_id))
        if not kb or kb.del_flag != 0:
            yield _sse({"type": "text", "content": "知识库不存在或已删除"})
            return

        top_k = kb.top_k or 3
        score = kb.score if kb.score is not None else 0.5
        multi_round = kb.multi_round or 5

        # 1. 向量检索相关切片
        hits = await rag_search(db, int(knowledge_id), content, top_k, score)
        contexts: List[str] = []
        if hits:
            ids = [h["slice_id"] for h in hits]
            result = await db.execute(
                select(AiDocSlice).where(AiDocSlice.slice_id.in_(ids))
            )
            slice_map = {int(s.slice_id): s for s in result.scalars().all()}
            contexts = [
                slice_map[i].content
                for i in ids
                if i in slice_map and slice_map[i].content
            ]

        # 2. 历史消息（可选）
        history_msgs: List[AiChatHistory] = []
        if carry_context and multi_round > 0:
            res = await db.execute(
                select(AiChatHistory)
                .where(AiChatHistory.knowledge_id == int(knowledge_id))
                .order_by(AiChatHistory.msg_id.desc())
                .limit(multi_round * 2)
            )
            history_msgs = list(reversed(res.scalars().all()))

        # 3. 落库用户消息
        db.add(
            AiChatHistory(
                knowledge_id=int(knowledge_id),
                session_id=None,
                role="USER",
                content=content,
                create_time=now_str(),
            )
        )
        await db.commit()

        # 4. 组装消息并流式调用 LLM
        from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

        lc_messages = [SystemMessage(content=_build_rag_system_prompt(kb, contexts))]
        for m in history_msgs:
            if (m.role or "").upper() == "USER":
                lc_messages.append(HumanMessage(content=m.content))
            else:
                lc_messages.append(AIMessage(content=m.content))
        lc_messages.append(HumanMessage(content=content))

        full_reply = ""
        merged_chunk = None
        try:
            llm = _get_llm()
            async for chunk in llm.astream(lc_messages):
                merged_chunk = chunk if merged_chunk is None else merged_chunk + chunk
                piece = chunk.content or ""
                if not piece:
                    continue
                full_reply += piece
                yield _sse({"type": "text", "content": piece})
        except Exception as e:
            logger.error("rag chat error: %s", e)
            from app.services.llm_service import _diagnose_llm_error
            yield _sse(
                {
                    "type": "text",
                    "content": "\n\n[AI 服务调用失败：" + _diagnose_llm_error(e) + "]",
                }
            )

        # 4.5 收尾 done 事件（含 token 用量）：_record_after_stream 解析后落账 ai_usage_daily
        usage_meta = _extract_usage(merged_chunk)
        if usage_meta:
            yield _sse({
                "type": "done",
                "usage": {
                    "promptTokens": usage_meta.get("input_tokens") or 0,
                    "completionTokens": usage_meta.get("output_tokens") or 0,
                },
            })

        # 5. 落库 AI 回复
        if full_reply:
            db.add(
                AiChatHistory(
                    knowledge_id=int(knowledge_id),
                    session_id=None,
                    role="ASSISTANT",
                    content=full_reply,
                    create_time=now_str(),
                )
            )
            await db.commit()


@router.get("/history/page")
async def history_page(
    knowledgeId: int,
    pageNum: int = 1,
    pageSize: int = 10,
    current: Optional[int] = None,
    db: AsyncSession = Depends(get_db),
):
    """分页查询某知识库的聊天历史（第 1 页为最新，按时间正序返回）。"""
    page = current or pageNum or 1
    size = max(1, min(pageSize or 10, 100))
    stmt = select(AiChatHistory).where(AiChatHistory.knowledge_id == knowledgeId)
    total = await db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = (
        (
            await db.execute(
                stmt.order_by(AiChatHistory.msg_id.desc())
                .offset((page - 1) * size)
                .limit(size)
            )
        )
        .scalars()
        .all()
    )
    records = [
        {
            "msgId": r.msg_id,
            "knowledgeId": r.knowledge_id,
            "sessionId": r.session_id,
            "role": (r.role or "").upper(),
            "content": r.content,
            "createTime": r.create_time,
        }
        for r in reversed(rows)
    ]
    return ApiResponse(data={"records": records, "total": total or 0})


@router.post("/tts")
async def tts(body: dict):
    """TTS 语音合成：{text, voice?, speed?} → audio/mpeg 二进制。

    失败时返回 ApiResponse JSON（前端按响应 Content-Type 区分）。
    """
    from fastapi.responses import Response

    from app.services.tts_service import TTSError, synthesize

    text = (body.get("text") or "").strip()
    if not text:
        return ApiResponse(code=400, msg="text 不能为空")
    # 超长截断，避免上游直接拒绝整个请求
    if len(text) > settings.tts_max_chars:
        text = text[: settings.tts_max_chars]

    try:
        audio = await synthesize(text, voice=body.get("voice"), speed=body.get("speed"))
    except TTSError as e:
        return ApiResponse(code=500, msg=str(e))
    except Exception as e:
        logger.error("tts unexpected error: %s", e, exc_info=True)
        return ApiResponse(code=500, msg="语音合成失败，请稍后重试")

    return Response(content=audio, media_type="audio/mpeg")
