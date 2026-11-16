"""Agent 韧性回归（学习路线 Stage 7 安全与异常维度）：

- 单次工具执行超时：卡死的工具返回结构化错误而非挂死对话
- 整图执行总超时：超时返回兜底回复而非无限等待
- 图 config 显式携带 recursion_limit（防死循环，设计文档 6.6）
"""
import asyncio
import json

import pytest

from app.config import settings
from app.services.agent.nodes import _invoke_tool_with_timeout
from app.services.agent.runner import _ainvoke_with_deadline, _graph_config
from langchain_core.tools import tool


@tool
async def _slow_tool(x: int) -> str:
    """测试用慢工具：sleep 5s"""
    await asyncio.sleep(5)
    return "done"


@tool
async def _fast_tool(x: int) -> str:
    """测试用快工具"""
    return "ok"


@pytest.mark.asyncio
async def test_tool_timeout_returns_error_json(monkeypatch):
    """卡死的工具在 agent_tool_timeout 内返回结构化错误，不抛异常不挂死。"""
    monkeypatch.setattr(settings, "agent_tool_timeout", 0.2)
    t0 = asyncio.get_event_loop().time()
    result_str, ok, err = await _invoke_tool_with_timeout(_slow_tool, {"x": 1})
    elapsed = asyncio.get_event_loop().time() - t0
    assert ok is False and err == "timeout"
    assert "超时" in json.loads(result_str)["error"]
    assert elapsed < 2  # 远小于工具本身 5s


@pytest.mark.asyncio
async def test_fast_tool_passes_through():
    result_str, ok, err = await _invoke_tool_with_timeout(_fast_tool, {"x": 1})
    assert ok is True and err == ""
    assert result_str == "ok"


@pytest.mark.asyncio
async def test_graph_deadline_returns_graceful_fallback(monkeypatch):
    """整图超时返回兜底结构（不抛异常），error=graph_timeout。"""
    monkeypatch.setattr(settings, "agent_graph_timeout", 0.2)

    class _HangingGraph:
        async def ainvoke(self, inp, config=None):
            await asyncio.sleep(5)
            return {}

    result = await _ainvoke_with_deadline(_HangingGraph(), {}, _graph_config("t1"))
    assert result["error"] == "graph_timeout"
    assert result["finished"] is True
    assert "耗时过长" in result["final_reply"]


def test_graph_config_has_recursion_limit():
    cfg = _graph_config("s1")
    assert cfg["configurable"]["thread_id"] == "s1"
    assert cfg["recursion_limit"] == settings.agent_recursion_limit
    assert cfg["recursion_limit"] >= 10
