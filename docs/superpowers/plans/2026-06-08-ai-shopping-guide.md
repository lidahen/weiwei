# AI 导购功能 实现计划

- 日期：2026-06-08
- 设计文档：`docs/superpowers/specs/2026-06-08-ai-shopping-guide-design.md`
- 范围：非流式 MVP（一次性返回 `{reply, products}`）
- 执行约定：每个实现任务遵循 TDD 微循环（写失败测试 → 运行看失败 → 实现 → 运行看通过 → 提交）。无测试基线处先建最小测试脚手架。

## 前置说明（环境约束）

- 前端 `twelvet-ui-master` 有 `jest`，可真实运行测试。
- 后端 `twelvet-product` 暂无 `src/test`，需新建测试脚手架；Spring AI 依赖需 Maven 可联网拉取。若本机离线/无 LLM key，后端任务以「可编译 + 单元逻辑测试（打桩 LLM）」为验收，不要求真实调用大模型。
- 后端 service 单测用打桩 `ChatModel`/工具回调，不依赖真实网络。

---

## 阶段 A：后端 — 数据契约与检索能力（不接 LLM，先打通商品检索）

### A1. ProductCardDTO 与请求/响应模型
- [ ] A1.1 在 `twelvet-product-api/.../domain/` 新增 `ProductCardDTO`（productId,title,cover,price,originalPrice,condition,city,sellerNickName）。
- [ ] A1.2 新增 `AiGuideChatReq { List<ChatMsg> messages }`、`ChatMsg { String role; String content }`、`AiGuideChatResp { String reply; List<ProductCardDTO> products }`。
- [ ] A1.3 提交。

### A2. selectForAi 检索（含价格区间/成色/limit）— TDD
- [ ] A2.1 建后端测试脚手架：`twelvet-product-server/src/test/...`，配置可跑的 Mapper 测试（H2 或测试库），写一条针对 `selectForAi` 的失败测试（按价格区间过滤）。
- [ ] A2.2 运行测试，确认失败（方法/SQL 不存在）。
- [ ] A2.3 在 `ProductMapper.java` 新增 `selectForAi(status,catL1,city,keyword,priceMin,priceMax,condition,orderBy,limit)`；在 `ProductMapper.xml` 新增 `<select>`（复用 `Base_Column_List`/`resultMap`，加 `price >= / <=`、`condition`、`LIMIT`）。
- [ ] A2.4 运行测试，确认通过。
- [ ] A2.5 补充用例：成色过滤、关键词、orderBy=price_asc、limit 上限。运行通过后提交。

### A3. ProductAiService.search — TDD
- [ ] A3.1 写 `ProductAiService.search(...)` 的失败测试（给定 mapper 桩，断言返回映射为 `ProductCardDTO` 且 status 固定 onsale、limit 默认 8/上限 20）。
- [ ] A3.2 运行看失败 → 实现 service → 运行通过 → 提交。

---

## 阶段 B：后端 — LLM 编排与工具（function calling）

### B1. 依赖与配置
- [ ] B1.1 `twelvet-product-server/pom.xml` 加 `spring-ai-openai-spring-boot-starter`（确认版本与父 BOM 兼容）。
- [ ] B1.2 `application.yml` 加 `spring.ai.openai.{base-url,api-key,chat.options.model,temperature}`，全部环境变量占位（`AI_BASE_URL/AI_API_KEY/AI_MODEL`）。
- [ ] B1.3 编译通过后提交。

### B2. searchProducts 工具
- [ ] B2.1 写工具单测：以打桩 `ProductAiService` 验证「给定参数 → 调用 service → 返回商品列表」。
- [ ] B2.2 运行看失败 → 用 `@Tool`/`FunctionToolCallback` 实现工具（入参 keyword/catL1/city/priceMin/priceMax/condition/orderBy/limit，描述清晰）→ 运行通过 → 提交。

### B3. AiGuideService 编排 — TDD（打桩 LLM）
- [ ] B3.1 写失败测试：用打桩 `ChatClient/ChatModel`，模拟模型触发一次工具调用；断言 `AiGuideService.chat(messages)` 收集到工具返回的 products，并组装 `{reply, products}`。
- [ ] B3.2 运行看失败 → 实现 `AiGuideService`（系统提示词 + messages 映射 + tools 注册 + 旁路收集工具结果 + 截断最近 N 轮）→ 运行通过。
- [ ] B3.3 增加分支测试：未配置 `api-key` → 返回友好降级；LLM 异常 → 捕获返回错误。运行通过后提交。

### B4. AiGuideController
- [ ] B4.1 写 controller 集成测试（mock `AiGuideService`）：`POST /product/ai-guide/chat` 返回契约正确；需登录。
- [ ] B4.2 运行看失败 → 实现 `AiGuideController`（`@RequestMapping("/product/ai-guide")`，`/chat`，返回 `JsonResult<AiGuideChatResp>`）→ 运行通过 → 提交。

---

## 阶段 C：前端 — 服务与页面

### C1. service — TDD
- [ ] C1.1 写 `service.ts` 的 `aiGuideChat` 测试：断言以 `POST /product/ai-guide/chat`、body `{messages}` 调用 `request`。
- [ ] C1.2 运行看失败 → 实现 `src/pages/AI/ShoppingGuide/service.ts` → 运行通过 → 提交。

### C2. ShoppingGuide 页面 — TDD
- [ ] C2.1 写组件测试：mock `aiGuideChat` 返回 `{reply, products:[1条]}`；断言渲染出 AI 文案气泡 + 1 个 `ProductCard`；点击卡片调用 `history.push('/product/detail/:id')`。
- [ ] C2.2 运行看失败 → 实现 `index.tsx`（消息列表、输入框、发送、loading、用户/AI 气泡、products 网格复用 `ProductCard`、欢迎语与示例提示）→ 运行通过。
- [ ] C2.3 补充：空 `products` 显示空态；输入为空禁用发送；每次上送完整历史。运行通过后提交。

### C3. 路由与菜单
- [ ] C3.1 `config/routes.ts` 在 `/ai` 组下新增 `{ path:'/ai/shopping-guide', name:'AI导购', icon:'icon-ai-chat', component:'./AI/ShoppingGuide' }`。
- [ ] C3.2 启动前端，确认菜单出现、页面可达、`tsc` 无新增错误。提交。

---

## 阶段 D：联调与收尾

- [ ] D1 配置真实/兼容 LLM（`AI_BASE_URL/AI_API_KEY/AI_MODEL`），后端起服务。
- [ ] D2 前端发指令「想要 500 元内 95 新机械键盘」，验证：返回文案气泡 + 商品卡片，点击跳详情。
- [ ] D3 验证多轮：追问「再便宜点」能基于上下文重新筛选。
- [ ] D4 验证降级：未配置 key 时前端显示友好提示，不报 500。
- [ ] D5 代码审查（规格合规 + 质量），整理提交。

---

## 任务粒度与执行方式

- 每个 `- [ ]` 子项约 2-5 分钟；实现项严格走红-绿-重构。
- 阶段 A→B→C→D 有依赖，需顺序进行；阶段内多数子任务可独立。
- 执行方式见对话中选择：Subagent-Driven（每任务独立子智能体 + 两阶段审查，推荐）或手动执行。

## 自我审查
- [x] 任务含精确文件路径与验收方式
- [x] 每个实现任务有对应测试（TDD）
- [x] 标注环境约束（后端测试/LLM 依赖）
- [x] 与设计文档一致，范围限定非流式 MVP

---

## 执行状态（2026-06-08）

已完成（代码已落地）：
- 阶段 A 后端契约与检索：`ProductCardDTO/ChatMsg/AiGuideChatReq/AiGuideChatResp`、`ProductMapper.selectForAi` + XML、`ProductAiService`。
- 阶段 B 后端 LLM 编排：product-server pom 引入 `spring-ai-bom` + `spring-ai-starter-model-openai`；`application.yml` 加 `spring.ai.openai`（环境变量占位）；`ProductSearchTools`(@Tool)、`AiGuideService`、`AiGuideController`。
- 阶段 C 前端：`src/pages/AI/ShoppingGuide/{service.ts,index.tsx}`、路由与菜单。已真实验证：`service.test.ts`、`index.test.tsx` 均通过，`tsc` 本功能无类型错误。

待用户环境验证（本会话无法执行）：
- 后端 Maven 构建（联网拉 Spring AI 依赖）、起服务。
- 配置 `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`（OpenAI 兼容端点）。
- 阶段 D1-D4 端到端联调：发指令→文案+卡片→点击跳详情；多轮追问；未配置 key 的降级提示。
