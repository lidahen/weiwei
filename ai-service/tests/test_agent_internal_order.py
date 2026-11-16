"""跨租户下单缺陷的回归测试。

背景（修复的缺陷）：``tools.py`` 的 ``confirm_order`` 原先直接
``INSERT INTO trade_order``，列清单中**没有 tenant_id**，于是落到表定义的
``NOT NULL DEFAULT 1``；而订单租户本应与商品同租户（见 Java 侧
``TradeOrderServiceImpl.createOrder``）。当前 ``tenant.enabled=true``，后果是：
买家在自己的租户里查不到刚下的单，默认租户（1）的订单列表里却混入了别人的单。

修复后下单必须经 Java 内部端点，由 Java 统一负责租户/运费/商品快照。
本文件锁定三条不变量：
  1. 调用 Java 内部端点时必须带上服务间令牌与 userId/productId；
  2. 令牌未配置时"失败关闭"，且**不发出任何网络请求**；
  3. Java 调用失败时明确报错，**绝不回退为直写数据库**。
"""
import json

import httpx
import pytest
import respx
from httpx import Response

from app.services.agent import tools
from app.services.agent.tools import JavaInternalUnavailable, _java_internal_create_order

BASE = "http://java.test:8080"
TOKEN = "unit-test-internal-token"
PATH = "/internal/trade/order/create"


@pytest.fixture(autouse=True)
def _java_settings(monkeypatch):
    """统一注入可预测的 Java 侧配置，避免依赖本机 env。"""
    monkeypatch.setattr(tools.settings, "java_base_url", BASE)
    monkeypatch.setattr(tools.settings, "internal_api_token", TOKEN)
    monkeypatch.setattr(tools.settings, "java_internal_timeout", 5)


# ─────────────────────────────────────────────
# _java_internal_create_order：请求契约与失败映射
# ─────────────────────────────────────────────
@pytest.mark.asyncio
async def test_calls_java_endpoint_with_token_and_ids():
    with respx.mock(base_url=BASE) as mock:
        route = mock.post(PATH).mock(
            return_value=Response(
                200,
                json={
                    "code": 200,
                    "msg": "操作成功",
                    "data": {"orderNo": "NO-1", "orderId": 9, "totalAmount": 13.0, "status": "unpaid"},
                },
            )
        )
        data = await _java_internal_create_order(7, 100, address_id=55)

    assert data["orderNo"] == "NO-1"
    request = route.calls.last.request
    assert request.headers["X-Internal-Token"] == TOKEN, "必须携带服务间令牌"
    # addressId 必须透传：express 商品缺地址会被 Java 校验链拒绝（Agent 买快递商品全量回归的根因）
    assert json.loads(request.read()) == {"userId": 7, "productId": 100, "addressId": 55}


@pytest.mark.asyncio
async def test_omits_address_id_when_absent():
    """未解析到地址时不发送 addressId 键（self_pickup 商品无需地址）。"""
    with respx.mock(base_url=BASE) as mock:
        route = mock.post(PATH).mock(
            return_value=Response(200, json={"code": 200, "data": {"orderNo": "NO-2", "orderId": 10}})
        )
        await _java_internal_create_order(7, 100)

    assert json.loads(route.calls.last.request.read()) == {"userId": 7, "productId": 100}


@pytest.mark.asyncio
async def test_fail_closed_and_no_request_when_token_missing(monkeypatch):
    """未配置令牌时必须失败关闭，且不得发出任何请求（防止"未配置即放行"）。"""
    monkeypatch.setattr(tools.settings, "internal_api_token", "   ")
    # assert_all_called=False：本用例的断言点恰恰是"这个 mock 路由一次都不该被调用"
    with respx.mock(base_url=BASE, assert_all_called=False) as mock:
        mock.post(PATH).mock(return_value=Response(200, json={"code": 200, "data": {}}))
        with pytest.raises(JavaInternalUnavailable) as exc:
            await _java_internal_create_order(7, 100)
        assert "令牌未配置" in str(exc.value)
        assert not mock.calls, "未配置令牌时不应发起网络请求"


@pytest.mark.asyncio
async def test_http_403_maps_to_auth_error():
    with respx.mock(base_url=BASE) as mock:
        mock.post(PATH).mock(return_value=Response(403, json={"code": 403, "msg": "内部接口鉴权失败"}))
        with pytest.raises(JavaInternalUnavailable) as exc:
            await _java_internal_create_order(7, 100)
    assert "鉴权失败" in str(exc.value)


@pytest.mark.asyncio
async def test_business_rejection_surfaces_server_message():
    """业务性拒绝（如跨租户商品）应把 Java 侧的消息原样给到用户。"""
    with respx.mock(base_url=BASE) as mock:
        mock.post(PATH).mock(return_value=Response(400, json={"code": 400, "msg": "商品不存在"}))
        with pytest.raises(JavaInternalUnavailable) as exc:
            await _java_internal_create_order(7, 100)
    assert str(exc.value) == "商品不存在"


@pytest.mark.asyncio
async def test_network_error_maps_to_friendly_message():
    with respx.mock(base_url=BASE) as mock:
        mock.post(PATH).mock(side_effect=httpx.ConnectError("connection refused"))
        with pytest.raises(JavaInternalUnavailable) as exc:
            await _java_internal_create_order(7, 100)
    assert "暂时不可用" in str(exc.value)


@pytest.mark.asyncio
async def test_success_without_order_no_is_rejected():
    with respx.mock(base_url=BASE) as mock:
        mock.post(PATH).mock(return_value=Response(200, json={"code": 200, "msg": "ok", "data": {}}))
        with pytest.raises(JavaInternalUnavailable):
            await _java_internal_create_order(7, 100)


# ─────────────────────────────────────────────
# confirm_order：不得回退为直写数据库
# ─────────────────────────────────────────────
class _FakeResult:
    def fetchone(self):
        return (1,)  # 模拟"存在默认收货地址"


class _FakeSession:
    async def execute(self, *args, **kwargs):
        return _FakeResult()

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False


@pytest.mark.asyncio
async def test_confirm_order_reports_error_and_does_not_mark_done_when_java_fails(monkeypatch):
    """核心回归：Java 调用失败时只报错返回；不生成订单、不标记幂等键。"""
    marked = []

    async def _pop(_token, _user_id):
        return {"params": {"product_id": 100}}

    async def _not_executed(_key):
        return False

    async def _mark(key):
        marked.append(key)

    async def _raise(_uid, _pid, address_id=None):
        raise JavaInternalUnavailable("下单服务暂时不可用，请稍后重试")

    monkeypatch.setattr(tools, "_pop_valid_confirm", _pop)
    monkeypatch.setattr(tools, "_idem_already_executed", _not_executed)
    monkeypatch.setattr(tools, "_idem_mark_executed", _mark)
    monkeypatch.setattr(tools, "_java_internal_create_order", _raise)
    monkeypatch.setattr(tools, "async_session", lambda: _FakeSession())

    result = json.loads(
        await tools.confirm_order.ainvoke(
            {"user_id": "7", "product_id": 100, "price": 88.0, "confirm_token": "valid-token"}
        )
    )

    assert "error" in result
    assert "暂时不可用" in result["error"]
    assert marked == [], "Java 未成功时不得标记幂等键已执行"


@pytest.mark.asyncio
async def test_confirm_order_uses_java_returned_order_no(monkeypatch):
    """成功路径：订单号/金额以 Java 返回为准（而不是 Python 自己拼一个）。"""
    marked = []

    async def _pop(_token, _user_id):
        return {"params": {"product_id": 100}}

    async def _not_executed(_key):
        return False

    async def _mark(key):
        marked.append(key)

    async def _ok(_uid, _pid, address_id=None):
        return {"orderNo": "NO-FROM-JAVA", "orderId": 9, "totalAmount": 13.0, "status": "unpaid"}

    monkeypatch.setattr(tools, "_pop_valid_confirm", _pop)
    monkeypatch.setattr(tools, "_idem_already_executed", _not_executed)
    monkeypatch.setattr(tools, "_idem_mark_executed", _mark)
    monkeypatch.setattr(tools, "_java_internal_create_order", _ok)
    monkeypatch.setattr(tools, "async_session", lambda: _FakeSession())

    result = json.loads(
        await tools.confirm_order.ainvoke(
            {"user_id": "7", "product_id": 100, "price": 88.0, "confirm_token": "valid-token"}
        )
    )

    assert result["success"] is True
    assert result["order_no"] == "NO-FROM-JAVA"
    assert "13.00" in result["message"], "金额应取 Java 返回的 totalAmount（含运费）"
    assert len(marked) == 1, "成功后应标记幂等键，防重复下单"
