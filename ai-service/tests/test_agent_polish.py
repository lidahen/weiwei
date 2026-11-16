"""Agent 打磨轮次纯函数单测：搜索放宽变体、成色归一、上下文裁剪、状态事件、路由历史。

全部无外部依赖（不触 DB / LLM），可离线运行。
"""
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.services.agent.nodes import (
    MAX_LLM_HISTORY_MESSAGES,
    _recent_history_text,
    _trim_for_llm,
)
from app.services.agent.stream import TokenSink
from app.services.product_search import (
    build_search_variants,
    normalize_condition_terms,
    split_keywords,
)


# ── 成色同义词归一 ──────────────────────────────
def test_condition_numeric_synonyms():
    terms = normalize_condition_terms("95新")
    assert "%95新%" in terms
    assert "%九五新%" in terms
    assert "%95成新%" in terms
    assert "%九五成新%" in terms


def test_condition_plain_value_passthrough():
    # 非数字等级的取值（如"全新"）只做原样 LIKE 包裹；中文数字写法不触发数字归一
    assert normalize_condition_terms("全新") == ["%全新%"]
    assert normalize_condition_terms("九成新") == ["%九成新%"]
    assert normalize_condition_terms("") == []
    assert normalize_condition_terms(None) == []


def test_condition_chinese_number_mapping():
    # 纯中文数字词不匹配 \d 正则，原样返回
    terms = normalize_condition_terms("八五成新")
    assert terms == ["%八五成新%"]
    # 阿拉伯数字+成新 → 数字与中文两套同义词
    terms2 = normalize_condition_terms("85成新")
    assert "%85成新%" in terms2 and "%八五成新%" in terms2


# ── 关键词分词 ──────────────────────────────────
def test_split_keywords_whitespace():
    assert split_keywords("机械键盘 侧刻") == ["机械键盘", "侧刻"]
    assert split_keywords("苹果，手机") == ["苹果", "手机"]


def test_split_keywords_single_chinese_token_kept():
    assert split_keywords("机械键盘") == ["机械键盘"]
    assert split_keywords("") == []
    assert split_keywords(None) == []


# ── 搜索放宽变体 ────────────────────────────────
def test_variants_full_relaxation_chain():
    variants = build_search_variants(
        keyword="机械键盘 侧刻", condition="95新",
        price_min=100, price_max=500,
    )
    notes = [n for _, n in variants]
    # L0 严格 + L1 放宽成色 + L2 分词 OR + L3 热门兜底
    assert len(variants) == 4
    assert notes[0] == ""
    assert "成色" in notes[1]
    assert "模糊匹配" in notes[2]
    assert "热门" in notes[3]
    # L1 变体不含成色参数；L2 变体含分词参数且不含整句关键词参数
    spec1 = variants[1][0]
    assert not any(k.startswith("cond") for k in spec1["params"])
    spec2 = variants[2][0]
    assert "kw" not in spec2["params"]
    assert any(k.startswith("tkw") for k in spec2["params"])
    assert spec1["where"] and spec2["where"]


def test_variants_single_keyword_no_token_level():
    variants = build_search_variants(keyword="机械键盘")
    # L0 严格 + L3 热门兜底（单词无 L1/L2）
    assert len(variants) == 2
    assert variants[0][1] == ""
    assert "热门" in variants[1][1]


def test_variants_no_filters_single_strict_level():
    variants = build_search_variants()
    assert len(variants) == 1
    spec = variants[0][0]
    assert spec["where"] == [] and spec["params"] == {}


# ── LLM 上下文裁剪 ──────────────────────────────
def _build_long_history(n_user_turns: int) -> list:
    """构造含工具调用序列的长历史：每轮 Human → AI(tool_calls) → Tool → AI(文本)。"""
    msgs: list = [SystemMessage(content="sys")]
    for i in range(n_user_turns):
        msgs.append(HumanMessage(content=f"需求{i}"))
        ai_tool = AIMessage(content="", tool_calls=[
            {"name": "search_products", "args": {"keyword": f"k{i}"}, "id": f"c{i}", "type": "tool_call"},
        ])
        msgs.append(ai_tool)
        msgs.append(ToolMessage(content="[]", tool_call_id=f"c{i}"))
        msgs.append(AIMessage(content=f"回答{i}"))
    return msgs


def test_trim_keeps_system_and_recent_turns():
    msgs = _build_long_history(10)
    trimmed = _trim_for_llm(msgs, keep=MAX_LLM_HISTORY_MESSAGES)
    assert trimmed[0] == msgs[0]
    assert len(trimmed) <= MAX_LLM_HISTORY_MESSAGES + 1
    # 切点必须是 HumanMessage，保证工具调用序列完整
    assert isinstance(trimmed[1], HumanMessage)
    # ToolMessage 不能成为切点首条（避免孤立 tool 响应）
    assert all(not isinstance(m, ToolMessage) or i == 0 or i > 1 for i, m in enumerate(trimmed))


def test_trim_noop_when_short():
    msgs = _build_long_history(2)
    assert _trim_for_llm(msgs) is msgs


def test_trim_without_system_message():
    msgs = _build_long_history(10)[1:]  # 去掉 SystemMessage
    trimmed = _trim_for_llm(msgs, keep=4)
    assert len(trimmed) <= 5
    assert isinstance(trimmed[0], HumanMessage)


# ── TokenSink 状态事件 ──────────────────────────
def test_sink_emit_status_without_queue_noop():
    sink = TokenSink(queue=None)
    sink.emit_status("正在查询")
    sink.emit("文本")
    # 无队列（非流式 chat）：不累积文本、不抛异常
    assert sink.emitted_text == ""
    assert sink.emitted_chars == 0


def test_sink_status_event_tagged_and_not_in_emitted_text():
    import asyncio

    async def run():
        q: asyncio.Queue = asyncio.Queue()
        sink = TokenSink(queue=q)
        sink.emit_status("正在为你查找商品")
        sink.emit("你好")
        return q

    q = asyncio.run(run())
    kinds = []
    while not q.empty():
        kinds.append(q.get_nowait())
    assert kinds == [("status", "正在为你查找商品"), ("text", "你好")]


def test_sink_emit_text_tracks_emitted_text():
    import asyncio

    async def run():
        q: asyncio.Queue = asyncio.Queue()
        sink = TokenSink(queue=q)
        sink.emit("你好")
        sink.emit("世界")
        return sink

    sink = asyncio.run(run())
    assert sink.emitted_text == "你好世界"
    assert sink.emitted_chars == 4


# ── 路由历史拼接 ────────────────────────────────
def test_recent_history_excludes_system_and_last_user():
    state = {
        "messages": [
            SystemMessage(content="sys"),
            HumanMessage(content="有没有机械键盘"),
            AIMessage(content="有 3 款"),
            HumanMessage(content="就买这个"),
        ]
    }
    text = _recent_history_text(state)
    assert "sys" not in text
    assert "有没有机械键盘" in text
    assert "有 3 款" in text
    assert "就买这个" not in text  # 最新一条用户消息单独呈现，不进历史
    assert "用户：" in text and "助手：" in text


def test_recent_history_truncates_long_content():
    state = {"messages": [HumanMessage(content="长" * 500), HumanMessage(content="最新")]}
    text = _recent_history_text(state, max_len=100)
    lines = [l for l in text.split("\n") if l]
    assert len(lines) == 1
    assert len(lines[0]) <= len("用户：") + 100
