"""流式 token 汇：节点内 LLM 增量 → SSE 生成器的跨任务通道。

设计约束：
- LangGraph state 会被 checkpointer 序列化，Queue 等不可序列化对象不能进 state，
  因此用 ContextVar 在同一异步任务树内传递（子任务自动继承上下文）。
- runner.chat_stream 设置 Sink(queue=...) 后执行图；节点内最终回答用 astream
  逐段推送 text 增量；非流式 chat 只采集 usage（queue=None）。
"""
import asyncio
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class TokenSink:
    """token 汇：queue 非空时推流式增量；usage 记录最终回答的 token 用量。

    队列元素为 (kind, payload) 元组：kind ∈ {"text", "status"}，
    text 为回答文本增量，status 为工具执行状态提示（如"正在为你查找商品"）。
    """

    queue: Optional[asyncio.Queue] = None
    usage: dict = field(default_factory=dict)
    emitted_chars: int = 0
    # 已推送文本全文（runner 兜底补发前判重：reply 未被流式输出过才补发）
    emitted_text: str = ""

    def _put(self, kind: str, payload: str) -> None:
        if self.queue is not None and payload:
            self.queue.put_nowait((kind, payload))

    def emit(self, text: str) -> None:
        if self.queue is not None and text:
            self.emitted_chars += len(text)
            self.emitted_text += text
            self._put("text", text)

    def emit_status(self, label: str) -> None:
        """推送工具执行状态提示（不进入 emitted_text，不影响最终回答判重）。"""
        self._put("status", label)

    def set_usage(self, prompt_tokens: int, completion_tokens: int) -> None:
        if prompt_tokens or completion_tokens:
            self.usage = {
                "promptTokens": int(prompt_tokens or 0),
                "completionTokens": int(completion_tokens or 0),
            }


_sink: ContextVar[Optional[TokenSink]] = ContextVar("ai_token_sink", default=None)


def current_sink() -> Optional[TokenSink]:
    return _sink.get()


def bind_sink(sink: Optional[TokenSink]) -> object:
    """绑定 sink，返回 reset token（调用方 finally 里 reset）。"""
    return _sink.set(sink)


def reset_sink(token: object) -> None:
    _sink.reset(token)  # type: ignore[arg-type]
