"""llm_service —— 兼容层（LangGraph 重写后保留对外接口）。

原手写工具循环已迁移至 app.services.agent（LangGraph 主图 + 域子图）。
此文件保留 chat / chat_stream / _diagnose_llm_error / _get_llm 等旧符号，
使 app/routers/chat.py 等外部调用无需改动。
"""
from typing import List


from app.schemas.chat import ChatMsg

# 复用 agent 包内的实现
from app.services.agent.llm import _diagnose_llm_error, _get_llm  # noqa: E402,F401
from app.services.agent.runner import chat, chat_stream  # noqa: E402,F401

# ─────────────────────────────────────────────
# 以下为兼容旧代码的存量符号（新逻辑请勿使用）
# ─────────────────────────────────────────────
MAX_HISTORY = 20

SYSTEM_PROMPT = (
    "你是二手交易商城的 AI 导购助手。请根据用户用自然语言描述的需求,"
    "在需要时调用 search_products 工具检索在售商品,并依据检索结果用简洁友好的中文向用户推荐。\n"
    "规则:\n"
    "1. 只能推荐 search_products 工具返回的真实商品,绝不可臆造不存在的商品或价格;\n"
    "2. 若用户提到了价格、成色、分类、城市等约束,尽量映射为工具参数;\n"
    "3. 若没有匹配的商品,请如实告知并建议用户调整需求;\n"
    "4. 回复保持简短,不要逐条罗列全部字段,商品卡片会单独展示。"
)


def _build_messages(chat_messages: List[ChatMsg]) -> List:
    """旧版消息构建（仅兼容，新逻辑使用 agent.runner._to_lc_messages）。"""
    from langchain_core.messages import HumanMessage, AIMessage, SystemMessage

    trimmed = chat_messages[-MAX_HISTORY:]
    lc_messages: List = [SystemMessage(content=SYSTEM_PROMPT)]
    for msg in trimmed:
        lc_messages.append(
            HumanMessage(content=msg.content)
            if msg.role != "assistant"
            else AIMessage(content=msg.content)
        )
    return lc_messages
