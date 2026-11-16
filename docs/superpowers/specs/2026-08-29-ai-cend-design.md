# C 端 AI 导购入口 + 用量挂接 设计文档

- 日期：2026-08-29
- 主题：marketplace AI 导购悬浮入口（流式）+ 租户管理页 AI 用量 + 知识库聊天 usage 采集
- 状态：已批准（用户确认按建议顺序继续）
- 前置：ai-optimize 已合 master（真流式/计量/月账单端点/rerank 就绪）

## 设计

### ① C 端 AI 导购入口（marketplace/AiGuideFloat）
| 决策 | 选择 | 理由 |
|---|---|---|
| 形态 | `FloatButton`（右下角）+ antd Drawer 聊天（宽 420） | 不新增路由/菜单；C 端商店标准形态 |
| 挂载点 | `app.tsx childrenRender`，按路径显示：`/home`、`/search*`、`/product*`、`/feed*` | C 端商店页面集合；管理台页面不显示 |
| 登录态 | 仅 `initialState.currentUser` 存在时渲染 | /product/ai-guide 需登录（SaInterceptor），onPageChange 已把匿名用户重定向登录页 |
| 会话 | sessionId（localStorage 持久）+ confirm/resume 流程，复用 `aiGuideChatStream`（ShoppingGuide service 直接 import） | 方案 3 多轮停留已在上游实现 |
| 商品卡片 | 复用 `@/components/marketplace/ProductCard`，链接 `/product/detail/:id` | C 端现有卡片组件 |
| 租户 | `eventSource` helper 补 `X-Tenant-Code`（与 requestErrorConfig 同逻辑） | 原先 SSE 请求不带租户头：localhost C 端（default）用量落不了账；子域名模式 Host 兜底 |
| 样式 | 邻里二手色系（#FF6B35 主色、#FFF8F1 底） | 与 marketplace 一致 |

### ② 租户管理页 AI 用量
| 决策 | 选择 | 理由 |
|---|---|---|
| 后端 | `/ai/usage/daily` 加可选 `tenantId` 参数：仅平台超管（userId=1）且上下文为 default/无 时生效，否则强制当前上下文 | 与用户列表 bindTenantScope 同语义：防越权枚举 + 平台运营可查任意租户 |
| 前端 | Tenant 行操作加「AI 用量」→ Drawer（近 30 天表格 + 合计），复用 usage 端点 | 无图表新依赖，MVP 表格呈现 |

### ③ 知识库聊天 usage 采集
`_rag_chat_stream` 的 `llm.astream` 累积 merged chunk → `usage_metadata` → 流末补发 `done` 事件；已有的 `_record_after_stream` 包装器解析 done 落账（tokens 原来恒 0）。Python 单点改动。

## 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | thu.localhost:8000 登录 alice → /home 右下角悬浮按钮 → 打开抽屉发消息 | 流式逐字回复 + 商品卡片可点进详情 |
| 2 | 对话后查 ai_usage_daily | thu 当日 calls/tokens 增长（悬浮入口流量计入租户） |
| 3 | localhost:8000 admin → Tenant 页某租户「AI 用量」 | Drawer 显示该租户近 30 天明细+合计 |
| 4 | 知识库聊天（管理端 AI Chat 页）后查落账 | completion/prompt tokens 非 0 |
| 5 | 回归：管理台页面（/system 等）不显示悬浮按钮；导购工具确认流（confirm/resume）不受影响 | 不变 |

## 回滚

悬浮按钮与用量 Drawer 为纯增量 UI（`git revert`）；knowledge usage 事件对旧前端是新增事件类型（前端按 type 分发，未知类型忽略）；tenantId 参数无调用即无影响。
