"""下单前风险检查测试：三项命中/不命中/单项失败跳过/NULL 跳过/confirm_order 集成。

锁定四条不变量：
  1. 三项检查独立降级——任何一项查询抛异常，其余项照常返回，整体不抛异常；
  2. login_date 为 NULL（未知）跳过活跃度提示；
  3. 无风险返回空列表；
  4. confirm_order 首次确认话术必须带出风险文案（且不阻塞确认流程）。
"""
import json
from datetime import datetime, timedelta

import pytest

from app.services.agent import tools


class _RouteResult:
    def __init__(self, row):
        self._row = row

    def fetchone(self):
        return self._row


class _RouteSession:
    """假 session：按 SQL 关键字路由到预设行，未命中返回 None 行（模拟查无数据）。"""

    def __init__(self, route):
        self._route = route

    async def execute(self, sql, params=None):
        s = str(sql)
        for key, row in self._route.items():
            if key in s:
                return _RouteResult(row)
        return _RouteResult(None)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False


class _FailSomeSession(_RouteSession):
    """对指定关键字的 SQL 抛异常（模拟单表故障）。"""

    def __init__(self, route, fail_key):
        super().__init__(route)
        self._fail_key = fail_key

    async def execute(self, sql, params=None):
        if self._fail_key in str(sql):
            raise RuntimeError("db down")
        return await super().execute(sql, params)


@pytest.mark.asyncio
async def test_bargain_pending_detected():
    session = _RouteSession({"trade_bargain": (320.0,)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("议价进行中" in r and "320" in r for r in risks)


@pytest.mark.asyncio
async def test_dispute_count_detected():
    session = _RouteSession({"trade_dispute": (2,)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("2 笔纠纷未结" in r for r in risks)


@pytest.mark.asyncio
async def test_seller_inactive_detected():
    # 45 天 + 5 分钟：扣除微秒取整后 days 恰为 45（直接减 45 天会得 44）
    session = _RouteSession({"sys_user": (datetime.now() - timedelta(days=45, minutes=5),)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("45 天未登录" in r for r in risks)


@pytest.mark.asyncio
async def test_no_risks_returns_empty():
    session = _RouteSession({})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert risks == []


@pytest.mark.asyncio
async def test_login_date_null_skipped():
    session = _RouteSession({"sys_user": (None,)})
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert not any("未登录" in r for r in risks), "login_date 未知时不得提示活跃度风险"


@pytest.mark.asyncio
async def test_partial_failure_skips_item():
    session = _FailSomeSession({"trade_bargain": (320.0,)}, fail_key="trade_dispute")
    risks = await tools._pre_order_risk_check(session, 100, 7, 7)
    assert any("议价进行中" in r for r in risks), "其他项照常"
    assert not any("纠纷" in r for r in risks), "失败项被跳过"


@pytest.mark.asyncio
async def test_confirm_order_message_includes_risk(monkeypatch):
    """集成：首次确认话术必须带出风险文案。"""

    async def _risks(db, product_id, buyer_id, seller_id):
        return ["你有一笔 ¥320 的议价进行中（卖家尚未回复），可等待议价结果再下单"]

    async def _save(token, action, params):
        pass

    monkeypatch.setattr(tools, "_pre_order_risk_check", _risks)
    monkeypatch.setattr(tools, "_save_pending_confirm", _save)
    # 会话合并后 confirm_order 只发一条商品行查询（price/seller_id/cat_l1/cat_l2/condition/status），
    # 路由关键字从 "seller_id" 改为 "FROM product"，返回整行供价格/seller_id 取用。
    monkeypatch.setattr(tools, "async_session", lambda: _RouteSession({
        "user_address": ("张三", "13800138000", "广东", "深圳", "南山", "某某路1号"),
        "FROM product": (88.0, 7, "数码", "手机", "95新", "onsale"),
    }))
    result = json.loads(await tools.confirm_order.ainvoke(
        {"user_id": "7", "product_id": 100, "price": 88.0}))
    assert result["need_confirm"] is True
    assert "议价进行中" in result["message"], "确认话术必须如实带出风险提示"
    assert "确认" in result["message"], "风险提示不得阻塞确认流程"


# ─────────────────────────────────────────────
# confirm_order 首次确认分支：单会话合并回归
# ─────────────────────────────────────────────
@pytest.mark.asyncio
async def test_merged_single_session(monkeypatch):
    """首次确认分支只开一个 DB 会话（价格兜底/地址/估价/风险检查合并）。"""
    opened = []

    class _CountingSession(_RouteSession):
        async def __aenter__(self):
            opened.append(1)
            return self

    async def _risks(db, product_id, buyer_id, seller_id):
        return []

    async def _save(token, action, params):
        pass

    monkeypatch.setattr(tools, "_pre_order_risk_check", _risks)
    monkeypatch.setattr(tools, "_save_pending_confirm", _save)
    monkeypatch.setattr(tools, "async_session", lambda: _CountingSession({
        "FROM product": (88.0, 7, "数码", "手机", "95新", "onsale"),
        "user_address": ("张三", "13800138000", "广东", "深圳", "南山", "某某路1号"),
    }))
    result = json.loads(await tools.confirm_order.ainvoke(
        {"user_id": "7", "product_id": 100, "tenant_id": "1"}))
    assert result["need_confirm"] is True
    assert len(opened) == 1, f"首次确认分支应只开 1 个会话，实际 {len(opened)}"


@pytest.mark.asyncio
async def test_valuation_failure_does_not_break_confirm(monkeypatch):
    """估价段失败不拖垮确认流程（合并后独立降级语义保留）。"""
    import app.services.valuation as valuation

    async def _risks(db, product_id, buyer_id, seller_id):
        return []

    async def _save(token, action, params):
        pass

    # 估价内部经 fetch_transactions 查库，直接在源模块上 mock 成抛异常
    async def _tx_fail(db, tenant, cat_l1):
        raise RuntimeError("valuation down")

    monkeypatch.setattr(tools, "_pre_order_risk_check", _risks)
    monkeypatch.setattr(tools, "_save_pending_confirm", _save)
    monkeypatch.setattr(valuation, "fetch_transactions", _tx_fail)
    monkeypatch.setattr(tools, "async_session", lambda: _RouteSession({
        "FROM product": (88.0, 7, "数码", "手机", "95新", "onsale"),
        "user_address": ("张三", "13800138000", "广东", "深圳", "南山", "某某路1号"),
    }))
    result = json.loads(await tools.confirm_order.ainvoke(
        {"user_id": "7", "product_id": 100, "tenant_id": "1"}))
    assert result["need_confirm"] is True
    assert "议价" not in result["message"]
