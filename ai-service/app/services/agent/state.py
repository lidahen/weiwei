"""ShoppingState —— 电商 Agent 全局共享状态（方案 4.1）。"""
from typing import Annotated, Any, Dict, List, Optional, TypedDict

from langgraph.graph.message import add_messages


class ShoppingState(TypedDict):
    # ── 对话历史（add_messages 自动追加）──
    messages: Annotated[list, add_messages]

    # ── 用户上下文 ──
    user_id: str
    tenant_id: str                 # 租户 ID（X-Tenant-Id 注入，社区域工具做租户隔离）

    # ── 流程控制 ──
    intent: str                    # 当前意图（见方案 4.4 意图清单）
    confidence: float              # 意图置信度（< 0.6 进澄清节点）
    domain: str                    # 当前所在域：pre_sale / in_sale / after_sale / general
    current_agent: str             # 当前处理专家（agent 名称）

    # ── 业务数据 ──
    products: List[Dict[str, Any]]     # 商品卡片（已去重），用于前端展示
    tool_results: List[str]            # 工具返回的原始 JSON 串
    pending_confirm: str               # 待确认操作描述（L2/L3 复述用）
    pending_action: Dict[str, Any]     # 待确认动作参数
    idempotency_keys: Dict[str, bool]  # 已使用的幂等键（L3 防重复扣款）

    # ── 异常与结束 ──
    retry_count: int
    error: str
    final_reply: str
    finished: bool
    human_handoff: bool                # 是否转人工

    # ── 可观测性：单次运行 trace（工具调用明细 / LLM 迭代数），由 runner 汇总输出 ──
    trace: Dict[str, Any]

    # ── 知识库上下文 ──
    knowledge_id: Optional[int]       # 关联的知识库 ID（无则为 None）
    rag_context: str                  # RAG 检索到的文档片段，空字符串表示无

    # ── 长期记忆（memory_recall 节点写入，跨轮由 checkpoint 保留）──
    memory_context: str               # 已格式化好的记忆块（空=无记忆）；注入人设用

    # ── Java 注入的用户兴趣画像（interestHint），每轮刷新（spec §9.3）──
    interest_hint: str                # 空=本轮无画像；修复 runner 增量分支丢参数，见 §9.3

    # ── 历史摘要压缩（替代纯裁剪：旧对话提炼为要点，近期消息保持完整）──
    history_summary: str              # 已压缩历史的摘要（空=无），随 checkpoint 跨轮保留
    summary_upto: int                 # messages 前缀中已被摘要覆盖的条数


# 意图常量
INTENT_FIND_PRODUCT = "find_product"
INTENT_PRODUCT_DETAIL = "product_detail"
INTENT_FAVORITE = "favorite_manage"
INTENT_CHECKOUT = "checkout"
INTENT_ORDER_QUERY = "order_query"
INTENT_LOGISTICS = "logistics"
INTENT_AFTER_SALES = "after_sales"
INTENT_RUNNER = "runner_query"        # 互助悬赏：找互助任务/想接单/代取代买
INTENT_COMMUNITY = "community_ask"    # 社区近况：概况/热帖/日报/今天社区怎么样
INTENT_CHITCHAT = "chitchat"
INTENT_OTHER = "other"

ALL_INTENTS = [
    INTENT_FIND_PRODUCT,
    INTENT_PRODUCT_DETAIL,
    INTENT_FAVORITE,
    INTENT_CHECKOUT,
    INTENT_ORDER_QUERY,
    INTENT_LOGISTICS,
    INTENT_AFTER_SALES,
    INTENT_RUNNER,
    INTENT_COMMUNITY,
    INTENT_CHITCHAT,
    INTENT_OTHER,
]

# 意图 → 域映射
INTENT_DOMAIN = {
    INTENT_FIND_PRODUCT: "pre_sale",
    INTENT_PRODUCT_DETAIL: "pre_sale",
    INTENT_FAVORITE: "in_sale",
    INTENT_CHECKOUT: "in_sale",
    INTENT_ORDER_QUERY: "after_sale",
    INTENT_LOGISTICS: "after_sale",
    INTENT_AFTER_SALES: "after_sale",
    INTENT_RUNNER: "community",
    INTENT_COMMUNITY: "community",
    INTENT_CHITCHAT: "general",
    INTENT_OTHER: "general",
}

CONFIDENCE_THRESHOLD = 0.6
