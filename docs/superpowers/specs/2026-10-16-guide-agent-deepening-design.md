# 导购 Agent 深化（P0）设计 — 推荐融合 + 下单前多轮风险检查

> 日期：2026-10-16。来源：`docs/ai/AI功能评估-多余与深化-2026-10-15.md` 行动清单第 2 项（P0）。
> 前置事实：行动清单第 6 项（checkout 估价偏离提醒）已随提交 `c0f3277` 落地（`tools.py` 的
> `estimate_price` 工具、商品详情偏离对比、`confirm_order` 确认话术 `valuation_warning`），不在本次范围。

---

## 1. 背景与目标

评估文档指出的两个深度缺口：

1. **结果融合**：Agent 的商品检索（`search_products`）与 Java 六路召回推荐系统
   （`RecommendServiceImpl.doRecommend()`：兴趣类目/热门/新品/向量/协同/同城）是两张皮。
   Agent 现在调不到 Java 推荐——Python 侧只有向量通道（`/recommend/similar|candidates`），
   而 `/product/home/recommend` 的 userId 取自 `StpUtil.isLogin()`
   （ProductController.java:45），Python 无登录态，直调只能拿到匿名推荐，兴趣/协同/同城三路失效。
2. **多轮决策**：用户说"买这个"时，Agent 直接走下单确认，不检查议价状态、卖家纠纷历史、
   卖家活跃度——缺一层"带业务判断的 agent 行为"。

**目标**：Agent 检索与推荐系统打通；下单确认前自动前置只读风险检查并把结果如实转述给用户。

## 2. 范围

**做**：
- Java：新增 `InternalRecommendController`（服务间推荐端点，复用六路召回，推荐逻辑零改动）
- Python：新增 L1 工具 `recommend_products`；`confirm_order` 前置 `_pre_order_risk_check`
- SKILL.md（热加载）同步更新使用约束

**不做（YAGNI）**：
- 不在 Python 重排序/融合算法（六路打分已在 Java 完成，Python 重复打分是重复建设）；
  融合发生在模型层（两个工具结果由 LLM 合并呈现、按 product_id 去重）
- 不把收藏/浏览历史拉进 Agent 再排序（六路召回已含这些信号）
- 不做 interrupt 升级（保持既有两段式确认；`tools.py` 头注已声明可平滑升级）
- 不改 `search_products`、不动前端、不动既有 `/internal/trade/order/create`

## 3. 架构与数据流

```
【结果融合】
用户"有什么好推荐的/想买X"
  → pre_sale 子图：模型并行调 search_products（精确匹配）
                    + recommend_products（个性化推荐，新）
  → recommend_products ──httpx + X-Internal-Token──→ Java GET /internal/recommend/for-user
  → Java 复用 doRecommend 六路召回 + 打分 + reason
  → 模型按 product_id 去重后融合呈现："搜到的这几件 + 为你推荐（带理由）"

【多轮决策】
用户"买这个" → confirm_order（首次调用，未带 confirm_token）
  → 查商品 price + seller_id（扩展现有 SQL）
  → _pre_order_risk_check（只读，L1）：
      ① trade_bargain：buyer 的 pending 议价   → "你有一笔 ¥X 议价进行中"
      ② trade_dispute：卖家近 90 天未结纠纷数   → "该卖家近 90 天有 N 笔纠纷未结"
      ③ sys_user.login_date：卖家 30 天未登录  → "卖家已 X 天未登录，回复可能较慢"
  → 风险项与 valuation_warning 同模式拼入确认 message
  → 模型复述风险 → 用户仍可确认 → 二次调用走既有 /internal/trade/order/create（不变）
```

架构不变量：前端只与 Java BFF 通信；Agent 工具保持 L1 只读、不直写库；
模型调用只发生在 Python。

## 4. Java 侧设计

**新增文件**：`backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/InternalRecommendController.java`

- `@RestController @RequestMapping("/internal/recommend")`，`@Hidden`（不进 API 文档）
- 端点：`GET /for-user`，参数 `userId`（必填）、`catL1`（可选）、`pageSize`（默认 8，上限 20）
- 内部调用 `recommendService.homeRecommend(userId, catL1, 1, pageSize)`（公开接口方法，
  RecommendServiceImpl.java:188），返回 `JsonResult<TableDataInfo<Product>>`（含 `reason` 推荐理由）
- 内部降级保持 `homeRecommend` 自带行为（推荐链路异常回退时间倒序，ProductController.java:49-56 同款）

**安全设计（复刻 InternalOrderController 三道防线，InternalOrderController.java:31-38）**：
1. `/internal/**` 前缀，便于反向代理统一 deny，不暴露公网
2. `X-Internal-Token` 经 `InternalApiToken` 常量时间比对；未配置令牌一律拒绝；
   失败返回 403 且不区分"未配置"与"令牌错误"
3. 不接受调用方传租户；`@SaIgnore` 下 token 比对是唯一鉴权，不可省略

**租户上下文检查点（实现期验证）**：匿名首页推荐已在生产可运行，证明该链路不依赖登录态
租户上下文。实现时以测试验证内部调用路径；若发现推荐 SQL 依赖租户上下文，则按
`createOrderForUser` 的模式先从 `sys_user.tenant_id` 服务端解析租户再调用（同
InternalOrderController 的第 3 道防线精神），不得取调用方传参。

## 5. Python 侧设计

### 5.1 新工具 `recommend_products`（L1，加入 `PRE_SALE_TOOLS`）

位置：`ai-service/app/services/agent/tools.py`

```python
@tool
async def recommend_products(
    cat_l1: Optional[str] = None,
    limit: int = 8,
    user_id: Annotated[Optional[str], InjectedToolArg] = None,
) -> str:
    """[L1] 个性化商品推荐（基于平台六路召回：兴趣/热门/新品/向量/协同/同城）。
    用户想买某类东西但没明确目标、说"随便看看/有什么推荐"时，与 search_products 同时调用：
    search 给精确匹配，本工具给"为你推荐"（含推荐理由 reason，必须如实转述）。
    参数：cat_l1 一级分类（可空）、limit 条数（默认 8）。
    返回 JSON：{products: [{product_id, title, price, cover, condition, city, reason}], source: "recommend"}。
    调用失败时返回 error，此时改用 search_products 即可，不要向用户编造推荐结果。
    """
```

- httpx 调用复用 `settings.internal_api_token` / `settings.java_base_url` /
  `settings.java_internal_timeout`（与 `_java_internal_create_order` 同模式）
- 失败（未配令牌 / 超时 / 非 200）→ 返回 `{"error": ...}` 如实告知，不阻塞对话、不静默编造
- 结果映射 Java `rows` 为精简字段（product_id/title/price/cover/condition/city/reason）

### 5.2 `_pre_order_risk_check`（只读私有函数）

位置：`ai-service/app/services/agent/tools.py`；注入点：`confirm_order` 首次调用分支
（现有 `valuation_warning` 构造处，tools.py:711-753 附近）。

- 常量：`RISK_DISPUTE_WINDOW_DAYS = 90`、`RISK_SELLER_INACTIVE_DAYS = 30`
- 前置条件：`user_id` 为有效登录用户且 `product` 查询拿到 `seller_id`
  （`confirm_order` 现有 `SELECT price FROM product` 扩展为同时取 `seller_id`）
- 三个查询（全部只读）：
  1. `trade_bargain`：`product_id + buyer_id + status='pending'`，取最新一条 `current_price`
     → "你有一笔 ¥{X} 的议价进行中（卖家尚未回复），可等待议价结果再下单"
  2. `trade_dispute`：`seller_id + status IN ('pending','in_progress') + 近 90 天` 计数
     → "该卖家近 90 天有 {N} 笔纠纷未结，请留意"
  3. `sys_user.login_date`：距今超过 30 天 → "卖家已 {X} 天未登录，回复可能较慢"
     （`login_date` 为 NULL 视为未知，跳过不提示）
- 每个查询独立 try/except：失败记 `logger.warning` 并跳过该项（与 tools.py:749 估价提示
  降级模式一致），**任何情况下不阻塞下单流程**
- 输出拼入确认 message：`risk_notes` 列表 → 与 `valuation_warning` 同格式拼接；
  二次调用（携带 confirm_token 真正执行）不重复检查

### 5.3 SKILL.md 更新（热加载）

`ai-service/skills/guide-agent/SKILL.md` 追加：
1. 推荐场景同时调 search_products + recommend_products，按 product_id 去重融合，
   `reason` 必须如实转述；recommend 失败时改用 search，不编造推荐
2. 确认下单话术必须如实带出风险提示（议价进行中/卖家纠纷/卖家活跃度/估价偏离），不得略过

## 6. 错误处理策略

| 故障 | 行为 |
|---|---|
| Java 内部推荐端点超时/非 200 | recommend_products 返回 error；模型改用 search_products；对话不中断 |
| `INTERNAL_API_TOKEN` 未配置 | 同上（与下单工具同一令牌，配置一次两处生效） |
| risk check 单项查询失败 | 记 warning 跳过该项，其余项照常 |
| risk check 整体异常 | 记 warning，确认话术退回现有形态（仅估价偏离提示） |
| homeRecommend 整链异常 | 内部端点 500（通道级降级在 doRecommend 内部各召回通道自带）；公开端点的整链 catch 不随服务方法走；Python 侧对非 200 返回 error 并改用 search_products |

## 7. 测试策略

- **Python pytest**（mock httpx / mock DB session，跟随 `ai-service/tests/` 既有模式）：
  - recommend_products：成功映射 / 超时降级 / 未配令牌三态
  - `_pre_order_risk_check`：三个条件各自命中、全不命中、部分失败跳过
  - `confirm_order`：确认话术含风险文本；二次调用不重复检查
- **回归**：跑既有 `agent_eval` 路由评测（26 条，`ai-service/tests/agent_eval/run_router_eval.py`；
  与 `moderation_eval` 平级、互不相干），确认新增工具不影响意图路由准确率
  （路由 prompt 未改，预期无回归）
- **Java**：跟随 `InternalOrderController` 既有测试模式（有测试则补 token 校验 403 用例；
  无则以 `mvn compile` + 联调验证），并验证 §4 租户上下文检查点

## 8. 验收标准

1. Agent 对"随便看看有什么好的"能同时给出搜索结果与带 reason 的个性化推荐；
   recommend 链路故障时对话不中断
2. Agent 对"买这个"的确认话术包含：估价偏离提示（已有）+ 议价/纠纷/活跃度风险（新增，
   命中时），用户确认后下单走既有 Java 内部端点
3. 内部推荐端点无 token 返回 403；未配置令牌时拒绝而非放行
4. 既有路由评测无回归；Python 新增用例全绿；`mvn compile` 通过
