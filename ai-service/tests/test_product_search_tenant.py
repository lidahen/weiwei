"""L1 商品读查询的租户过滤测试：tid>0 过滤、tid=0 不过滤（零侵入回滚）、
L0-L3 全部变体与 detail / estimate_price 商品行查询同样必须带过滤。"""
import json

import pytest

from app.services import product_search
from app.services.agent import tools


class _CaptureSession:
    def __init__(self):
        self.captured = []

    async def execute(self, sql, params=None):
        self.captured.append((str(sql), dict(params or {})))

        class _R:
            def fetchall(self):
                return []

            def fetchone(self):
                return None

        return _R()

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False


@pytest.mark.asyncio
async def test_search_filters_by_tenant_when_tid_positive():
    session = _CaptureSession()
    await product_search.search_products(db=session, keyword="沙发", tenant_id=3)
    sql, params = session.captured[0]
    assert "p.tenant_id = :tid" in sql
    assert params["tid"] == 3


@pytest.mark.asyncio
async def test_search_no_tenant_filter_when_tid_missing():
    session = _CaptureSession()
    await product_search.search_products(db=session, keyword="沙发")
    sql, _params = session.captured[0]
    assert "tenant_id" not in sql, "tid 缺失时不过滤（零侵入回滚）"


@pytest.mark.asyncio
async def test_l3_fallback_respects_tenant():
    """多词 keyword + condition 使 build_search_variants 产生 L0-L3 全部四个变体，
    每一级都必须带租户过滤——否则跨租户商品/热门会推给用户。"""
    session = _CaptureSession()
    await product_search.search_products(
        db=session, keyword="不存在的商品 沙发 xyz", condition="95新", tenant_id=3)
    assert len(session.captured) >= 4, "应产生 L0-L3 全部四个变体"
    for sql, params in session.captured:
        assert "p.tenant_id = :tid" in sql, "L0-L3 全部变体都必须带租户过滤"
        assert params["tid"] == 3


@pytest.mark.asyncio
async def test_get_product_detail_filters_by_tenant(monkeypatch):
    """detail 工具的商品行 SQL 必须带 tenant_id 过滤，且参数值来自注入的 tenant_id。"""
    captured = {}

    class _FakeSession:
        async def execute(self, sql, params=None):
            captured["sql"] = str(sql)
            captured["params"] = dict(params or {})

            class _R:
                def fetchone(self):
                    return None

            return _R()

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

    monkeypatch.setattr(tools, "async_session", lambda: _FakeSession())
    out = json.loads(await tools.get_product_detail.ainvoke(
        {"product_id": 100, "tenant_id": "3"}))
    # fetchone 返回 None → 走"不存在或已下架"分支，正好短路、无需 mock 后续
    assert "error" in out
    assert "p.tenant_id = :tid" in captured["sql"]
    assert captured["params"]["tid"] == 3


@pytest.mark.asyncio
async def test_get_product_detail_no_filter_when_tid_missing(monkeypatch):
    """对照组：不注入 tenant_id 时 SQL 不含 tenant_id 条件（零侵入回滚语义）。"""
    captured = {}

    class _FakeSession:
        async def execute(self, sql, params=None):
            captured["sql"] = str(sql)
            captured["params"] = dict(params or {})

            class _R:
                def fetchone(self):
                    return None

            return _R()

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

    monkeypatch.setattr(tools, "async_session", lambda: _FakeSession())
    out = json.loads(await tools.get_product_detail.ainvoke({"product_id": 100}))
    assert "error" in out
    assert "tenant_id" not in captured["sql"]


@pytest.mark.asyncio
async def test_estimate_price_filters_by_tenant(monkeypatch):
    """estimate_price 的商品行查询同样必须带 tenant_id 过滤。
    fetchone 返回 None → 走"不存在或已下架"短路，无需 mock 估价链路。"""
    captured = {}

    class _FakeSession:
        async def execute(self, sql, params=None):
            captured["sql"] = str(sql)
            captured["params"] = dict(params or {})

            class _R:
                def fetchone(self):
                    return None

            return _R()

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

    monkeypatch.setattr(tools, "async_session", lambda: _FakeSession())
    out = json.loads(await tools.estimate_price.ainvoke(
        {"product_id": 100, "tenant_id": "3"}))
    assert "error" in out
    assert "tenant_id = :tid" in captured["sql"]
    assert captured["params"]["tid"] == 3


@pytest.mark.asyncio
async def test_search_tool_passes_tenant():
    """工具层：search_products 工具把注入的 tenant_id 透传给 service。"""
    called = {}

    async def _fake_search(db, **kwargs):
        called.update(kwargs)
        return {"products": [], "search_note": ""}

    from unittest.mock import patch
    with patch.object(product_search, "search_products", _fake_search):
        await tools.search_products.ainvoke({"keyword": "沙发", "tenant_id": "3"})
    assert called.get("tenant_id") == 3
