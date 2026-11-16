"""共享 Redis 异步客户端（懒加载 + 优雅降级）。

用途：Agent 工具确认令牌、幂等键（LangGraph checkpointer 由 AsyncRedisSaver 自行建连）。
Redis 不可用时 get_redis() 返回 None，调用方退化为进程内实现——开发环境无 Redis 也能跑。
"""
import logging
from typing import Any, Optional

from app.config import settings

logger = logging.getLogger(__name__)

_client: Optional[Any] = None
_probed = False


async def get_redis():
    """返回可用的 Redis 异步客户端；连接失败只探测一次并缓存结果，返回 None。"""
    global _client, _probed
    if _probed:
        return _client
    _probed = True
    try:
        import redis.asyncio as aioredis

        client = aioredis.from_url(
            settings.redis_url,
            socket_connect_timeout=settings.redis_connect_timeout,
            socket_timeout=settings.redis_connect_timeout,
        )
        await client.ping()
        _client = client
        logger.info("Redis 已连接（%s）", settings.redis_url)
    except Exception as e:
        _client = None
        logger.warning("Redis 不可用（%s），Agent 状态退化为进程内实现", e)
    return _client


def reset_probe() -> None:
    """重置连接探测缓存（测试用；生产进程内通常只需探测一次）。"""
    global _client, _probed
    _client = None
    _probed = False


async def json_set(key: str, value: Any, ttl_seconds: int) -> None:
    """写 JSON 值并设过期时间。"""
    import json as _json

    r = await get_redis()
    if r is None:
        return
    await r.set(key, _json.dumps(value, ensure_ascii=False), ex=ttl_seconds)


async def json_get(key: str) -> Optional[Any]:
    """读 JSON 值，不存在/解析失败返回 None。"""
    import json as _json

    r = await get_redis()
    if r is None:
        return None
    raw = await r.get(key)
    if raw is None:
        return None
    try:
        return _json.loads(raw)
    except (ValueError, TypeError):
        return None


async def json_getdel(key: str) -> Optional[Any]:
    """原子读删（GETDEL，Redis 6.2+；不支持时退化为 GET+DEL）。"""
    import json as _json

    r = await get_redis()
    if r is None:
        return None
    try:
        raw = await r.getdel(key)
    except Exception:
        raw = await r.get(key)
        if raw is not None:
            await r.delete(key)
    if raw is None:
        return None
    try:
        return _json.loads(raw)
    except (ValueError, TypeError):
        return None
