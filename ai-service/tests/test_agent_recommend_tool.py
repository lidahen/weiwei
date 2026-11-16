"""recommend_products 工具测试：请求契约、失败降级、结果映射。

模式跟随 test_agent_internal_order.py：respx mock Java 端点 + monkeypatch settings。
锁定四条不变量：
  1. 必须携带 X-Internal-Token，userId 进 query（服务端解析租户用）；
  2. 未配置令牌失败关闭，且不发出任何网络请求；
  3. 超时/403/业务拒绝一律返回 error（不静默给空推荐——空结果会被用户当成"没有好货"）；
  4. 未登录（无 user_id）不给推荐；limit 上限 20 与 Java 侧对齐。
"""
import json

import httpx
import pytest
import respx
from httpx import Response

from app.services.agent import tools

BASE = "http://java.test:8080"
TOKEN = "unit-test-internal-token"
PATH = "/internal/recommend/for-user"


def _java_ok(records):
    # Java 端 TableDataInfo 的真实 JSON 形状是 data.records（Jackson 全局 Long→String、
    # 无 naming strategy），而非旧文档写的 rows
    return Response(200, json={"code": 200, "msg": "操作成功", "data": {"total": len(records), "records": records}})


@pytest.fixture(autouse=True)
def _java_settings(monkeypatch):
    monkeypatch.setattr(tools.settings, "java_base_url", BASE)
    monkeypatch.setattr(tools.settings, "internal_api_token", TOKEN)
    monkeypatch.setattr(tools.settings, "java_internal_timeout", 5)


@pytest.mark.asyncio
async def test_calls_java_with_token_and_user_id():
    with respx.mock(base_url=BASE) as mock:
        # Java 端 TableDataInfo 的真实 JSON 形状是 data.records（Jackson 全局 Long→String、
        # 无 naming strategy）——因此 productId 实际是字符串，mock 用字符串/数字混合锁定该形状
        route = mock.get(PATH).mock(return_value=_java_ok([{
            "productId": "1", "title": "九成新沙发", "price": 320.0,
            "cover": "http://c/1.jpg", "condition": "九成新",
            "city": "深圳", "reason": "你看过同款",
        }, {
            "productId": 2, "title": "八成新茶几", "price": 150.0,
            "cover": "http://c/2.jpg", "condition": "八成新",
            "city": "深圳", "reason": "同城好货",
        }]))
        out = json.loads(await tools.recommend_products.ainvoke(
            {"user_id": "7", "cat_l1": None, "limit": 8}))
    assert out["source"] == "recommend"
    # Long→String 真实形状：productId "1"（字符串）与 2（数字）都应统一输出为 int
    assert out["products"][0]["product_id"] == 1
    assert isinstance(out["products"][0]["product_id"], int)
    assert out["products"][0]["reason"] == "你看过同款"
    assert out["products"][1]["product_id"] == 2
    assert isinstance(out["products"][1]["product_id"], int)
    req = route.calls.last.request
    assert req.headers["X-Internal-Token"] == TOKEN, "必须携带服务间令牌"
    assert "userId=7" in str(req.url), "userId 必须进 query（服务端解析租户用）"


@pytest.mark.asyncio
async def test_token_missing_fails_closed_without_request(monkeypatch):
    monkeypatch.setattr(tools.settings, "internal_api_token", "")
    # assert_all_called=False：本用例的断言点恰恰是"这个 mock 路由一次都不该被调用"
    with respx.mock(base_url=BASE, assert_all_called=False) as mock:
        mock.get(PATH).mock(return_value=_java_ok([]))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert "error" in out
    assert "令牌未配置" in out["error"]
    assert not mock.calls, "未配置令牌时不应发起网络请求"


@pytest.mark.asyncio
async def test_timeout_returns_error():
    with respx.mock(base_url=BASE) as mock:
        mock.get(PATH).mock(side_effect=httpx.ReadTimeout("timeout"))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert "error" in out
    assert "暂时不可用" in out["error"]
    assert "products" not in out, "失败时不得携带空 products（会被当成没有好货）"


@pytest.mark.asyncio
async def test_java_403_maps_to_error():
    with respx.mock(base_url=BASE) as mock:
        mock.get(PATH).mock(return_value=Response(403, json={"code": 403, "msg": "内部接口鉴权失败"}))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert out.get("error") == "内部接口鉴权失败"


@pytest.mark.asyncio
async def test_business_rejection_surfaces_msg():
    with respx.mock(base_url=BASE) as mock:
        mock.get(PATH).mock(return_value=Response(400, json={"code": 400, "msg": "用户不存在或未归属任何租户"}))
        out = json.loads(await tools.recommend_products.ainvoke({"user_id": "7"}))
    assert out.get("error") == "用户不存在或未归属任何租户"


@pytest.mark.asyncio
async def test_missing_user_id_returns_error():
    out = json.loads(await tools.recommend_products.ainvoke({"user_id": ""}))
    assert "error" in out, "未登录用户不给推荐（个性化推荐依赖 userId）"


@pytest.mark.asyncio
async def test_limit_clamped_to_20():
    with respx.mock(base_url=BASE) as mock:
        route = mock.get(PATH).mock(return_value=_java_ok([]))
        await tools.recommend_products.ainvoke({"user_id": "7", "limit": 99})
    assert "pageSize=20" in str(route.calls.last.request.url), "limit 上限 20 与 Java 侧对齐"
