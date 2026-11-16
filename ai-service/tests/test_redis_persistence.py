"""Redis 持久化测试：确认令牌/幂等键的 Redis 与内存双路径、checkpointer 优雅降级。

Redis 路径用 fakeredis 模拟（不依赖真实 Redis 服务）；
降级路径显式把客户端置为不可用；checkpointer 回退测试用必然拒绝连接的死端口。
"""
import json
import time

import pytest

from app.config import settings
from app.services import redis_client
from app.services.agent import graph as agent_graph
from app.services.agent import tools as agent_tools


@pytest.fixture()
def no_redis(monkeypatch):
    """强制 Redis 不可用（内存兜底路径）。"""
    async def _none():
        return None

    monkeypatch.setattr(redis_client, "get_redis", _none)
    agent_tools._pending_confirms.clear()
    agent_tools._executed_keys.clear()
    yield


@pytest.fixture()
def fake_redis(monkeypatch):
    """fakeredis 客户端（Redis 路径）。"""
    from fakeredis import aioredis as fake_aioredis

    client = fake_aioredis.FakeRedis(decode_responses=True)

    async def _fake():
        return client

    monkeypatch.setattr(redis_client, "get_redis", _fake)
    agent_tools._pending_confirms.clear()
    agent_tools._executed_keys.clear()
    yield client


# ── JSON 助手 ───────────────────────────────────
@pytest.mark.asyncio
async def test_json_set_get_roundtrip(fake_redis):
    await redis_client.json_set("t:t1", {"a": 1, "b": "中文"}, 60)
    assert await redis_client.json_get("t:t1") == {"a": 1, "b": "中文"}
    ttl = await fake_redis.ttl("t:t1")
    assert 0 < ttl <= 60
    assert await redis_client.json_get("t:missing") is None


@pytest.mark.asyncio
async def test_json_getdel_consumes(fake_redis):
    await redis_client.json_set("t:t2", {"k": "v"}, 60)
    assert await redis_client.json_getdel("t:t2") == {"k": "v"}
    # 一次性：读删后不存在
    assert await redis_client.json_getdel("t:t2") is None


# ── 确认令牌：内存兜底路径 ────────────────────────
@pytest.mark.asyncio
async def test_confirm_token_memory_fallback(no_redis):
    await agent_tools._save_pending_confirm("tok-m", "confirm_order", {"user_id": "42", "product_id": 1})
    assert "tok-m" in agent_tools._pending_confirms
    pending = await agent_tools._pop_valid_confirm("tok-m", "42")
    assert pending["params"]["product_id"] == 1
    assert await agent_tools._pop_valid_confirm("tok-m", "42") is None  # 一次性


@pytest.mark.asyncio
async def test_confirm_token_rejects_wrong_user(no_redis):
    await agent_tools._save_pending_confirm("tok-w", "confirm_order", {"user_id": "42"})
    assert await agent_tools._pop_valid_confirm("tok-w", "99") is None


@pytest.mark.asyncio
async def test_confirm_token_expired(no_redis):
    agent_tools._pending_confirms["tok-old"] = {
        "action": "confirm_order",
        "params": {"user_id": "42"},
        "ts": time.time() - agent_tools._PENDING_CONFIRM_TTL - 10,
    }
    assert await agent_tools._pop_valid_confirm("tok-old", "42") is None


# ── 确认令牌：Redis 路径 ─────────────────────────
@pytest.mark.asyncio
async def test_confirm_token_redis_path(fake_redis):
    await agent_tools._save_pending_confirm("tok-r", "pay_order", {"user_id": "42", "order_no": "TWT1"})
    # 落在 Redis（带 TTL），不在进程内字典
    assert await fake_redis.exists(agent_tools._CONFIRM_KEY_PREFIX + "tok-r") == 1
    assert "tok-r" not in agent_tools._pending_confirms
    ttl = await fake_redis.ttl(agent_tools._CONFIRM_KEY_PREFIX + "tok-r")
    assert 0 < ttl <= agent_tools._PENDING_CONFIRM_TTL

    pending = await agent_tools._pop_valid_confirm("tok-r", "42")
    assert pending["params"]["order_no"] == "TWT1"
    assert await fake_redis.exists(agent_tools._CONFIRM_KEY_PREFIX + "tok-r") == 0
    # GETDEL 语义：用户不符时令牌同样被消费，防反复试探
    await agent_tools._save_pending_confirm("tok-r2", "pay_order", {"user_id": "42"})
    assert await agent_tools._pop_valid_confirm("tok-r2", "99") is None
    assert await agent_tools._pop_valid_confirm("tok-r2", "42") is None


# ── 幂等键 ──────────────────────────────────────
@pytest.mark.asyncio
async def test_idempotency_redis(fake_redis):
    key = agent_tools._idempotency_key("42", "confirm_order", {"product_id": 1, "price": 99.0})
    assert await agent_tools._idem_already_executed(key) is False
    await agent_tools._idem_mark_executed(key)
    assert await agent_tools._idem_already_executed(key) is True
    ttl = await fake_redis.ttl(agent_tools._IDEM_KEY_PREFIX + key)
    assert 0 < ttl <= agent_tools._IDEM_WINDOW_TTL


@pytest.mark.asyncio
async def test_idempotency_memory_fallback(no_redis):
    key = agent_tools._idempotency_key("42", "pay_order", {"order_no": "TWT2"})
    assert await agent_tools._idem_already_executed(key) is False
    await agent_tools._idem_mark_executed(key)
    assert await agent_tools._idem_already_executed(key) is True


# ── checkpointer 优雅降级 ────────────────────────
@pytest.mark.asyncio
async def test_checkpointer_falls_back_on_dead_redis(monkeypatch):
    monkeypatch.setattr(settings, "redis_url", "redis://localhost:59999/0")
    saver = await agent_graph._build_checkpointer()
    from langgraph.checkpoint.memory import InMemorySaver

    assert isinstance(saver, InMemorySaver)


@pytest.mark.asyncio
async def test_build_graph_accepts_custom_checkpointer():
    from langgraph.checkpoint.memory import InMemorySaver

    cp = InMemorySaver()
    g = agent_graph.build_graph(checkpointer=cp)
    assert g.checkpointer is cp


@pytest.mark.asyncio
async def test_aget_graph_caches_and_falls_back(monkeypatch):
    monkeypatch.setattr(settings, "redis_url", "redis://localhost:59999/0")
    monkeypatch.setattr(agent_graph, "_graph_async_cache", None)
    g1 = await agent_graph.aget_graph()
    g2 = await agent_graph.aget_graph()
    assert g1 is g2
    from langgraph.checkpoint.memory import InMemorySaver

    assert isinstance(g1.checkpointer, InMemorySaver)


# ── 真实两段式确认（内存兜底路径回归）────────────
@pytest.mark.asyncio
async def test_confirm_order_two_phase_flow_memory(no_redis, monkeypatch):
    """无需 DB：首次调用返回 need_confirm；无效令牌被拒。"""
    first = json.loads(await agent_tools.confirm_order.ainvoke({"product_id": 999, "user_id": "42"}))
    if first.get("error"):
        pytest.skip(f"环境缺 DB 依赖：{first['error']}")
    assert first["need_confirm"] is True
    token = first["confirm_token"]
    # 篡改令牌 → 拒绝
    bad = json.loads(await agent_tools.confirm_order.ainvoke(
        {"product_id": 999, "confirm_token": "deadbeef", "user_id": "42"}))
    assert "error" in bad
    # 正确令牌走 DB 分支（无 DB 时返回错误而非崩溃）
    second = json.loads(await agent_tools.confirm_order.ainvoke(
        {"product_id": 999, "confirm_token": token, "user_id": "42"}))
    assert isinstance(second, dict)
