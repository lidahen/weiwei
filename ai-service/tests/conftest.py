"""pytest 全局配置。

职责：让测试之间不共享跨事件循环的 I/O 资源。

背景（2026-10-21 实测）：`app.services.redis_client` 用模块级 `_client` / `_probed` 缓存
Redis 连接（生产环境合理——进程内探测一次即可）。但 pytest 的 `asyncio_mode = auto` 为
**每个用例新建一个事件循环**，而 `redis.asyncio` 的连接绑定在创建它的事件循环上。
于是第一个用例建立的连接会被后续用例复用，一旦首个事件循环关闭，后续用例就报：

    RuntimeError: Event loop is closed
    AttributeError: 'NoneType' object has no attribute 'send'

表现为 tests/test_agent.py 里 `test_confirm_order_requires_confirm` /
`test_create_dispute_requires_confirm` 单独跑通过、成组跑必失败。

修法：每个用例结束后重置探测缓存并关闭连接，让下一个用例在自己的事件循环里重建。
这是测试隔离问题，不改生产代码语义（生产的 `reset_probe` 本就是为此暴露的）。
"""
import pytest

from app.services import redis_client


@pytest.fixture(autouse=True)
async def _isolate_redis_client():
    """用例间不复用 Redis 连接（连接与事件循环绑定，不能跨循环移交）。"""
    yield
    client = redis_client._client
    redis_client.reset_probe()
    if client is not None:
        try:
            await client.aclose()
        except Exception:
            # 连接可能已随事件循环关闭而失效；此处只做尽力清理，不影响用例结论
            pass
