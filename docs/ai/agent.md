# 电商购物助手技术架构设计文档（智能电商 Agent）

> 基于 LangGraph + LangChain 混合架构，覆盖售前导购、售中下单、售后客服的一体化购物助手

---

## 📋 文档版本信息

| 项目 | 内容 |
| :--- | :--- |
| **文档版本** | v2.1（对接项目实际：C2C 二手交易 + 收藏替代购物车 + 售后纠纷仲裁 + 地址确认） |
| **创建日期** | 2026-08-11 |
| **目标场景** | 电商购物助手（一体型：售前导购 + 售中下单 + 售后客服） |
| **核心技术栈** | LangGraph (运行时/编排) + LangChain (模型/工具抽象) |
| **文档状态** | 架构设计定稿（v2.0） |

---

## 一、产品定位与系统目标

### 1.1 定位：一体型购物助手（C 方案）

本系统不是被动应答的"智能客服"，而是完整的**购物助手 Agent**，一条对话可横跨交易全链路：

| 域 | 能力 | 示例 |
| :--- | :--- | :--- |
| **售前（导购）** | 商品推荐、商品咨询、比价、优惠活动、搭配建议 | "帮我找一款 500 元以内降噪好的耳机" |
| **售中（下单）** | 收藏管理、下单确认（含收货地址确认）、支付 | "帮我把这个收藏，顺便下单" |
| **售后（服务）** | 订单查询、物流追踪、纠纷仲裁（agent 先处理，处理不了转人工） | "我昨天买的手机物流到哪了" |

**关键差异**：客服是"一次分发、走到结束"；购物助手是**多轮状态机**——用户可随时在售前/售中/售后之间切换（比价 → 收藏 → 下单 → 查物流 → 纠纷仲裁），路由必须支持**域间流转**。

### 1.2 核心设计原则

1. **域内子图、域间流转**：三个域各建子图，域内线性处理；主图只负责跨域切换，保证主图简单、子图可复用。
2. **操作分级**：查询类自动执行、修改类二次确认、资金类强制人工确认（见 4.5），杜绝"一刀切转人工"或"无确认直接下单"两个极端。
3. **意图由 LLM 分类 + 置信度阈值 + 澄清节点**：模糊意图反问澄清，绝不强行分发（解决旧版关键词匹配的误判问题）。
4. **防御纵深**：入口安全过滤 → 节点级捕获 → 全局兜底 → 顶层终结者，异常处理详见专项文档。

---

## 二、技术选型结论

### 2.1 核心决策

采用 **LangGraph + LangChain 混合架构**，遵循"**LangGraph 管流程，LangChain 管能力**"的职责分离原则。

### 2.2 选型理由

| 组件 | 选型 | 核心职责 | 关键理由 |
| :--- | :--- | :--- | :--- |
| **运行时/编排** | **LangGraph** | 状态管理、子图嵌套、流程路由、持久化、HITL、循环控制 | 购物助手的跨域多轮流转 + 资金操作人工确认，天然需要图编排与中断能力 |
| **能力抽象** | **LangChain** | 模型统一调用、工具定义、结构化输出、RAG 检索 | 标准化组件抽象，与 LangGraph 无缝集成 |
| **可观测性** | **LangSmith** | 全链路追踪、调试、性能监控 | Agent 生产级必备 |
| **持久化** | **Redis** | Checkpointer 后端存储 | 会话中断恢复、跨线程状态管理 |

### 2.3 模型选型（决策待定项，默认国产模型）

| 方案 | 说明 | 建议 |
| :--- | :--- | :--- |
| **DeepSeek / 通义千问**（默认） | 中文商品语义理解好、价格低，兼容 OpenAI 协议（`base_url` 可配） | **MVP 首选**，成本可控 |
| GPT-4o | 综合能力最强，成本高 | 若预算充足或需多模态（看图找同款）再升级 |

> 代码模板使用 `ChatOpenAI(base_url=...)`，可通过环境变量无缝切换任意 OpenAI 兼容模型，无需改代码。

### 2.4 数据源（MVP 决策待定项）

| 系统 | MVP 阶段 | 生产阶段 |
| :--- | :--- | :--- |
| 商品库 / 库存 | mock 数据 + 预留 REST API | 对接真实商品服务（分页 + 截断） |
| 订单系统 | mock | 对接真实订单 API（幂等键） |
| 支付 | **模拟支付闭环**（不做真实扣款） | 接入支付宝/微信，L3 操作强制 HITL |

---

## 三、系统分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    第五层：用户接入层                           │
│          (Web/App 聊天窗口 + API Gateway + 鉴权 + 分布式锁)    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    第四层：流程编排层 (LangGraph)               │
│  ┌────────┐  ┌────────┐  ┌──────────┐  ┌────────┐  ┌─────────┐ │
│  │ 入口   │→ │ 路由   │→ │ 售前子图 │  │ 澄清   │  │ 结束/   │ │
│  │ 节点   │  │ 节点   │  │ 售中子图 │→ │ 节点   │  │ 人工    │ │
│  │        │  │(LLM   │  │ 售后子图 │  │ 闲聊   │  │         │ │
│  │        │  │ 分类)  │  │ 域间流转 │  │ 节点   │  │         │ │
│  └────────┘  └────────┘  └──────────┘  └────────┘  └─────────┘ │
│                    ↑ State 驱动 + 子图嵌套 ↑                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    第三层：智能能力层 (LangChain)               │
│  ┌───────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │
│  │ ChatModel │ │ @tool    │ │ Parser   │ │ RAG Retriever  │  │
│  │ (统一抽象)│ │ (L1/L2/L3)│ │(结构化输出)│ │ (商品知识库)   │  │
│  └───────────┘ └──────────┘ └──────────┘ └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    第二层：服务支撑层                           │
│  (商品API / 收藏 / 订单API / 支付网关 / 纠纷工单 / 通知)      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    第一层：基础设施层                           │
│       (LLM 模型 / Redis / 向量库 / LangSmith / Docker)        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、核心模块设计

### 4.1 全局状态 (State) —— 系统的"共享白板"

```python
from typing import TypedDict, List, Dict, Any, Annotated
from langgraph.graph.message import add_messages

class ShoppingState(TypedDict):
    # ── 对话历史 (add_messages 自动追加) ──
    messages: Annotated[List[Dict], add_messages]

    # ── 用户信息 ──
    user_id: str
    user_info: Dict[str, Any]

    # ── 流程控制 ──
    intent: str                      # 当前意图（见 4.4 意图清单）
    confidence: float                # 意图置信度（< 0.6 进澄清节点）
    domain: str                      # 当前所在域：pre_sale / in_sale / after_sale / general
    current_agent: str               # 当前处理专家
    requires_human: bool             # 是否需要人工介入
    is_complete: bool                # 会话是否结束

    # ── 电商业务数据 ──
    current_product: Dict[str, Any]  # 当前浏览/比价商品
    favorites: List[Dict[str, Any]]  # 收藏的商品（替代购物车）
    order_info: Dict[str, Any]       # 当前订单
    payment_status: str              # 支付状态: unpaid / paid / failed
    shipping_address: Dict[str, Any] # 当前收货地址（来自 user_address，下单前确认）
    coupon_info: Dict[str, Any]      # 优惠计算（平台暂未上线优惠券，字段预留）
    operation_level: str             # 本次操作等级: L1 查询 / L2 修改 / L3 资金
    idempotency_key: str             # 幂等键（防重复下单/重复扣款）
    ticket_id: str                   # 售后工单号

    # ── 异常处理 ──
    error: str
    retry_count: int
    repair_count: int
    logs: List[str]
```

### 4.2 核心节点与子图（三层结构）

**主图节点：**

| 节点名称 | 职责 | 输入 | 输出 |
| :--- | :--- | :--- | :--- |
| **入口节点** | 接收消息、安全过滤（注入/超长/空输入）、分布式锁、初始化 | 用户原始消息 | 标准化消息 + 用户ID |
| **路由节点** | LLM 意图分类（结构化输出），低置信度转澄清 | 用户消息 + 上下文 | intent + confidence + 目标节点 |
| **澄清节点** | 意图模糊时反问用户，待回复后重回路由 | 模糊意图 | 澄清问题（不消耗业务工具） |
| **闲聊节点** | 闲聊、寒暄、菜单引导 | 一般性问题 | 轻松回复 + 能力引导 |
| **人工介入节点** | interrupt 挂起转人工，交接完整上下文 | 当前完整状态 | 工单 + 交接上下文 |
| **结束节点** | 汇总答案、结束会话、写回 Checkpointer | 处理结果 | 最终回复 + 结束标记 |
| **错误处理节点** | 统一兜底：记录 + 告警 + 安抚 + 转人工 | 错误信息 + 状态快照 | 安抚话术 + 转人工 |

**子图节点（三个域）：**

| 子图 | 节点 | 操作等级 | 工具示例 |
| :--- | :--- | :--- | :--- |
| **售前子图** | 导购推荐节点、商品咨询节点、比价节点 | L1 | `search_products` / `get_product_detail` / `compare_products` |
| **售中子图** | 收藏节点、地址确认节点、下单确认节点、支付节点 | L1 查询 / L2 修改 / L3 资金 | `add_to_favorite` / `get_default_address` / `confirm_order` / `pay_order` |
| **售后子图** | 订单查询节点、物流追踪节点、纠纷处理节点 | L1 查询 / L3 仲裁 | `query_order` / `track_logistics` / `create_dispute` |

### 4.3 路由逻辑（LLM 意图分类 + 置信度阈值 + default 兜底）

```
入口节点 ──→ 路由节点（LLM 分类，输出 intent + confidence）
               │
      ┌────────┼──────────────┬──────────────┬──────────────┐
      ▼        ▼              ▼              ▼              ▼
 confidence  售前子图       售中子图       售后子图     闲聊节点/澄清节点
 < 0.6 或意图
 不在清单内
   │
   ▼
 澄清节点 ──→ 回到路由节点（用户补充信息后重新分类）
```

- 路由函数**必须有 `default` 万能兜底分支**，意图不在清单内时进澄清节点，永不崩溃（异常文档场景 24）。
- 域间流转：用户任意时刻说"我要退货"（原在售中子图），主图条件边检测到域变化，**从当前子图跳出回到主图**，重新路由到售后子图。

### 4.4 意图清单

**MVP 阶段（8 个，覆盖最小交易闭环：找商品→收藏→下单→查物流→纠纷仲裁）：**

| 域 | 意图 | 说明 |
| :--- | :--- | :--- |
| 售前 | `find_product` | 找商品/推荐 |
| 售前 | `product_detail` | 商品详情咨询 |
| 售中 | `favorite_manage` | 收藏/查收藏/取消收藏 |
| 售中 | `checkout` | 下单确认 + 支付 |
| 售后 | `order_query` | 查订单 |
| 售后 | `logistics` | 查物流 |
| 售后 | `after_sales` | 售后纠纷（agent 仲裁，处理不了转人工） |
| 通用 | `chitchat` | 闲聊、求助人工、菜单引导 |

**全量阶段（20 个，上线后按真实对话数据补齐）：**

| 域 | 意图 |
| :--- | :--- |
| 售前 | find_product、product_detail、compare（比价）、promotion（优惠活动）、recommend（搭配推荐） |
| 售中 | favorite_manage、checkout（下单+地址确认）、address_edit（改地址）、payment（支付） |
| 售后 | order_query、logistics、after_sales（纠纷仲裁）、dispute_appeal（申诉转人工）、complaint（投诉） |
| 通用 | chitchat、human_help（求助人工）、other（→澄清） |

> 意图清单只是路由映射表，新增意图 = 加一个映射 + 一个子图节点，**不改变架构**，因此 MVP 先做 8 个、后续按需扩展成本极低。

### 4.5 操作分级（L1 / L2 / L3）

| 等级 | 操作示例 | 策略 | 异常兜底 |
| :--- | :--- | :--- | :--- |
| **L1 查询** | 查商品、比价、查物流、算优惠 | 工具直接执行，无需确认 | 失败自动重试 → 降级话术 |
| **L2 修改** | 改地址、改收藏 | 执行前**复述确认**（"将修改为 X，对吗？"） | 用户确认后才落库 |
| **L3 资金** | 下单、支付、纠纷仲裁 | 强制 `interrupt` 用户/人工确认 + **幂等键** + 风控规则（高频下单拦截） | 幂等键防重复扣款，异常走人工审核 |

**实施要点：**
- 工具层按等级打标（`@tool` docstring 中注明），路由/确认节点根据等级决定是否插入确认步骤。
- L3 工具必须使用 `idempotency_key`（`user_id + 时间窗`）写入请求头，下游据此去重（异常文档场景 11）。
- 入口处 Redis 分布式锁（`setnx` + TTL），同一 `thread_id` 并发请求直接返回"请求处理中"（异常文档场景 40）。

### 4.6 域间流转（跨域切换）

用户在售中子图下单后说"我顺便退一个之前买的"，流程：

```
售中子图(下单完成) ──[域切换检测]──→ 主图 ──→ 路由节点 ──→ 售后子图(纠纷仲裁)
```

实现：子图入口节点读取 `state["domain"]`，若与当前子图域不一致则抛出让步信号（或子图设置 `domain` 变更后由主图条件边重新路由）。MVP 简化方案：**每轮对话都从主图路由节点进入**（不强求子图内多轮停留），域间切换天然支持，代价是每轮多一次分类调用。

---

## 五、异常处理体系（8 维度 55+ 场景全覆盖）

> 详细设计见《agent异常处理.md》专项文档，此处仅列总览。

### 5.1 八大异常维度总览

| 维度 | 场景数 | 核心策略 |
| :--- | :--- | :--- |
| **LLM 推理异常** | 8 | 重试、格式校验、降级、摘要压缩 |
| **工具/API 异常** | 8 | 超时、幂等重试、熔断降级、数据截断 |
| **状态管理异常** | 6 | Reducer 合并、序列化、脱敏、压缩 |
| **流程路由异常** | 7 | 置信度判定、默认分支、计数器限流 |
| **人机协同异常** | 5 | 超时中断、状态恢复、取消转接 |
| **基础设施异常** | 6 | 持久化恢复、冷启动、分布式锁 |
| **用户输入异常** | 5 | 安全守卫、截断、标准化、乱序重排 |
| **业务与交易异常**（新增） | 10+ | 幂等对账、自动兜底（推荐替代）、价格复核、风控拦截 |

> 重要调整：旧版"所有异常一律转人工"改为**按严重等级 + 操作等级分流**——业务异常多为 P2 自动兜底（如库存不足 → 推荐替代品），仅资金纠纷/风控命中为 P1 强制人工。

### 5.2 关键异常处理机制

#### 机制一：全局兜底节点

```python
def error_handler_node(state: ShoppingState):
    """统一错误处理：记录日志 + 安抚用户 + 按等级转人工"""
    error_msg = state.get("error", "未知错误")
    log_error(state["user_id"], error_msg, state["logs"])
    send_alert(f"购物助手异常: {error_msg}")
    state["messages"].append({
        "role": "assistant",
        "content": "抱歉，系统暂时遇到问题，已为您转接人工客服。"
    })
    state["requires_human"] = True
    return state
```

#### 机制二：重试与步数限制

```python
from tenacity import retry, stop_after_attempt, wait_exponential

@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
def call_external_api(url, params, idempotency_key):
    # L3 资金类操作必须携带幂等键，重试不产生重复扣款
    pass

# recursion_limit 必须放进 config（不是 stream 参数），见 6.6
config = {"recursion_limit": 30, "configurable": {"thread_id": "user_12345"}}
```

#### 机制三：状态持久化与恢复

```python
from langgraph.checkpoint.memory import MemorySaver  # MVP 用内存
# 生产环境: pip install langgraph-checkpoint-redis
# from langgraph.checkpoint.redis import RedisSaver

checkpointer = MemorySaver()  # MVP；生产替换为 RedisSaver
graph = builder.compile(checkpointer=checkpointer)

# 从上次中断处继续执行（HITL 恢复、Pod 重启恢复）
for event in graph.stream(input_data, config=config, stream_mode="values"):
    pass
```

### 5.3 异常处理流程图

```
                     ┌─────────────┐
                     │  节点执行   │
                     └──────┬──────┘
                            │
                  ┌─────────┴─────────┐
                  │                   │
                  ▼                   ▼
            执行成功              执行失败
                  │                   │
                  ▼                   ▼
            正常路由            ┌─────┴─────┐
                              │           │
                              ▼           ▼
                        可自动兜底     需人工介入
                     (P2:重试/替代/降级) (P1/P0:转人工+记录)
                              │           │
                              ▼           ▼
                         继续流程     error_handler
```

---

## 六、LangGraph + LangChain 混合代码模板

### 6.1 依赖安装

```bash
pip install langgraph langchain langchain-openai langchain-community redis tenacity
# 生产环境 Checkpointer（MVP 可用 MemorySaver 免装）
pip install langgraph-checkpoint-redis
```

### 6.2 定义状态与工具（L1/L2/L3 分级）

```python
from typing import Annotated, List, Dict, TypedDict
from langgraph.graph import StateGraph, MessagesState
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode
from langchain_openai import ChatOpenAI
from langchain.tools import tool
from langgraph.checkpoint.memory import MemorySaver

class ShoppingState(MessagesState):
    user_id: str
    intent: str
    confidence: float
    domain: str
    requires_human: bool
    is_complete: bool
    current_product: Dict
    favorites: List[Dict]
    order_info: Dict
    payment_status: str
    operation_level: str
    idempotency_key: str
    error: str
    retry_count: int

# ═══ 工具层：按操作等级组织（L1 查询 / L2 修改 / L3 资金）═══

@tool
def search_products(keyword: str, max_price: float = None) -> str:
    """[L1] 根据关键词和价格上限搜索商品，返回商品列表（最多 3 条）"""
    # MVP: mock 数据；生产: 调商品服务 REST API，注意结果截断（异常文档场景 12）
    return "为您找到：\n1. {keyword}旗舰版 ¥399 好评98%\n2. {keyword}青春版 ¥299 好评95%".format(keyword=keyword)

@tool
def add_to_favorite(product_id: str) -> str:
    """[L2] 收藏商品（平台无购物车，收藏替代加购）。执行前必须由确认节点向用户复述。"""
    # L2 操作：确认节点先行，用户确认后才真正调用
    return f"已收藏商品 {product_id}"

@tool
def confirm_order(user_id: str, idempotency_key: str) -> str:
    """[L3] 提交订单。必须携带幂等键，防重复下单；失败时用查询接口对账而非盲目重试。"""
    # 幂等键 = user_id + 时间窗，写入请求头
    return f"订单已提交（幂等键 {idempotency_key}），等待支付"

@tool
def query_order(order_id: str) -> str:
    """[L1] 查询订单状态"""
    return f"订单 {order_id} 已发货，预计 2 天后送达"

# 模块级复用 ToolNode（不要在每个节点里新建）
tools = [search_products, add_to_favorite, confirm_order, query_order]
tool_node = ToolNode(tools)
```

### 6.3 绑定模型（结构化输出做意图分类）

```python
from pydantic import BaseModel, Field

class IntentOutput(BaseModel):
    intent: str = Field(description="意图，取值为: find_product/product_detail/favorite_manage/checkout/order_query/logistics/after_sales/chitchat/other")
    confidence: float = Field(description="置信度 0-1")

model = ChatOpenAI(
    model="deepseek-chat",                 # 可替换: gpt-4o / qwen-max 等
    base_url="https://api.deepseek.com/v1",  # OpenAI 兼容协议，换模型只改这里
    temperature=0.1,
)
router_model = model.with_structured_output(IntentOutput)
expert_model = model.bind_tools(tools)
```

### 6.4 定义主图节点

```python
def entry_node(state: ShoppingState):
    """入口：初始化 + 安全过滤（超长截断 / 空输入拦截）"""
    state["retry_count"] = 0
    state["error"] = ""
    last = state["messages"][-1].content
    if len(last) > 2000:                      # 异常文档场景 42
        state["messages"].append({"role": "assistant", "content": "内容过长，请精简后重试"})
        state["is_complete"] = True
    return state

def router_node(state: ShoppingState):
    """路由：LLM 意图分类，低置信度进澄清"""
    result = router_model.invoke([{"role": "system",
        "content": "你是电商购物助手路由。将用户意图分类到: find_product/product_detail/favorite_manage/checkout/order_query/logistics/after_sales/chitchat/other"}]
        + state["messages"][-3:])
    state["intent"] = result.intent
    state["confidence"] = result.confidence
    if result.confidence < 0.6 or result.intent == "other":   # 异常文档场景 23
        return {"intent": "clarify"}
    return {"intent": result.intent}

def clarify_node(state: ShoppingState):
    """澄清：意图模糊时反问，不消耗业务工具"""
    state["messages"].append({
        "role": "assistant",
        "content": "抱歉没完全理解您的意思。您是想：① 找商品 ② 查订单/物流 ③ 售后纠纷 ④ 下单？请告诉我具体需求。"
    })
    state["is_complete"] = True
    return state

def chitchat_node(state: ShoppingState):
    """闲聊兜底"""
    state["messages"].append({"role": "assistant", "content": "我可以在售前、下单、售后三个环节帮您~比如找商品、下单、查物流、处理售后纠纷。"})
    state["is_complete"] = True
    return state

def human_review_node(state: ShoppingState):
    """人工介入（HITL）：真实场景用 interrupt 挂起，由外部 Webhook 唤醒"""
    state["requires_human"] = True
    state["messages"].append({"role": "assistant", "content": "已为您转接人工客服，请稍候..."})
    state["is_complete"] = True
    return state

def error_handler_node(state: ShoppingState):
    state["requires_human"] = True
    state["messages"].append({"role": "assistant",
        "content": "抱歉，系统暂时遇到问题，已为您转接人工客服。"})
    state["is_complete"] = True
    return state

def end_node(state: ShoppingState):
    state["is_complete"] = True
    return state
```

### 6.5 子图：售中子图（收藏 → 地址确认 → 下单确认 → 支付）

```python
def build_in_sale_subgraph():
    """售中子图：域内线性流程。售前/售后子图结构相同，不再赘述"""
    sub = StateGraph(ShoppingState)

    def favorite_node(state):
        # L2: 先复述确认再调用 add_to_favorite
        state["messages"].append({"role": "assistant",
            "content": f"确认收藏该商品吗？当前收藏：{state.get('favorites', [])}"})
        state["operation_level"] = "L2"
        return state

    def checkout_node(state):
        # L3: 生成幂等键，强制确认；地址来自 user_address 默认地址快照
        state["idempotency_key"] = f"{state['user_id']}-{state['messages'][-1].content[:16]}"
        state["operation_level"] = "L3"
        state["messages"].append({"role": "assistant",
            "content": f"请确认订单金额 {state.get('order_info', {}).get('total_amount')} 元，"
                       f"收货地址：{state.get('shipping_address', '请先选择地址')}，"
                       "确认后提交（此为资金操作，不会重复扣款）。"})
        return state

    sub.add_node("favorite", favorite_node)
    sub.add_node("checkout", checkout_node)
    sub.add_node("tools", tool_node)
    sub.add_node("end", end_node)
    sub.set_entry_point("favorite")
    sub.add_edge("favorite", "tools")    # tools 中实际执行 L2 确认后动作
    sub.add_edge("tools", "checkout")
    sub.add_edge("checkout", "tools")
    sub.add_edge("checkout", "end")
    sub.set_finish_point("end")
    return sub.compile()
```

### 6.6 构建主图并运行

```python
def build_graph():
    builder = StateGraph(ShoppingState)

    builder.add_node("entry", entry_node)
    builder.add_node("router", router_node)
    builder.add_node("clarify", clarify_node)
    builder.add_node("chitchat", chitchat_node)
    builder.add_node("human_review", human_review_node)
    builder.add_node("error_handler", error_handler_node)
    builder.add_node("end", end_node)
    builder.add_node("in_sale", build_in_sale_subgraph())   # 子图作为节点嵌套
    # 售前/售后子图同上：builder.add_node("pre_sale", ...) / ("after_sale", ...)

    builder.set_entry_point("entry")
    builder.add_edge("entry", "router")

    # 意图 → 目标节点（default 兜底进澄清，永不崩溃 —— 异常文档场景 24）
    builder.add_conditional_edges("router", lambda s: s["intent"], {
        "find_product": "in_sale",      # MVP: 售前暂由售中子图占位，正式版接 pre_sale 子图
        "favorite_manage": "in_sale",
        "checkout": "in_sale",
        "order_query": "in_sale",       # MVP 占位，正式版接 after_sale 子图
        "logistics": "in_sale",
        "after_sales": "in_sale",
        "chitchat": "chitchat",
        "clarify": "clarify",
        "__default__": "clarify",
    })

    builder.add_edge("clarify", "end")
    builder.add_edge("chitchat", "end")
    builder.add_edge("human_review", "end")
    builder.add_edge("error_handler", "end")
    return builder

if __name__ == "__main__":
    graph = build_graph().compile(checkpointer=MemorySaver())  # 生产: RedisSaver

    # recursion_limit 必须放在 config 里（不是 stream 参数）
    config = {"configurable": {"thread_id": "user_12345"}, "recursion_limit": 30}

    input_data = {
        "messages": [{"role": "user", "content": "帮我找一个 500 元以内的降噪耳机"}],
        "user_id": "user_12345",
        "domain": "pre_sale",
        "favorites": [],
    }

    try:
        for event in graph.stream(input_data, config=config, stream_mode="values"):
            if event.get("messages"):
                print(event["messages"][-1].content)
    except Exception as e:                     # 顶层终结者模式（异常文档 10.2）
        print(f"图执行异常: {e}")
        # 记录 Fatal 日志 + 紧急告警 + 触发人工兜底
```

---

## 七、生产环境部署清单

### 7.1 必备组件

| 组件 | 用途 | MVP 方案 | 生产方案 |
| :--- | :--- | :--- | :--- |
| **Checkpointer 存储** | 会话状态持久化 | MemorySaver（单机） | Redis |
| **LLM API** | 模型推理 | DeepSeek / 通义（默认） | 按预算切换 GPT-4o |
| **向量数据库** | RAG 商品知识库检索 | Chroma（本地） | Milvus / Pinecone |
| **可观测性** | 全链路追踪 | LangSmith | LangSmith |
| **容器编排** | 服务部署 | **Docker Compose** | Kubernetes |
| **日志聚合** | 日志收集分析 | 文件 + ELK | ELK / Datadog |

### 7.2 环境变量配置

```bash
# .env 示例
LLM_API_KEY=sk-xxx
LLM_BASE_URL=https://api.deepseek.com/v1   # 换模型只改这里
MODEL_NAME=deepseek-chat
REDIS_URL=redis://localhost:6379
LANGSMITH_API_KEY=ls_xxx
RECURSION_LIMIT=30
TOOL_TIMEOUT=30
HUMAN_WAIT_TIMEOUT=300
CONFIDENCE_THRESHOLD=0.6
CART_LIMIT=50
```

### 7.3 监控指标建议

| 指标 | 说明 | 告警阈值 |
| :--- | :--- | :--- |
| `graph_completion_rate` | 图执行完成率 | < 95% |
| `clarify_rate` | 澄清率（意图模糊占比，过高说明路由需优化） | > 30% |
| `human_escalation_rate` | 人工介入率 | > 20% |
| `checkout_conversion_rate` | 下单转化率（售前→售中核心指标） | 关注趋势 |
| `payment_failure_rate` | 支付失败率（L3 核心指标） | > 2% |
| `avg_response_time` | 平均响应时间 | > 5s |
| `recursion_limit_reached` | 步数超限次数 | > 10/天 |

---

## 八、实施路线图

### Phase 1: MVP（1-2 周）—— 8 个意图跑通最小交易闭环
- [ ] 搭建 LangGraph + LangChain 框架，State 定义（含电商字段）
- [ ] 入口/路由/澄清/闲聊/结束 五个基础节点
- [ ] LLM 意图分类（8 意图 + 置信度阈值 + default 兜底）
- [ ] 售中子图占位打通：找商品 → 收藏 → 下单确认（mock 数据 + 模拟支付）
- [ ] 工具层 L1/L2/L3 分级 + 幂等键
- [ ] 本地调试通过

### Phase 2: 核心能力建设（2-3 周）
- [ ] 拆出真正的售前子图（导购推荐 + RAG 商品知识库）与售后子图（订单/物流/纠纷仲裁）
- [ ] 接入真实商品/订单 REST API（分页截断 + 超时重试）
- [ ] 域间流转：比价 → 收藏 → 下单 → 查物流 → 纠纷仲裁 全链路
- [ ] 业务异常处理：库存不足推荐替代、价格变动复核、地址不合法校验

### Phase 3: 生产就绪（2 周）
- [ ] Redis Checkpointer 持久化 + 会话恢复
- [ ] 接入 LangSmith 全链路追踪
- [ ] HITL：L3 资金操作 interrupt 人工确认 + Webhook 恢复
- [ ] 入口安全加固（Prompt 注入守卫、限流、分布式锁）
- [ ] 压力测试 + 异常场景覆盖验证

### Phase 4: 上线与优化（持续）
- [ ] 灰度发布，逐步切流
- [ ] 按真实对话数据扩充意图（8 → 20）
- [ ] 监控澄清率/转化率，优化路由 Prompt 与推荐策略
- [ ] 持续补充业务异常场景

---

## 九、关键决策记录

| 决策点 | 选择 | 理由 |
| :--- | :--- | :--- |
| 产品定位 | 一体型购物助手（售前+售中+售后） | 一条对话闭环交易全链路 |
| 编排框架 | LangGraph | 多轮跨域状态机 + 子图嵌套 + HITL |
| 能力抽象 | LangChain | 标准化工具/模型接口 |
| 意图路由 | LLM 分类 + 置信度阈值 + 澄清 + default 兜底 | 替代关键词匹配，解决模糊意图 |
| 节点结构 | 域内子图 + 域间流转 | 主图简单、子图复用 |
| 操作分级 | L1 自动 / L2 确认 / L3 人工+幂等 | 防止误操作与重复扣款 |
| 状态存储 | Redis（MVP 用 MemorySaver） | 高性能、分布式友好 |
| 可观测性 | LangSmith | 原生集成 |
| 异常处理 | 8 维度 55+ 场景，按等级分流 | 业务异常自动兜底，不一律转人工 |
| 意向暂存 | 收藏（`product_favorite`）替代购物车 | 平台无购物车表，C2C 直拍模式 |
| 售后形态 | agent 先处理纠纷仲裁，处理不了转人工工单 | 平台 C2C 纠纷工单（CsDispute）+ HITL 兜底 |
| 售中地址 | 确认节点展示 `user_address` 默认地址快照 | 地址表存在但下单接口未接，需扩展 createOrder(addressId) |
| 落地方式 | 重写现有 `twelvet-ai-service/llm_service.py` 手写工具循环为 LangGraph 图 | 现有代码仅单工具直通，无法承载意图路由/子图/HITL |
| 模型（待定） | DeepSeek/通义（默认，可替换 GPT-4o） | 中文语义好、成本低、OpenAI 兼容 |

---

## 十、参考资料

可以参考"F:\面试\langchain\langchain-docs-python"，"F:\面试\langchain\langgraph-docs"相关文档

---

## 附录：异常场景速查清单（8 维度 55+）

<details>
<summary>点击展开完整异常清单</summary>

| 维度 | 异常场景 |
| :--- | :--- |
| **LLM** | 结构化输出失效、工具参数错乱、安全拒答、推理死循环、上下文遗忘、RAG空结果、多模态解析失败、流式中断 |
| **工具/API** | 鉴权过期、服务熔断、非幂等重试、超大Payload、编码异常、Webhook超时、连接池耗尽、相似度异常 |
| **状态管理** | Reducer冲突、序列化失败、跨线程污染、长期记忆错误、状态膨胀OOM、PII泄露 |
| **流程路由** | 置信度低、目标节点不存在、未捕获异常、子图输入不匹配、条件边死循环、并行竞态、类型校验失败 |
| **人机协同** | 排队超时、误操作关单、恢复时上下文丢失、用户拒绝转接、人工数据格式错误 |
| **基础设施** | Pod重启、冷启动延迟、网络分区、日志磁盘满、内存泄漏、分布式锁超时 |
| **用户输入** | 空输入、超长文本、Prompt注入、混合编码、消息乱序 |
| **业务与交易**（新增） | 支付失败/重复扣款、库存不足/超卖、下单时价格变动、商品下架/售罄、地址不合法/未填地址、恶意高频下单、纠纷仲裁争议、跨境/税费异常（优惠券相关：平台暂未上线，预留扩展） |

</details>

---

*文档结束 — v2.0 更新于 2026-08-11（定位：一体型电商购物助手）*
