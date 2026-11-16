# AI 导购功能 设计文档

- 日期：2026-06-08
- 主题：AI 导购（自然语言筛选商品并以卡片展示，点击跳转详情）
- 状态：待用户审查（设计获批前不编写任何实现代码）

## 1. 概述

在二手交易商城（前端 `twelvet-ui-master` + 后端 `twelvet-fast`）中新增「AI 导购」功能：用户在对话式页面用自然语言描述购物需求，后端 LLM 通过工具调用（function calling）检索商城在售商品，返回推荐文案 + 结构化商品列表；前端以商品卡片网格展示，点击卡片跳转到对应商品详情页。

## 2. 已确认决策（来自头脑风暴）

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 智能筛选实现层 | 新增**后端 AI 接口**（全栈） |
| 2 | 智能来源 | **LLM + 工具调用（function calling）** |
| 3 | 入口与交互 | **独立「AI 导购」页面 + 菜单入口，对话式多轮** |
| 4 | LLM 供应商 | **OpenAI 兼容端点**（`spring-ai-openai-starter`，`base-url` + `api-key` 可配，支持 DeepSeek / 智谱 / OpenAI 等） |
| 5 | 对话状态/范围 | **多轮对话，上下文仅前端保持**；后端无状态、不持久化数据库 |

## 3. 架构设计

```
用户(浏览器)
  │  输入自然语言指令 + 前端保存的对话历史
  ▼
[前端] /ai/shopping-guide 页面 (React/UmiJS)
  │  POST /product/ai-guide/chat  { messages:[{role,content}...] }
  ▼
[后端] AiGuideController (twelvet-product-server)
  │  Spring AI ChatClient.prompt(messages).tools(searchProducts)
  ▼
LLM(OpenAI 兼容端点)  ──决定调用──►  searchProducts 工具
                                        │  (keyword,catL1,city,priceMin,priceMax,condition,orderBy,limit)
                                        ▼
                              ProductAiService → ProductMapper.selectForAi(...)
                                        │  返回精简商品列表 (Top N)
                                        ▼
LLM 基于工具结果生成推荐文案  ──►  后端旁路收集本轮工具返回的商品列表
  │
  ▼  返回 { reply: 文案, products: [商品DTO...] }
[前端] 渲染：AI 气泡(文案) + 商品卡片网格(ProductCard) → 点击跳 /product/detail/:id
```

核心设计点：**工具返回的结构化商品列表由后端旁路收集**，与 LLM 生成的自然语言文案一并返回前端。前端不依赖从文案中解析商品，卡片数据始终是结构化、可靠的。

## 4. 后端设计（twelvet-fast / twelvet-product 模块）

将 AI 导购放在 `twelvet-product-server`，因为其工具本质是商品检索，内聚度最高。

### 4.1 依赖与配置

- `twelvet-product-server/pom.xml` 新增 `spring-ai-openai-spring-boot-starter`（Spring AI 1.0.x，OpenAI 兼容）。
- `application.yml` 新增（值用环境变量占位，**绝不硬编码密钥**）：
  ```yaml
  spring:
    ai:
      openai:
        base-url: ${AI_BASE_URL:https://api.deepseek.com}
        api-key: ${AI_API_KEY:}
        chat:
          options:
            model: ${AI_MODEL:deepseek-chat}
            temperature: 0.3
  ```
- 若 `api-key` 为空，接口返回友好错误「AI 导购暂未配置，请联系管理员」，不抛 500。

### 4.2 Controller

- 新增 `AiGuideController`，路径 `@RequestMapping("/product/ai-guide")`。
- `POST /product/ai-guide/chat`：
  - 请求体 `AiGuideChatReq { List<ChatMsg> messages }`，`ChatMsg { String role; String content }`（role ∈ user/assistant）。
  - 需登录（不加 `@SaIgnore`），与一般业务接口一致，便于成本控制。
  - 返回 `JsonResult<AiGuideChatResp>`，`AiGuideChatResp { String reply; List<ProductCardDTO> products }`。

### 4.3 Service：LLM 编排

- 新增 `AiGuideService`：
  1. 将前端 `messages` 映射为 Spring AI `Message`（含一段系统提示词：约束模型“你是二手商城导购，必要时调用 searchProducts 工具，依据结果推荐，不臆造不存在的商品”）。
  2. `ChatClient.prompt(...).tools(productSearchTool).call()`。
  3. 通过工具回调的副作用收集本轮检索到的商品列表（线程内 `List<ProductCardDTO>` 累积，取最后一次/合并去重）。
  4. 返回 `reply`（模型文案）+ `products`（结构化卡片数据）。

### 4.4 工具（function calling）

- 定义工具 `searchProducts`（Spring AI `@Tool` 或 `FunctionToolCallback`），入参：
  - `keyword`（String，可空）、`catL1`（一级分类，可空）、`city`（可空）、`priceMin`/`priceMax`（BigDecimal，可空）、`condition`（成色，可空）、`orderBy`（default/price_asc/price_desc/hot，可空）、`limit`（默认 8，最大 20）。
  - 工具描述清晰说明各参数语义与可选分类枚举，便于模型正确填参。
- 工具实现委托 `ProductAiService.search(...)`。

### 4.5 检索能力（不破坏现有接口）

- 现有 `ProductMapper.selectList(status,catL1,city,keyword,sellerId,orderBy)` **无价格区间**。
- 新增 `ProductMapper.selectForAi(status,catL1,city,keyword,priceMin,priceMax,condition,orderBy,limit)` + 对应 `ProductMapper.xml` 的 `<select>`（复用现有 `Base_Column_List` 与 `resultMap`，新增 `price >= / <=` 与 `condition` 条件，`LIMIT #{limit}`）。`status` 固定 `onsale`。
- 新增 `ProductCardDTO { productId,title,cover,price,originalPrice,condition,city,sellerNickName }`（位于 `twelvet-product-api`，供 Controller 序列化）。

### 4.6 错误处理

- LLM 超时/异常 → 捕获，返回 `JsonResult.error("AI 导购暂时不可用，请稍后再试")`。
- 工具检索为空 → 模型据此回复「没有找到符合条件的商品」，`products` 为空数组，前端显示空态。
- 入参 `messages` 为空或过长（> 设定轮数，如 20 条）→ 截断保留最近 N 条。

## 5. 前端设计（twelvet-ui-master）

### 5.1 页面与路由

- 新增页面目录 `src/pages/AI/ShoppingGuide/`：
  - `index.tsx`：对话式 UI（消息列表 + 底部输入框 + 发送按钮）。
  - `service.ts`：`aiGuideChat(messages)` → `request('/product/ai-guide/chat', { method:'POST', data:{ messages } })`。
- `config/routes.ts` 在 `/ai` 路由组下新增 `{ path: '/ai/shopping-guide', name: 'AI导购', icon: 'icon-ai-chat', component: './AI/ShoppingGuide' }`，并出现在左侧菜单。

### 5.2 消息模型与渲染

- 前端状态 `messages: Array<{ role:'user'|'assistant'; content:string; products?:ProductCardDTO[] }>`。
- 用户发送：push 一条 user 消息 → 调接口 → 用返回的 `reply`/`products` push 一条 assistant 消息。
- 渲染：
  - 用户气泡右侧、AI 气泡左侧（沿用项目既有对话排版习惯）。
  - AI 气泡内：先渲染 `content` 文案；若 `products` 非空，下方以响应式网格（`Row/Col`）渲染 `ProductCard`。
- 复用 `@/components/marketplace/ProductCard`，映射：`image=cover, title, price, originalPrice, condition, sellerName=sellerNickName, city, onClick=() => history.push('/product/detail/'+productId)`。
- 发送中显示 loading；输入为空时禁用发送。

### 5.3 交互细节

- 每次请求把**完整前端对话历史**作为 `messages` 上送（实现多轮上下文；后端无状态）。
- 刷新页面对话清空（符合「仅前端保持」决策）。
- 进入页面给一条欢迎语 + 示例提示（如「想要 500 元以内的 95 新机械键盘」）。

## 6. API 契约

请求：
```
POST /product/ai-guide/chat
{ "messages": [ { "role": "user", "content": "想要500元内95新的机械键盘" } ] }
```
响应：
```
{
  "code": 200,
  "data": {
    "reply": "为你筛选了几款 500 元内、95 新左右的机械键盘：",
    "products": [
      { "productId": 123, "title": "...", "cover": "http://...", "price": 399,
        "originalPrice": 699, "condition": "95", "city": "深圳", "sellerNickName": "..." }
    ]
  }
}
```

## 7. 测试策略

- 后端：
  - `ProductMapper.selectForAi` 的检索单测（价格区间、成色、limit、排序）。
  - `AiGuideService` 单测：以打桩的 `ChatModel`/工具回调验证「工具被调用 → products 被收集 → 组装响应」，不依赖真实 LLM。
  - `AiGuideController` 集成测试：mock service，校验请求/响应契约与未配置 key 的降级分支。
- 前端：
  - `ShoppingGuide` 组件测试：给定接口返回，断言渲染出文案气泡与对应数量的 ProductCard；点击卡片调用 `history.push('/product/detail/:id')`。
  - service 测试：`aiGuideChat` 以正确 method/url/body 调 `request`。

## 8. 非目标 / 后续增强（MVP 不做）

- **流式输出（SSE）**：✅ 已在 MVP 之上落地。新增 `POST /product/ai-guide/chat/stream`，逐 token 推送文案(`{type:'text'}`)，文案流结束后推送一次商品(`{type:'products'}`)，错误为 `{type:'error'}`；前端用既有 `eventSource` 工具消费,逐字渲染文案、随后渲染卡片。非流式 `POST /product/ai-guide/chat` 仍保留。
- 对话历史持久化到数据库 / 跨会话恢复。
- 高级筛选维度（多分类、标签、语义向量检索）。
- 敏感词/安全审查、限流与配额（仅保留未配置 key 的降级）。

## 9. 文件清单

后端（twelvet-fast）：
- 修改 `twelvet-product/twelvet-product-server/pom.xml`（加 spring-ai-openai starter）
- 修改 `…/twelvet-admin` 或 product-server 的 `application.yml`（AI 配置占位）
- 新增 `…/product/server/controller/AiGuideController.java`
- 新增 `…/product/server/service/AiGuideService.java`（+ impl）
- 新增 `…/product/server/service/ProductAiService.java`（+ impl，或并入 AiGuideService）
- 新增工具定义（`@Tool` 方法或 `FunctionToolCallback` 配置类）
- 修改 `…/product/server/mapper/ProductMapper.java` + `mapper/product/ProductMapper.xml`（新增 `selectForAi`）
- 新增 `…/twelvet-product-api/.../domain/ProductCardDTO.java`、`AiGuideChatReq/Resp`、`ChatMsg`

前端（twelvet-ui-master）：
- 新增 `src/pages/AI/ShoppingGuide/index.tsx`
- 新增 `src/pages/AI/ShoppingGuide/service.ts`
- 修改 `config/routes.ts`（新增路由与菜单项）
- 复用 `src/components/marketplace/ProductCard`（不改）

## 10. 风险与权衡

- [外部 LLM 依赖 base-url/key 与网络] → 用环境变量配置；未配置时优雅降级，不影响其它功能。
- [function calling 收集工具结果的实现因 Spring AI 版本而异] → 在 plans 阶段先用最小示例打通「工具被调用并拿到返回值」，再接业务。
- [价格/成色枚举需与商城既有字典一致] → 工具描述中明确枚举，必要时从 `dictCategoryTree` 等校准 catL1 取值。
- [非流式与「对话式」体验的取舍] → MVP 接受整条返回；若用户坚持逐字流式，则升级为 SSE（增加工作量与复杂度）。

## 11. 自我审查清单

- [x] 无占位符（无 TODO/TBD）
- [x] 无内部矛盾（决策表与各节一致）
- [x] 每项需求清晰可执行（含 API 契约、文件清单、字段映射）
- [x] 复用现有资产（ProductCard、/product/detail 路由、ProductMapper）
- [x] 明确 MVP 边界与后续增强（YAGNI）
