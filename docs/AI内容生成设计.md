# AI 内容生成（商品文案 / 配图）设计

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

## 1. 目标

社区商品发布页（`ui/src/pages/marketplace/ProductPublish.tsx`）新增两项 AI 能力，降低卖家发布成本：

1. **AI 帮我写**：根据商品名、分类、成色、价格等已填字段，生成多个候选标题 + 描述文案，一键回填表单。
2. **AI 配图**：根据商品信息生成一张商品配图，自动加入商品图片列表。

链路：前端(UmiJS) → Java 网关(twelvet-ai-server, sa-token 鉴权) → Python FastAPI(ai-service, 端口 8001) → LLM / 图像模型。

## 2. 接口设计

统一响应包装沿用 ai-service 的 `ApiResponse{code,msg,data}`；Java `AiServiceClient.unwrapBody` 解包，`code != 200` 抛 TWTException 透传友好错误。

### 2.1 POST /ai/assist/product-copy（文案）

请求（前端发驼峰，Python 侧 schema 用 `alias_generator=to_camel, populate_by_name=True`，两种命名都接受）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productName | string | 是 | 商品名（≤60 字） |
| category | string | 否 | 分类（一级/二级，逗号拼接） |
| condition | string | 否 | 成色（全新/95新…） |
| price | number | 否 | 售价 |
| keywords | string | 否 | 补充卖点关键词（≤200 字） |

校验：productName 非空且 ≤60；keywords/condition ≤200；超限返回 code=400。

响应 data（驼峰）：

```json
{
  "titles": ["iPhone 14 Pro 256G 暗紫色 95新 国行无拆修", "..."],
  "description": "自用一年……",
  "modelId": 6,
  "modelName": "deepseek-chat"
}
```

titles 返回 3~5 个候选，每个 ≤60 字；description ≤500 字。

### 2.2 POST /ai/assist/product-image（配图）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productName | string | 是 | 商品名 |
| category / condition / price / keywords | 同上 | 否 | 拼进图像 prompt |
| size | string | 否 | 默认 1024x1024 |

实现：**同步复用现有图像任务链路**——构建商品实拍风格 prompt → 建 `AiImageTask`（enhance_prompt 关闭，避免二次 LLM 调用拖慢且偏离商品本体）→ `await create_image_task_async(task_id)` 同步执行 → 读回 `result_url`。

响应 data（驼峰）：

```json
{
  "taskId": 123,
  "imageUrl": "https://.../xxx.png",
  "modelId": 1,
  "modelName": "agnes-image-2.1-flash"
}
```

同步执行默认超时由 httpx 内部超时控制，失败返回 code=500 + 友好提示。

## 3. 模型选择策略

- 文案：`ai_model` 中 `model_type='LLM' and default_flag=1 and status=1 and del_flag=0` 的默认模型；取不到时报错"未配置可用的 AI 文案模型，请联系管理员"。用 `langchain_openai.ChatOpenAI(base_url, api_key, model_name)` 调用，要求输出 JSON（容错解析：```json 剥壳 / 最外层花括号 / 纯文本降级为单标题）。
- 配图：复用 `app/routers/image.py` 的 `_resolve_model` 同款逻辑（IMAGE 默认模型，default 优先、任一可用兜底）。
- 后台无可用默认 LLM 模型时需在 `ai_model` 插入一行（model_type='LLM'，api_key 取 ai-service/.env 的 AI_API_KEY）。

## 4. Java 代理层与权限对接

新增 `twelvet-ai-server/controller/AiAssistController.java`（参考 AiImageController）：

- `POST /ai/assist/product-copy`、`POST /ai/assist/product-image`
- `@SaCheckLogin`（发布页仅要求登录，不用 `ai:model:*` 那类管理员权限点）
- 请求体用 `Map<String,Object>` 原样透传（前端驼峰 JSON → Python 驼峰别名直接解析），响应 `JsonResult.success(aiServiceClient.post(...))`。

## 5. 失败与降级

| 场景 | 行为 |
|---|---|
| 无可用 LLM / IMAGE 默认模型 | code=500，msg="未配置可用的AI文案/配图模型，请联系管理员" |
| LLM 返回非 JSON / 调用异常 | 记录日志；JSON 解析失败时降级取纯文本作单条标题；调用异常返回 code=500 友好错误 |
| 参数缺失/超长 | code=400 + 具体字段提示 |
| 图像生成失败 | 读取任务 error_msg 返回 code=500 |

## 6. 涉及文件清单

| 层 | 文件 | 改动 |
|---|---|---|
| Python | `ai-service/app/schemas/assist.py` | 新增请求/响应 schema（驼峰别名） |
| Python | `ai-service/app/services/product_copywriting.py` | 新增 prompt 构建 + 响应解析（纯逻辑，可测） |
| Python | `ai-service/app/routers/assist.py` | 新增 /ai/assist/* 路由 |
| Python | `ai-service/app/main.py` | 注册 router |
| Python | `ai-service/tests/test_product_copy.py` | 单测（mock LLM） |
| Java | `twelvet-ai/.../controller/AiAssistController.java` | 新增透传 controller（@SaCheckLogin） |
| 前端 | `ui/src/services/marketplace/api.ts` | 新增 assistProductCopy / assistProductImage |
| 前端 | `ui/src/pages/marketplace/ProductPublish.tsx` | "AI 帮我写" / "AI 配图" 按钮 + 回填逻辑 |
| 文档 | `docs/AI内容生成设计.md` | 本文档 |

## 7. 遗留 TODO

- AI 配图结果为外站 URL，未转存 OSS；商品 images 存储约定为 OSS 相对路径，发布前应加转存步骤（DFS）。
- 图像生成目前为同步等待（Agnes flash 较快），后续可改为复用 `/ai/image/generate` 异步任务 + 前端轮询。
- ~~可选：文案生成接入 token 计费（AiQuotaChecker）~~ → 按次配额检查已接入，见第 8 节；token 级计费仍可选。

## 8. 用量计费（2026-09-06 追加）

### 8.1 结论

product-copy / product-image 接入用量配额：**事前检查**每日调用次数（与 `sys_tenant.quota_ai_call_day` 对齐，超限/套餐过期返回 429 友好错误、不触发上游模型调用），**事后计量**成功调用落明细账（`ai_usage_log`，含用户/功能维度）。

### 8.2 现有机制与口径选型

- Java 侧已有 `AiQuotaChecker`（商业化第三轮 P1，仅聊天入口 `/ai/chat` 调用）：Redis（db1，`BillingKeys.aiDayKey`）INCR 计数 + `sys_tenant.quota_ai_call_day`（0=不限）+ 套餐过期（`expire_at`）检查；计数先于上游调用、失败不回滚。
- Python 侧已有计量旁路 `record_usage`：聊天完成后按租户×自然日 upsert `ai_usage_daily`（calls/tokens 累加），租户取 `X-Tenant-Id` 头（Java 代理透传）。
- 两侧计数存储不共享（Redis db1 vs MySQL），ai-service 无法读 Java 的 Redis 计数，故 Python 侧配额检查以 **`ai_usage_daily.calls` 为口径**（= Python 侧全部 AI 入口：聊天 record_usage + 辅助入口 check_and_count），检查语义逐条对齐 Java：0=不限、过期拒绝、计数先于生成、无租户上下文跳过。

### 8.3 实现要点

| 层 | 文件 | 说明 |
|---|---|---|
| Python | `ai-service/app/services/quota_service.py` | `check_and_count(db, tenant_id)`：查 `sys_tenant`（quota_ai_call_day / expire_at）→ 过期抛 `QuotaExceededException` → `record_usage(+1)` → 读回 `ai_usage_daily.calls` 超限抛 `QuotaExceededException("今日 AI 调用配额已用完（N/M），请升级套餐")`；`log_usage(...)` 成功调用落 `ai_usage_log`（旁路，失败只告警）；`tenant_id_from_request` / `user_id_from_request` 身份提取（`X-Tenant-Id`/`X-User-Id` 头，兼容请求体 `userId`，照 chat.py 惯例） |
| Python | `ai-service/app/routers/assist.py` | 两个接口在模型解析/生成**之前** `check_and_count`（429 直接返回），成功后 `log_usage`（feature=product-copy/product-image，task_type=text/image，cost=1） |
| SQL | `backend/data/ai-assist-usage-init.sql` | `ai_usage_log` 建表（tenant_id/user_id/feature/model_id/model_name/task_type/cost/create_time），已执行 |
| Java | `twelvet-ai/.../client/AiServiceClient.java` | `post` 新增 `extraHeaders` 重载（源码已改，待下次构建生效） |
| Java | `twelvet-ai/.../controller/AiAssistController.java` | 透传 `X-Tenant-Id`（TenantContext）+ `X-User-Id`（StpUtil）给 Python（源码已改，待下次构建生效） |
| 测试 | `ai-service/tests/test_quota_service.py` | 15 例单测（全 mock） |

关键坑：读回当日计数**必须用新会话**——请求级会话在 REPEATABLE READ 下持有先查 `sys_tenant` 的快照，看不到 `record_usage`（独立会话）刚提交的 +1，会把 51/50 误判为 50/50 放行。

### 8.4 验证记录（2026-09-06）

- 单测：`python -m pytest tests/ -q` 全量 140 passed（含本节 15 例）；存量 `test_agent.py` 2 个失败与本改动无关。
- 实测（直连 8001，手动把租户 2 的 `ai_usage_daily.calls` 插到配额 50）：
  - `X-Tenant-Id: 2`（free，50/日）调 product-copy/product-image → `{"code":429,"msg":"今日 AI 调用配额已用完（53/50），请升级套餐"}`，且未触发 LLM（拒绝发生在生成之前）；
  - `X-Tenant-Id: 1`（pro，不限）调 product-copy → 200 返回文案，`ai_usage_daily` 当日 calls+1，`ai_usage_log` 落一行（tenant_id=1, user_id=1, feature=product-copy, model=agnes-2.5-flash, task_type=text, cost=1）；
  - 验证后已清理人为插入的计数行，避免影响真实配额。

### 8.5 配额统一（2026-09-06 追加）：聊天并入 ai_usage_daily 单一账本

**问题（8.1–8.4 遗留的闭环缺陷）**：接入 assist 后形成两套互不相通的每日配额计数——
Java 侧 `AiQuotaChecker`（Redis db1 按日 INCR，仅聊天入口调用）与 Python 侧
`ai_usage_daily`（assist 在用）。同一租户聊天和 AI 内容生成各有一个独立额度池，
`quota_ai_call_day` 实际被翻倍（各扣各的，互不感知）。

**方案**：统一收口到 Python 侧 `ai_usage_daily` 单一账本，`sys_tenant.quota_ai_call_day` 语义不变（0/NULL=不限仍计数、过期拒绝、超限 429 友好错误）。

- **Python 聊天入口接入 `check_and_count`**（`app/routers/chat.py`）：三个入口
  `/ai/chat`（messages 导购模式）、`/ai/chat`（knowledgeId RAG 流式）、`/ai/chat/stream`
  （导购流式）均在调用上游模型**之前**经 `_check_quota → check_and_count` 事前检查并预计数，
  超限/过期返回 `{"code":429,"msg":"今日 AI 调用配额已用完（N/M），请升级套餐"}`
  （HTTP 200 + 业务码 429，与 assist 一致）。feature 命名：导购聊天=`chat`、知识库 RAG=`rag-chat`。
- **去双计**：`check_and_count` 已预计 calls=1，聊天成功后的补记只加 token
  （`record_usage(calls=0, ...)`，非流式与 `_record_after_stream` 均改），不再重复 +1。
- **明细可区分**：聊天成功调用也落 `ai_usage_log`（feature=chat/rag-chat，task_type=text），
  与 product-copy/product-image 共表区分。
- **Java 停用旧计数**：`AiQuotaChecker` 保留类但 `checkAndIncr()` 改为 no-op（注明口径迁移
  原因；它只承担每日计数、无每秒级限流职责，故整体停用），`AiChatController` 移除调用点。
  Redis 历史计数 key 随 TTL 自然失效。
- **Java 透传修正（防吞错）**：Python 超限返回 HTTP 200 + `code=429` 体内码，
  `AiServiceClient.unwrapBody` 对 `code=429` 抛 `QuotaExceededException` → 全局处理器映射
  HTTP 429 + 友好文案（assist 与聊天非流式路径共用，原 TWTException 会降级成 HTTP 400）；
  两条 SSE 代理路径（`AiChatController` 知识库 RAG、`AiGuideController.chatStream` 导购流式）
  用 `exchangeToFlux` 检测上游响应 Content-Type，非 `text/event-stream`（即 429 JSON）时解析
  `{code,msg}` 转为前端既有协议的 `{"type":"error","code":429,"message":...}` SSE 事件透传
  （emitter 返回时 HTTP 200 已提交，无法再改状态码）；`AiGuideController` 非流式路径的解包移出
  try 块，避免 429 被网络异常兜底吞成"AI 导购暂时不可用"。
- **计数语义**：被拒绝的调用仍 +1（先计数后比较、失败不回滚，防刷优先），与原 Java 语义一致。

**改动文件**：

| 层 | 文件 | 说明 |
|---|---|---|
| Python | `ai-service/app/routers/chat.py` | 三入口接 `_check_quota`（429 友好错误）；补 token 改 `calls=0`；成功落 `log_usage(feature=chat/rag-chat)` |
| Python | `ai-service/app/services/quota_service.py` | 仅模块 docstring 更新（口径迁移说明），检查逻辑不变 |
| Java | `twelvet-ai/.../billing/AiQuotaChecker.java` | `checkAndIncr()` 改 no-op + @Deprecated，类保留注明停用原因 |
| Java | `twelvet-ai/.../controller/AiChatController.java` | 移除 `aiQuotaChecker` 调用；RAG SSE 分支 `exchangeToFlux` + 非 SSE 响应转 error 事件 |
| Java | `twelvet-ai/.../client/AiServiceClient.java` | `unwrapBody`：`code=429` → `QuotaExceededException`（HTTP 429） |
| Java | `twelvet-product/.../controller/AiGuideController.java` | 非流式解包移出 try（429 不被吞）；流式 `exchangeToFlux` + error 事件透传 |
| 测试 | `ai-service/tests/test_chat_quota.py` | 新增 7 例（全 mock）：三入口超限 429 且不触发 LLM、流式超限返回 JSON 非 SSE、正常路径 `calls=0` 不双计、feature 可区分、`_check_quota` 会话委托 |

**验证记录（2026-09-06，重启 8001 后直连实测）**：

- 单测：`python -m pytest tests/ -q` → 147 passed / 2 failed（存量 `test_agent.py`，与本改动无关）。
- 正常计数（租户 1，pro 不限）：直连 `/ai/chat` messages 模式成功回复后，`ai_usage_daily`
  当日 calls 恰好 +1（1→2，无双计）且 tokens 331/206 落账；`ai_usage_log` 新增一行
  feature=`chat`、user_id=1、model=agnes-2.5-flash、cost=1，与 assist 的 feature 可区分。
- 共享额度池（租户 2，free 50/日，插满 calls=50）：聊天 → `{"code":429,"msg":"今日 AI 调用配额
  已用完（51/50），请升级套餐"}`；紧接 `/ai/assist/product-copy` → `（52/50）`——聊天与
  assist 同池递减，`quota_ai_call_day` 不再翻倍。流式与 RAG 路径超限同样返回
  `application/json` 的 429（非 SSE），供 Java 代理层转 error 事件；被拒调用仍计入
  （52→54），与原防刷语义一致。
- 验证后已清理测试数据（删除租户 2 当日计数行、租户 1 恢复基线、删除测试明细行）。
- Java 侧为源码改动、未构建：待主会话统一构建部署后，HTTP 429 透传与 SSE error 事件生效。
