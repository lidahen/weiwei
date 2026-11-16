"""Agent 包内 LLM 工厂与错误诊断（从 llm_service 迁入，避免循环导入）。"""
from typing import List, Optional

from langchain_openai import ChatOpenAI

from app.config import settings


def _diagnose_llm_error(e: Exception) -> str:
    """从异常中提取 LLM 调用诊断信息，避免把根因吞成通用文案。"""
    import httpx

    code = None
    body = None

    if isinstance(e, httpx.HTTPStatusError):
        code = e.response.status_code
        body = e.response.text
    elif hasattr(e, "response") and e.response is not None:
        try:
            code = getattr(e.response, "status_code", None) or getattr(
                e.response, "status", None
            )
        except Exception:
            pass
        if body is None:
            try:
                body = e.response.text
            except Exception:
                pass

    parts: List[str] = []
    if code is not None:
        parts.append(f"HTTP {code}")
    if body:
        stripped = body.strip()
        is_html = stripped.lower().startswith("<")
        snippet = stripped[:160].replace("\n", " ")
        if is_html:
            parts.append(f"响应为 HTML 页面（请求路径/鉴权可能错误）: {snippet}...")
        else:
            parts.append(f"响应内容: {snippet}...")
    if not parts:
        parts.append(type(e).__name__ + ": " + str(e))

    return " | ".join(parts)


def _get_llm() -> ChatOpenAI:
    """导购 Agent 的对话 LLM。

    base_url / api_key / model 由 model_resolver 统一提供：优先读 ai_model 表的
    LLM 默认模型（启动时预热、TTL 60s），读不到则回退 settings。
    保持同步签名，避免改动 LangGraph 节点里的调用点。
    """
    from app.services.model_resolver import build_chat_llm

    return build_chat_llm(temperature=settings.ai_temperature)


async def ainvoke_or_stream(llm, messages):
    """有 token 汇时走 astream 逐段推送最终回答文本，否则普通 ainvoke。

    返回 (AIMessage, usage_meta)：usage_meta 为 {input_tokens, output_tokens} 或 None。
    工具决策迭代同样走 astream——tool_call 响应的 text content 为空，不会推给用户。
    """
    from app.services.agent.stream import current_sink

    sink = current_sink()
    if sink is None:
        resp = await llm.ainvoke(messages)
        return resp, _extract_usage(resp)

    final = None
    async for chunk in llm.astream(messages):
        if final is None:
            final = chunk
        else:
            final = final + chunk
        text = getattr(chunk, "content", None)
        if isinstance(text, str) and text:
            sink.emit(text)
        elif isinstance(text, list):
            # 多模态 content 结构，取纯文本段
            for part in text:
                if isinstance(part, dict) and part.get("type") == "text" and part.get("text"):
                    sink.emit(part["text"])
    usage = _extract_usage(final)
    if usage:
        sink.set_usage(usage.get("input_tokens", 0), usage.get("output_tokens", 0))
    return final, usage


def _extract_usage(message) -> Optional[dict]:
    """从 AIMessage/AIMessageChunk 提取 token 用量（OpenAI 兼容响应）。"""
    if message is None:
        return None
    meta = getattr(message, "usage_metadata", None)
    if meta:
        return {
            "input_tokens": meta.get("input_tokens") or 0,
            "output_tokens": meta.get("output_tokens") or 0,
        }
    legacy = getattr(message, "response_metadata", None) or {}
    token_usage = legacy.get("token_usage") or {}
    if token_usage:
        return {
            "input_tokens": token_usage.get("prompt_tokens") or 0,
            "output_tokens": token_usage.get("completion_tokens") or 0,
        }
    return None
