"""聊天路径配额统一单测（mock，不调外网、不连真实 MySQL）。

配额统一改造（2026-09）：聊天入口（/ai/chat messages、/ai/chat knowledgeId、/ai/chat/stream）
接入 quota_service.check_and_count（ai_usage_daily 单一账本，与 /ai/assist/* 共用额度池）：

- 超限/过期 → code=429 友好错误（与 assist 一致），且不触发 LLM；
- 流式路径超限时返回 JSON（非 SSE）——Java 代理层（AiChatController/AiGuideController）
  检测到非 text/event-stream 响应后转为 {type:"error"} 事件透传前端；
- 正常路径 → calls 已由事前检查计入 ai_usage_daily，事后仅补 token（calls=0）避免双计，
  并落 ai_usage_log 明细（feature=chat/rag-chat，与 product-copy/product-image 可区分）。
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app
from app.routers.chat import _check_quota
from app.services.quota_service import QuotaExceededException

QUOTA_MSG = "今日 AI 调用配额已用完（51/50），请升级套餐"


def _client():
    """httpx AsyncClient（直连 FastAPI app，不出网）。"""
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


class TestChatQuotaOverLimit:
    @pytest.mark.asyncio
    async def test_messages_mode_returns_429_without_llm(self):
        """/ai/chat messages 模式超限：code=429 友好错误，且不触发 agent chat。"""
        with patch("app.routers.chat._check_quota",
                   new=AsyncMock(side_effect=QuotaExceededException(QUOTA_MSG))), \
             patch("app.routers.chat.chat", new=AsyncMock()) as chat_mock:
            async with _client() as client:
                resp = await client.post(
                    "/ai/chat",
                    json={"messages": [{"role": "user", "content": "你好"}]},
                    headers={"X-Tenant-Id": "2"},
                )
        assert resp.status_code == 200  # FastAPI 层 200，业务码在 body.code（与 assist 一致）
        body = resp.json()
        assert body["code"] == 429
        assert "配额已用完" in body["msg"]
        chat_mock.assert_not_awaited()  # 超限必须在调用 LLM 之前拦截

    @pytest.mark.asyncio
    async def test_rag_mode_returns_429_json_not_sse(self):
        """/ai/chat knowledgeId 模式超限：返回 JSON（非 SSE），供 Java 代理层转 error 事件。"""
        with patch("app.routers.chat._check_quota",
                   new=AsyncMock(side_effect=QuotaExceededException(QUOTA_MSG))):
            async with _client() as client:
                resp = await client.post(
                    "/ai/chat",
                    json={"knowledgeId": 1, "content": "什么是退货政策"},
                    headers={"X-Tenant-Id": "2"},
                )
        assert "text/event-stream" not in resp.headers.get("content-type", "")
        body = resp.json()
        assert body["code"] == 429
        assert "配额已用完" in body["msg"]

    @pytest.mark.asyncio
    async def test_stream_mode_returns_429_json_not_sse(self):
        """/ai/chat/stream 超限：返回 JSON（非 SSE），且不启动 agent 流式图。"""
        with patch("app.routers.chat._check_quota",
                   new=AsyncMock(side_effect=QuotaExceededException(QUOTA_MSG))), \
             patch("app.routers.chat.chat_stream") as stream_mock:
            async with _client() as client:
                resp = await client.post(
                    "/ai/chat/stream",
                    json={"messages": [{"role": "user", "content": "你好"}]},
                    headers={"X-Tenant-Id": "2"},
                )
        assert "text/event-stream" not in resp.headers.get("content-type", "")
        body = resp.json()
        assert body["code"] == 429
        stream_mock.assert_not_called()


class TestChatQuotaPass:
    @pytest.mark.asyncio
    async def test_messages_mode_records_tokens_without_double_counting(self):
        """messages 模式正常路径：calls 已事前计入，事后仅补 token（calls=0）+ 明细 feature=chat。"""
        result = MagicMock()
        result.usage = {"promptTokens": 12, "completionTokens": 34}
        result.model_dump.return_value = {"reply": "你好"}
        with patch("app.routers.chat._check_quota", new=AsyncMock()) as check, \
             patch("app.routers.chat.chat", new=AsyncMock(return_value=result)), \
             patch("app.routers.chat.record_usage", new=AsyncMock()) as ru, \
             patch("app.routers.chat.log_usage", new=AsyncMock()) as lu:
            async with _client() as client:
                resp = await client.post(
                    "/ai/chat",
                    json={"messages": [{"role": "user", "content": "你好"}], "userId": "42"},
                    headers={"X-Tenant-Id": "2"},
                )
        assert resp.json()["code"] == 200
        check.assert_awaited_once_with(2)  # 事前检查（预计数）以租户为键
        ru.assert_awaited_once()
        assert ru.await_args.kwargs["calls"] == 0  # 关键：calls 已事前计入，不双计
        assert ru.await_args.kwargs["prompt_tokens"] == 12
        assert ru.await_args.kwargs["completion_tokens"] == 34
        lu.assert_awaited_once()
        assert lu.await_args.kwargs["feature"] == "chat"  # 与 assist 的 feature 可区分
        assert lu.await_args.kwargs["user_id"] == "42"

    @pytest.mark.asyncio
    async def test_stream_mode_records_tokens_after_done_event(self):
        """stream 模式正常路径：done 事件的 usage 被解析补记 token（calls=0），明细 feature=chat。"""

        async def _gen(*args, **kwargs):
            yield 'data: {"type": "text", "content": "你好"}\n\n'
            yield 'data: {"type": "done", "usage": {"promptTokens": 3, "completionTokens": 7}}\n\n'

        with patch("app.routers.chat._check_quota", new=AsyncMock()), \
             patch("app.routers.chat.chat_stream", new=_gen), \
             patch("app.routers.chat.record_usage", new=AsyncMock()) as ru, \
             patch("app.routers.chat.log_usage", new=AsyncMock()) as lu:
            async with _client() as client:
                resp = await client.post(
                    "/ai/chat/stream",
                    json={"messages": [{"role": "user", "content": "你好"}]},
                    headers={"X-Tenant-Id": "2"},
                )
        assert '"type": "text"' in resp.text  # 流式事件正常透传
        ru.assert_awaited_once()
        assert ru.await_args.kwargs["calls"] == 0
        assert ru.await_args.kwargs["prompt_tokens"] == 3
        assert ru.await_args.kwargs["completion_tokens"] == 7
        lu.assert_awaited_once()
        assert lu.await_args.kwargs["feature"] == "chat"


class TestCheckQuotaDelegation:
    @pytest.mark.asyncio
    async def test_delegates_to_check_and_count_with_fresh_session(self):
        """_check_quota 用独立短会话委托 check_and_count（与 assist 同一函数/账本）。"""
        db = MagicMock()
        cm = MagicMock()
        cm.__aenter__ = AsyncMock(return_value=db)
        cm.__aexit__ = AsyncMock(return_value=False)
        with patch("app.database.async_session", MagicMock(return_value=cm)), \
             patch("app.routers.chat.check_and_count", new=AsyncMock()) as cac:
            await _check_quota(2)
        cac.assert_awaited_once_with(db, 2)

    @pytest.mark.asyncio
    async def test_no_tenant_still_delegates(self):
        """无租户上下文（X-Tenant-Id 缺失 → 0）：仍委托 check_and_count（其内部跳过）。"""
        db = MagicMock()
        cm = MagicMock()
        cm.__aenter__ = AsyncMock(return_value=db)
        cm.__aexit__ = AsyncMock(return_value=False)
        with patch("app.database.async_session", MagicMock(return_value=cm)), \
             patch("app.routers.chat.check_and_count", new=AsyncMock()) as cac:
            await _check_quota(0)
        cac.assert_awaited_once_with(db, 0)
