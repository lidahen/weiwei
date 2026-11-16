"""社区管家（community 域）的单元测试：意图路由、类型解析、工具注册与租户隔离。"""

import json
from unittest.mock import patch

from app.services.agent.community_tools import (
    RUNNER_TYPE_NAMES,
    get_community_overview,
    parse_runner_type,
    search_feeds,
    search_runner_tasks,
)
from app.services.agent.state import INTENT_COMMUNITY, INTENT_RUNNER, INTENT_DOMAIN
from app.services.agent.tools import AGENT_TOOLS


class TestIntentDomain:
    def test_community_intents_mapped(self):
        assert INTENT_DOMAIN[INTENT_RUNNER] == "community"
        assert INTENT_DOMAIN[INTENT_COMMUNITY] == "community"

    def test_community_tools_registered(self):
        names = [t.name for t in AGENT_TOOLS["community"]]
        assert "search_runner_tasks" in names
        assert "get_community_overview" in names
        assert "search_feeds" in names
        # 管家也能带出商品卡片
        assert "search_products" in names


class TestParseRunnerType:
    def test_chinese_aliases(self):
        assert parse_runner_type("代取快递") == 1
        assert parse_runner_type("代买东西") == 2
        assert parse_runner_type("代排队") == 3
        assert parse_runner_type("其他") == 4

    def test_free_text_contains_alias(self):
        assert parse_runner_type("帮我看看快递单") == 1
        assert parse_runner_type("想找个人买东西") == 2

    def test_numeric_codes(self):
        assert parse_runner_type("1") == 1
        assert parse_runner_type("3") == 3

    def test_unknown_or_empty(self):
        assert parse_runner_type("随便") is None
        assert parse_runner_type("") is None
        assert parse_runner_type(None) is None


class TestTenantGuard:
    """无租户上下文时社区工具必须拒绝查询（绝不跨租户泄露）。"""

    def _find_async(self, tool):
        return tool.coroutine if hasattr(tool, "coroutine") else tool.func

    async def _invoke(self, tool, **kwargs):
        return await tool.ainvoke(kwargs)

    async def test_runner_tasks_rejected_without_tenant(self):
        out = await self._invoke(search_runner_tasks, task_type=None, reward_min=None,
                                 reward_max=None, limit=5, tenant_id="")
        assert json.loads(out)["error"]

    async def test_overview_rejected_without_tenant(self):
        out = await self._invoke(get_community_overview, tenant_id=None)
        assert json.loads(out)["error"]

    async def test_feeds_rejected_with_zero_tenant(self):
        out = await self._invoke(search_feeds, keyword=None, limit=5, tenant_id="0")
        assert json.loads(out)["error"]


class TestCommunityToolsWithDb:
    """mock 数据库会话，验证 SQL 组装与结果序列化。"""

    async def test_search_runner_tasks_serializes(self):
        import datetime

        class FakeResult:
            def __init__(self, rows):
                self._rows = rows

            def fetchall(self):
                return self._rows

        class FakeDB:
            async def execute(self, sql, params):
                assert params["tid"] == 1
                assert params["type"] == 1  # "代取快递" 已解析为枚举
                return FakeResult([(
                    9, "R20260906001", 1, "帮取快递",
                    "丰巢三号柜", 5.0,
                    datetime.datetime(2026, 9, 7, 18, 0, 0),
                )])

        fake_session = __import__("contextlib").asynccontextmanager

        @fake_session
        async def fake_session_ctx(*a, **k):
            yield FakeDB()

        with patch("app.services.agent.community_tools.async_session", fake_session_ctx), \
                patch("app.services.agent.community_tools._require_tenant", return_value=None):
            out = await search_runner_tasks.ainvoke(
                {"task_type": "代取快递", "reward_min": None, "reward_max": None,
                 "limit": 5, "tenant_id": "1"}
            )
        data = json.loads(out)
        assert data["total"] == 1
        task = data["tasks"][0]
        assert task["task_id"] == 9
        assert task["task_type_name"] == RUNNER_TYPE_NAMES[1]
        assert task["deadline"] == "2026-09-07T18:00:00"
