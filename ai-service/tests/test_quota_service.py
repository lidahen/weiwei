"""用量计费（quota_service + assist 路由接入）单元测试。

全部 mock（不调外网、不连真实 MySQL），覆盖：
- check_and_count：无租户跳过 / 租户不存在跳过 / 套餐过期拒绝 / 不限(0)放行 /
  次数超限拒绝 / 计量旁路失败放行
- log_usage：落账成功 / 无租户跳过 / 落账失败只告警
- 路由接入：product-copy 超限返回 429 友好错误（不触发 LLM）；正常路径事后计量被调用
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from app.database import get_db
from app.main import app
from app.services.quota_service import (
    QuotaExceededException,
    check_and_count,
    log_usage,
    user_id_from_request,
)


def _mock_db(tenant_row="unset", calls=None):
    """构造 mock：返回 (请求级会话, fresh会话工厂)。

    - 请求级会话只应收到 sys_tenant 查询（tenant_row="unset" 表示不该查；None=租户不存在）；
    - check_and_count 读回当日计数用新会话（规避 REPEATABLE READ 快照），
      tests 中通过 patch app.database.async_session 注入，scalar 返回 calls。
    """
    db = AsyncMock()

    async def _execute(stmt, params=None):
        if "sys_tenant" in str(stmt):
            if tenant_row == "unset":
                raise AssertionError("不应查询 sys_tenant（无租户上下文应直接跳过）")
            res = MagicMock()
            res.first = MagicMock(return_value=tenant_row)
            return res
        raise AssertionError(f"请求级会话不应执行: {stmt}")

    db.execute = AsyncMock(side_effect=_execute)
    return db, _fresh_session_factory(calls)


def _fresh_session_factory(calls):
    """app.database.async_session 的 mock 工厂：execute().scalar() 返回 calls。"""
    fresh = AsyncMock()
    fresh.execute = AsyncMock(
        return_value=MagicMock(scalar=MagicMock(return_value=calls))
    )
    cm = MagicMock()
    cm.__aenter__ = AsyncMock(return_value=fresh)
    cm.__aexit__ = AsyncMock(return_value=False)
    return MagicMock(return_value=cm)


def _unwrapped():
    """httpx AsyncClient（直连 FastAPI app，不出网）。"""
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


def _override_db(db):
    """用 mock 会话覆盖 get_db 依赖（与 test_image.py 同款异步生成器写法）。"""

    async def _get_db_override():
        yield db

    app.dependency_overrides[get_db] = _get_db_override


class TestCheckAndCount:
    @pytest.mark.asyncio
    async def test_no_tenant_skips(self):
        """无租户上下文（X-Tenant-Id 缺失 → 0）：直接放行，不查库不计数。"""
        db, _ = _mock_db()
        with patch("app.services.quota_service.record_usage", new=AsyncMock()) as ru:
            await check_and_count(db, 0)
        db.execute.assert_not_awaited()
        ru.assert_not_awaited()

    @pytest.mark.asyncio
    async def test_tenant_missing_skips(self):
        """租户不存在：放行（对齐 Java billing 未识别租户跳过）。"""
        db, _ = _mock_db(tenant_row=None)
        with patch("app.services.quota_service.record_usage", new=AsyncMock()):
            await check_and_count(db, 99)

    @pytest.mark.asyncio
    async def test_expired_tenant_rejected(self):
        """套餐过期：拒绝并返回友好文案。"""
        db, _ = _mock_db(tenant_row=(0, __import__("datetime").datetime(2020, 1, 1)))
        with patch("app.services.quota_service.record_usage", new=AsyncMock()):
            with pytest.raises(QuotaExceededException) as ei:
                await check_and_count(db, 1)
        assert "过期" in str(ei.value)

    @pytest.mark.asyncio
    async def test_unlimited_quota_passes(self):
        """quota_ai_call_day = 0（不限，Pro）：放行，但仍计数供用量展示。"""
        db, _ = _mock_db(tenant_row=(0, None))
        with patch("app.services.quota_service.record_usage", new=AsyncMock()) as ru:
            await check_and_count(db, 1)
        ru.assert_awaited_once_with(1, calls=1)

    @pytest.mark.asyncio
    async def test_under_limit_passes(self):
        """次数未超限（5/6）：放行。"""
        db, factory = _mock_db(tenant_row=(6, None), calls=5)
        with patch("app.services.quota_service.record_usage", new=AsyncMock()), \
             patch("app.database.async_session", factory):
            await check_and_count(db, 2)

    @pytest.mark.asyncio
    async def test_over_limit_rejected(self):
        """次数超限（配额 5，计数 51 → 51/5）：拒绝并带配额数字的友好文案。"""
        db, factory = _mock_db(tenant_row=(5, None), calls=51)
        with patch("app.services.quota_service.record_usage", new=AsyncMock()), \
             patch("app.database.async_session", factory):
            with pytest.raises(QuotaExceededException) as ei:
                await check_and_count(db, 2)
        assert "配额已用完" in str(ei.value)
        assert "51/5" in str(ei.value)

    @pytest.mark.asyncio
    async def test_metering_failure_fails_open(self):
        """计量旁路失败（读回 calls 为 None）：检查退化为放行，不误杀。"""
        db, factory = _mock_db(tenant_row=(5, None), calls=None)
        with patch(
            "app.services.quota_service.record_usage",
            new=AsyncMock(side_effect=RuntimeError("db down")),
        ), patch("app.database.async_session", factory):
            await check_and_count(db, 2)


class TestLogUsage:
    @pytest.mark.asyncio
    async def test_inserts_row(self):
        mock_session = AsyncMock()
        cm = MagicMock()
        cm.__aenter__ = AsyncMock(return_value=mock_session)
        cm.__aexit__ = AsyncMock(return_value=False)
        with patch("app.database.async_session", MagicMock(return_value=cm)):
            await log_usage(1, user_id="42", feature="product-copy",
                            model_id=3, model_name="agnes-2.5-flash", task_type="text")
        mock_session.execute.assert_awaited_once()
        mock_session.commit.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_no_tenant_skips(self):
        with patch("app.database.async_session") as factory:
            await log_usage(0, feature="product-copy")
        factory.assert_not_called()

    @pytest.mark.asyncio
    async def test_db_error_swallowed(self):
        mock_session = AsyncMock()
        mock_session.execute = AsyncMock(side_effect=RuntimeError("insert failed"))
        cm = MagicMock()
        cm.__aenter__ = AsyncMock(return_value=mock_session)
        cm.__aexit__ = AsyncMock(return_value=False)
        with patch("app.database.async_session", MagicMock(return_value=cm)):
            await log_usage(1, feature="product-copy")  # 不应抛出


class TestUserIdFromRequest:
    def test_header_priority(self):
        req = MagicMock(headers={"X-User-Id": "42"})
        body = MagicMock(user_id="99")
        assert user_id_from_request(req, body) == "42"

    def test_body_fallback(self):
        req = MagicMock(headers={})
        body = MagicMock(user_id="99")
        assert user_id_from_request(req, body) == "99"

    def test_missing_empty(self):
        req = MagicMock(headers={})
        assert user_id_from_request(req, None) == ""


class TestAssistRouterQuota:
    @pytest.mark.asyncio
    async def test_product_copy_over_limit_returns_429(self):
        """product-copy 超限：返回 code=429 + 友好错误，且不触发 LLM 生成。"""
        db, factory = _mock_db(tenant_row=(5, None), calls=51)
        _override_db(db)
        try:
            with patch("app.services.quota_service.record_usage", new=AsyncMock()), \
                 patch("app.database.async_session", factory), \
                 patch("app.routers.assist.generate_product_copy", new=AsyncMock()) as gen:
                async with _unwrapped() as client:
                    resp = await client.post(
                        "/ai/assist/product-copy",
                        json={"productName": "测试商品"},
                        headers={"X-Tenant-Id": "2"},
                    )
            assert resp.status_code == 200  # FastAPI 层 200，业务码在 body.code
            body = resp.json()
            assert body["code"] == 429
            assert "配额已用完" in body["msg"]
            gen.assert_not_awaited()  # 超限必须在生成之前拦截
        finally:
            app.dependency_overrides.clear()

    @pytest.mark.asyncio
    async def test_product_copy_success_logs_usage(self):
        """product-copy 正常路径：生成成功后事后计量（ai_usage_log 落账被调用）。"""
        model = MagicMock(model_id=3, model_name="agnes-2.5-flash")
        db = AsyncMock()
        _res = MagicMock()
        _res.scalar_one_or_none.return_value = model
        _res.scalars.return_value.all.return_value = [model]
        db.execute = AsyncMock(return_value=_res)
        _override_db(db)
        try:
            with patch(
                "app.routers.assist.generate_product_copy",
                new=AsyncMock(return_value=(["标题A", "标题B"], "描述内容")),
            ), patch("app.routers.assist._build_chat_llm", new=MagicMock()), \
               patch("app.routers.assist.log_usage", new=AsyncMock()) as log_u:
                async with _unwrapped() as client:
                    resp = await client.post(
                        "/ai/assist/product-copy",
                        json={"productName": "测试商品", "userId": "42"},
                    )
            body = resp.json()
            assert body["code"] == 200
            assert body["data"]["titles"] == ["标题A", "标题B"]
            log_u.assert_awaited_once()
            kwargs = log_u.await_args.kwargs
            assert kwargs["feature"] == "product-copy"
            assert kwargs["user_id"] == "42"
            assert kwargs["task_type"] == "text"
        finally:
            app.dependency_overrides.clear()
