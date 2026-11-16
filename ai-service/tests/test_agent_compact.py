"""历史摘要压缩（_compact_for_llm）单元测试。

验证「提炼优先于裁剪」（学习路线 s06）：触发阈值、切点对齐、
covered>=start 时不耗 LLM 重建、增量摘要只覆盖新增段、LLM 失败降级。
全部通过 monkeypatch 避免真实 LLM 调用。
"""
from typing import Any, Dict, List, Tuple

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.services.agent import nodes as N
from app.services.agent.nodes import (
    COMPACT_KEEP_RECENT,
    COMPACT_TRIGGER,
    _compact_for_llm,
    _fallback_summary,
    _summarize_messages,
)
from app.services.agent.state import ShoppingState


def _fake_summary(old_summary: str, msgs: list) -> str:
    """可观察的假摘要：记录参与摘要的消息条数与首条内容。"""
    texts = [getattr(m, "content", "") for m in msgs if isinstance(m, (HumanMessage, AIMessage))]
    return f"FAKE[old={bool(old_summary)},n={len(msgs)},first={texts[0] if texts else ''}]"


@pytest.fixture
def patched_summarize(monkeypatch):
    calls: List[Tuple[str, list]] = []

    async def fake(old_summary: str, msgs: list) -> str:
        calls.append((old_summary, msgs))
        return _fake_summary(old_summary, msgs)

    monkeypatch.setattr(N, "_summarize_messages", fake)
    return calls


def _make_messages(n: int) -> List:
    """构造 n 条消息：首条人设 SystemMessage + Human/AIMessage 交替（与生产一致）。"""
    msgs: List = [SystemMessage(content="人设")]
    for i in range(n):
        if i % 2 == 0:
            msgs.append(HumanMessage(content=f"m{i}"))
        else:
            msgs.append(AIMessage(content=f"a{i}"))
    return msgs


@pytest.mark.asyncio
async def test_below_threshold_no_compact(patched_summarize):
    """消息数 <= TRIGGER（含首条人设）：不压缩、不耗 LLM。"""
    state: ShoppingState = {"history_summary": "", "summary_upto": 0}
    msgs = _make_messages(COMPACT_TRIGGER - 1)
    assert len(msgs) == COMPACT_TRIGGER
    head, updates, tail_start = await _compact_for_llm(msgs, state)
    assert head is None and updates is None and tail_start == 0
    assert patched_summarize == []


@pytest.mark.asyncio
async def test_trigger_summarizes_increment(patched_summarize):
    """超过阈值：摘要只覆盖增量段，切点后消息保持完整。"""
    msgs = _make_messages(COMPACT_TRIGGER + 4)
    state: ShoppingState = {"history_summary": "", "summary_upto": 0}
    head, updates, tail_start = await _compact_for_llm(msgs, state)
    assert head is not None
    # head = 首条 SystemMessage(人设) + 摘要 SystemMessage
    assert len(head) == 2
    assert isinstance(head[0], SystemMessage)
    assert head[1].content.startswith("## 此前对话摘要")
    assert "FAKE[old=False" in head[1].content
    # 摘要输入只含增量段（0..start）
    old, seg = patched_summarize[0]
    assert old == ""
    assert seg == msgs[:tail_start]
    # 切点必须落在 HumanMessage 上且 >= len - KEEP
    assert isinstance(msgs[tail_start], HumanMessage)
    assert tail_start >= len(msgs) - COMPACT_KEEP_RECENT
    # updates 持久化原始摘要（不含消息头包装）与覆盖点
    assert updates["summary_upto"] == tail_start
    assert "FAKE[old=False" in updates["history_summary"]


@pytest.mark.asyncio
async def test_incremental_second_round(patched_summarize):
    """第二轮压缩：旧摘要传入，只摘要 covered→start 的新增段。"""
    msgs = _make_messages(COMPACT_TRIGGER + 10)
    old_summary = "旧摘要要点"
    covered = 10
    state: ShoppingState = {"history_summary": old_summary, "summary_upto": covered}
    _, updates, tail_start = await _compact_for_llm(msgs, state)
    old, seg = patched_summarize[0]
    assert old == old_summary
    assert seg == msgs[covered:tail_start]
    assert updates["summary_upto"] == tail_start


@pytest.mark.asyncio
async def test_no_new_segment_rebuilds_from_summary(patched_summarize):
    """start <= covered：不调 LLM，用已有摘要重建 head。"""
    msgs = _make_messages(COMPACT_TRIGGER + 2)
    state: ShoppingState = {"history_summary": "已有摘要", "summary_upto": len(msgs) - 2}
    head, updates, tail_start = await _compact_for_llm(msgs, state)
    assert patched_summarize == []
    assert updates is None
    assert tail_start == len(msgs) - 2
    assert len(head) == 2 and "已有摘要" in head[1].content


@pytest.mark.asyncio
async def test_no_new_segment_no_summary_passthrough(patched_summarize):
    """start <= covered 且无已有摘要：原样透传（走 _trim_for_llm 兜底）。"""
    msgs = _make_messages(COMPACT_TRIGGER + 2)
    state: ShoppingState = {"history_summary": "", "summary_upto": len(msgs) - 2}
    head, updates, tail_start = await _compact_for_llm(msgs, state)
    assert head is None and updates is None and tail_start == 0
    assert patched_summarize == []


@pytest.mark.asyncio
async def test_tail_alignment_keeps_tool_sequence(patched_summarize):
    """切点从 HumanMessage 起切：工具调用序列（AI+Tool）不被拆散。"""
    msgs = _make_messages(COMPACT_TRIGGER + 6)
    # 末尾拼一段完整工具调用序列：AI(tool_calls) + Tool + AIMessage
    msgs += [
        AIMessage(content="", tool_calls=[{"name": "search_goods", "args": {}, "id": "t1"}]),
        ToolMessage(content="[结果]", tool_call_id="t1"),
        AIMessage(content="找到了"),
    ]
    state: ShoppingState = {"history_summary": "", "summary_upto": 0}
    _, _, tail_start = await _compact_for_llm(msgs, state)
    assert tail_start < len(msgs)
    assert isinstance(msgs[tail_start], HumanMessage)


@pytest.mark.asyncio
async def test_summarize_skips_system_and_tool_messages(monkeypatch):
    """摘要内容提取：System/ToolMessage 跳过，tool_calls 记为「调用工具」。"""
    captured: Dict[str, Any] = {}

    class FakeLLM:
        async def ainvoke(self, _msgs, config=None):
            captured["content"] = _msgs[-1].content
            return AIMessage(content="要点1")

    monkeypatch.setattr(N, "_get_llm", lambda: FakeLLM())
    msgs = [
        HumanMessage(content="想买手机"),
        AIMessage(content="", tool_calls=[{"name": "search_goods", "args": {}, "id": "t"}]),
        ToolMessage(content="结果", tool_call_id="t"),
        SystemMessage(content="人设"),
        AIMessage(content="推荐 A"),
    ]
    s = await _summarize_messages("", msgs)
    assert s == "要点1"
    c = captured["content"]
    assert "用户：想买手机" in c
    assert "[调用工具 search_goods]" in c
    assert "推荐 A" in c
    assert "结果" not in c and "人设" not in c


@pytest.mark.asyncio
async def test_summarize_llm_failure_degrades(monkeypatch):
    """LLM 失败降级为 _fallback_summary，不抛异常。"""

    def boom():
        raise RuntimeError("llm down")

    monkeypatch.setattr(N, "_get_llm", boom)
    msgs = [HumanMessage(content="问题"), AIMessage(content="回答")]
    s = await _summarize_messages("", msgs)
    assert "用户：问题" in s and "助手：回答" in s


def test_fallback_summary_appends_old():
    s = _fallback_summary("旧", ["a", "b", "c", "d", "e", "f", "g", "h"])
    assert s.startswith("旧")
    assert "a\n" not in s  # 只保留尾部 6 条
    assert "h" in s


def test_fallback_summary_empty_old():
    assert _fallback_summary("", ["x"]) == "x"


# ── 审计修复回归 ───────────────────────────────────────────

def test_trim_keeps_persona_and_summary_system_messages():
    """_trim_for_llm 必须保留开头连续 SystemMessage（人设 + 摘要），只裁历史。"""
    from app.services.agent.nodes import _trim_for_llm
    sys1 = SystemMessage(content="人设")
    sys2 = SystemMessage(content="## 此前对话摘要\n要点")
    msgs = _make_messages(30)
    msgs[0] = sys1
    msgs.insert(1, sys2)
    out = _trim_for_llm(msgs)
    assert out[0] is sys1
    assert out[1] is sys2
    # keep=16，切点右移到 HumanMessage 最多多保 1 条
    assert len(out) <= 2 + 16 + 1
    assert isinstance(out[2], HumanMessage)


def test_ensure_persona_overrides_runner_prompt():
    """runner 注入的旧 SYSTEM_PROMPT 必须被域人设覆盖；id 保留供 add_messages 原位替换。"""
    from app.services.agent.nodes import GUIDE_SYSTEM_PROMPT, _ensure_persona
    old = SystemMessage(content="旧版 runner prompt")
    old.id = "msg-0"
    out = _ensure_persona([old, HumanMessage(content="hi")], "in_sale")
    assert out[0].content.startswith(GUIDE_SYSTEM_PROMPT[:20])
    assert out[0].id == "msg-0"
    assert len(out) == 2


def test_ensure_persona_butler_for_community():
    from app.services.agent.nodes import BUTLER_SYSTEM_PROMPT, _ensure_persona
    out = _ensure_persona([SystemMessage(content="旧")], "community")
    assert out[0].content.startswith(BUTLER_SYSTEM_PROMPT[:20])


def test_ensure_persona_appends_rag():
    from app.services.agent.nodes import _ensure_persona
    out = _ensure_persona([], "in_sale", rag_context="退货政策：7 天无理由")
    assert "相关知识库信息" in out[0].content
    assert "退货政策：7 天无理由" in out[0].content


def test_ensure_persona_prepends_when_no_system():
    from app.services.agent.nodes import _ensure_persona
    out = _ensure_persona([HumanMessage(content="hi")], "in_sale")
    assert isinstance(out[0], SystemMessage)
    assert isinstance(out[1], HumanMessage)


# ── 人设注入：长期记忆 / 行为画像 / 历史 system 快照剔除（spec §9.3）──


def test_ensure_persona_order_persona_memory_hint_rag():
    """拼接顺序：人设 → 记忆 → interestHint → RAG（spec §9.3 第 3 条）。"""
    from app.services.agent.nodes import GUIDE_SYSTEM_PROMPT, _ensure_persona
    memory = "## 该用户的长期偏好（来自历史对话，仅供参考）\n- 预算上限：50元以内"
    out = _ensure_persona(
        [HumanMessage(content="hi")], "in_sale",
        rag_context="退货政策：7 天无理由",
        memory_context=memory,
        interest_hint="常看数码",
    )
    content = out[0].content
    assert content.startswith(GUIDE_SYSTEM_PROMPT[:20])
    assert (
        content.index("- 预算上限：50元以内")
        < content.index("## 用户行为画像（来自平台统计）")
        < content.index("常看数码")
        < content.index("## 相关知识库信息")
        < content.index("退货政策：7 天无理由")
    )


def test_ensure_persona_drops_non_head_system():
    """[SystemMessage(人设), SystemMessage(旧画像快照), HumanMessage] → 输出仅 1 条 system。

    历史 checkpoint（7 天 TTL）里可能留着旧版"每轮注入画像"的快照；不剔除的话
    模型会同时看到两份互相矛盾的画像。
    """
    from app.services.agent.nodes import _ensure_persona
    out = _ensure_persona([
        SystemMessage(content="人设"), SystemMessage(content="旧画像快照"), HumanMessage(content="你好"),
    ], "in_sale")
    assert sum(isinstance(m, SystemMessage) for m in out) == 1
    assert len(out) == 2
    assert out[0].content != "旧画像快照" and out[1].content == "你好"


def test_ensure_persona_keeps_head_id_for_inplace_replace():
    """首位人设的 id 必须保留（add_messages 按 id 归并，丢 id 会追加到历史末尾）。"""
    from app.services.agent.nodes import _ensure_persona
    head = SystemMessage(content="旧人设", id="s0")
    out = _ensure_persona([head, HumanMessage(content="hi")], "in_sale",
                          memory_context="## 该用户的长期偏好\n- 预算上限：50元以内")
    assert out[0].id == "s0"
    assert "- 预算上限：50元以内" in out[0].content


def test_ensure_persona_never_mutates_input():
    """剔除是「每轮从输入剔除」，不是「从 state 删除」（spec §9.3 第 6 条）：
    传入列表长度不变、内容不变；summary_upto 的旧下标继续有效。"""
    from app.services.agent import nodes
    src = [SystemMessage(content="人设", id="s0"), SystemMessage(content="旧画像", id="s1"),
           HumanMessage(content="你好")]
    out = nodes._ensure_persona(src, "guide", memory_context="## 长期偏好\n- 预算上限：50元以内")
    assert len(src) == 3 and isinstance(src[1], SystemMessage)   # 入参未被改动
    assert [getattr(m, "content", "") for m in src] == ["人设", "旧画像", "你好"]
    assert [getattr(m, "id", None) for m in src] == ["s0", "s1", None]
    assert "- 预算上限：50元以内" in out[0].content
    assert out is not src


def test_ensure_persona_without_memory_unchanged():
    """memory_context="" 且 interest_hint="" → 输出与既有行为一致（不引入空段落）。"""
    from app.services.agent.nodes import _ensure_persona
    src = [SystemMessage(content="旧", id="s0"), HumanMessage(content="hi")]
    plain = _ensure_persona(src, "in_sale", rag_context="规则")
    with_blank = _ensure_persona(src, "in_sale", rag_context="规则",
                                 memory_context="", interest_hint="")
    assert with_blank[0].content == plain[0].content
    assert "用户行为画像" not in with_blank[0].content
    assert len(with_blank) == 2


async def test_chitchat_node_injects_memory_block(monkeypatch):
    """chitchat_node 自建 messages，不走 _ensure_persona —— 必须单独注入记忆块。"""
    from app.services.agent import nodes
    captured = {}

    async def _fake_invoke(llm, msgs):
        captured["msgs"] = msgs
        return AIMessage(content="ok"), {}

    monkeypatch.setattr(nodes, "ainvoke_or_stream", _fake_invoke)
    monkeypatch.setattr(nodes.settings, "ai_api_key", "test-key")
    await nodes.chitchat_node({"messages": [HumanMessage(content="你好")],
                               "memory_context": "## 该用户的长期偏好"})
    assert any("长期偏好" in getattr(m, "content", "") for m in captured["msgs"])
    # 记忆拼进原有那条 system（不新增 system 消息条数，sys_head 逻辑只认开头连续段）
    assert [type(m).__name__ for m in captured["msgs"]].count("SystemMessage") == 1


class _LoopFakeLLM:
    """`bind_tools` 后原样返回自己：真正的 LLM 调用被 ainvoke_or_stream 替身拦下。"""

    def bind_tools(self, tools):
        return self


class _LoopFakeSessionFactory:
    """`async with async_session() as db` 的替身（无工具结果时用不到这个 session）。"""

    def __call__(self):
        return self

    async def __aenter__(self):
        return object()

    async def __aexit__(self, *exc):
        return False


def _boundary_state() -> ShoppingState:
    """30 条消息：首位人设 + messages[1] 旧画像快照 + m2..m29（恰好越过压缩阈值）。

    summary_upto=21 是在改动前（未剔除快照时）记下的下标：已摘要前缀 = U[0:21]，
    边界消息 = U[21] = "m21"，它必须留在实时尾部。
    """
    msgs: List = [SystemMessage(content="人设", id="p0"),
                  SystemMessage(content="旧画像快照", id="s1")]
    for i in range(2, 30):
        msgs.append(HumanMessage(content=f"m{i}") if i % 2 == 0 else AIMessage(content=f"m{i}"))
    return {"messages": msgs, "summary_upto": 21, "history_summary": "旧摘要"}


async def test_compaction_keeps_message_at_stale_snapshot_boundary(monkeypatch):
    """剔除 system 快照会让下标左移：压缩切点必须换算，否则边界消息永久掉出 LLM 输入。

    场景：改动前已压缩过（summary_upto 记的是未剔除列表的下标）且 checkpoint 里带着
    旧画像快照。不换算时 tail 从 m22 起，而摘要只覆盖到 m20 —— m21 两边都不在。
    """
    import app.database as _db

    captured: List[list] = []

    async def _fake_invoke(llm, msgs):
        captured.append(list(msgs))
        return AIMessage(content="好的"), {}

    monkeypatch.setattr(N, "ainvoke_or_stream", _fake_invoke)
    monkeypatch.setattr(N, "_get_llm", lambda: _LoopFakeLLM())
    monkeypatch.setattr(N.settings, "ai_api_key", "test-key")
    monkeypatch.setattr(_db, "async_session", _LoopFakeSessionFactory())

    await N.run_agent_tool_loop(_boundary_state(), "pre_sale")

    texts = [getattr(m, "content", "") for m in captured[0]]
    assert "m21" in texts, "边界消息既不在摘要里也不在尾部 = 永久丢失"
    assert "旧画像快照" not in texts, "历史 system 快照仍必须剔除"
    assert "m29" in texts                      # 最近一条照常在尾部
    assert isinstance(captured[0][0], SystemMessage)
    assert captured[0][1].content.startswith("## 此前对话摘要")


def test_covered_after_strip_counts_survivors_not_constant_one():
    """换算按「前缀里还剩几条」数，不假设只剔了一条（0 条 / N 条快照都要算准）。"""
    from app.services.agent.nodes import _covered_after_strip
    # 未剔除：U[0] 是 system，前缀 21 条里只有它一条 system → 边界仍是 21
    plain = [SystemMessage(content="人设", id="p0")] + [HumanMessage(content=f"u{i}") for i in range(1, 30)]
    assert _covered_after_strip(plain, 21, 29) == 21
    # 首位非 system：剔除后头部会新增一条人设 → 边界右移一位
    no_head = [HumanMessage(content=f"u{i}") for i in range(30)]
    assert _covered_after_strip(no_head, 21, 31) == 22
    # 前缀里有两条 system 快照 → 边界左移两位
    two = [SystemMessage(content="人设", id="p0"), SystemMessage(content="旧1", id="s1"),
           SystemMessage(content="旧2", id="s2")] + [HumanMessage(content=f"u{i}") for i in range(3, 30)]
    assert _covered_after_strip(two, 21, 28) == 19
    # 全部已摘要 / 空前缀：夹到合法区间，不越界
    assert _covered_after_strip(plain, 0, 29) == 0
    assert _covered_after_strip(plain, 99, 29) == 29


@pytest.mark.asyncio
async def test_summarize_never_streams_to_sink(monkeypatch):
    """摘要 LLM 调用不得经 token 汇泄漏到用户流式输出。"""
    from app.services.agent.stream import TokenSink, bind_sink, reset_sink

    class FakeLLM:
        async def ainvoke(self, _msgs, config=None):
            return AIMessage(content="要点1\n要点2")

    monkeypatch.setattr(N, "_get_llm", lambda: FakeLLM())
    sink = TokenSink(queue=__import__("asyncio").Queue())
    token = bind_sink(sink)
    try:
        msgs = [HumanMessage(content="问题"), AIMessage(content="回答")]
        s = await _summarize_messages("", msgs)
        assert "要点1" in s
        assert sink.emitted_text == ""  # 关键：摘要不进用户流
        assert sink.queue.empty()
    finally:
        reset_sink(token)


@pytest.mark.asyncio
async def test_summarize_timeout_degrades(monkeypatch):
    """摘要 LLM 卡死（>15s）时降级为 fallback，不拖垮主流程。"""
    import asyncio as _aio

    class SlowLLM:
        async def ainvoke(self, _msgs, config=None):
            await _aio.sleep(30)

    monkeypatch.setattr(N, "_get_llm", lambda: SlowLLM())
    msgs = [HumanMessage(content="问题"), AIMessage(content="回答")]
    s = await _summarize_messages("", msgs)
    assert "用户：问题" in s
