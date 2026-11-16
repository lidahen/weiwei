# AI 服务优化（四合一）设计文档

- 日期：2026-08-29
- 主题：导购聊天真流式 + 稳定性加固 + 计量与成本 + RAG 重排
- 状态：已批准（用户四方向全选）
- 范围：`ai-service/`（Python FastAPI:8001）+ `twelvet-ai-server`/`twelvet-product-server`（Java 代理层）

## 0. 现状与结论

- 流式链路已存在：Python `/ai/chat/stream` → Java `/product/ai-guide/chat/stream`（SSE 透传）→ 管理端 ShoppingGuide 页。
- **核心问题**：Python `chat_stream` 是伪流式——先 `await _run_graph(...)` 跑完整图（含工具循环），再把最终回复按 2 字符切片。首 token 延迟 = 全图执行时间。
- Java WebClient 无超时配置（上游挂起即无限等待）；LLM 无显式 timeout/retries。
- 计量只有「按租户×日调用次数」（Redis 26h 过期），无 token 计量、无历史、无月账单。
- RAG 检索 = FAISS 向量 top-k 直出，无重排。

## 1. Part 1 导购聊天真流式

| 决策 | 选择 | 理由 |
|---|---|---|
| 流式机制 | 新 `agent/stream.py`：`ContextVar[asyncio.Queue]` token 汇；`run_agent_tool_loop`/`chitchat_node` 的 LLM 调用改 `astream`，text 增量推入队列 | 不改 LangGraph state（Queue 不可序列化，会破坏 checkpointer）；contextvar 随子任务继承 |
| 泄漏风险 | 工具决策迭代同样 astream，但 tool_call 响应 text 为空 → 自然不过滤不出错；模型「叙述+调工具」同轮的边缘场景会多推一段叙述 | DeepSeek 工具调用响应 content 为空，实测风险低；接受 |
| 事件协议 | 沿用 `{"type":"text"}` 增量 + `{"type":"products"}` + 新增 `{"type":"done","usage":{...}}` 收尾 | 前端 ShoppingGuide 已按 type 分发，增量兼容 |
| usage 采集 | 最终回答 AIMessage 的 usage_metadata（prompt/completion tokens）存入 sink，chat/chat_stream 读出 | 为 Part 4 提供数据；非流式 ChatResponse 加 `usage` 字段 |
| Java | `/product/ai-guide/chat/stream` 已存在不动；`/product/ai-guide/chat` 非流式透传 `usage` | — |

## 2. Part 3 稳定性与性能加固

| 决策 | 选择 | 理由 |
|---|---|---|
| Java 超时 | 新 `AiWebClientConfig`：connect 5s / responseTimeout 180s（SSE 长回答），ai-server 与 product-server 的 AI 控制器共用 | 现 Builder 无超时，上游挂起=请求线程无限等 |
| 上游错误 | 连接失败/超时 → 友好 JsonResult（「AI 服务暂时不可用」），非流式路径统一 catch 已有，补超时语义；SSE 路径 error 事件已有 | 避免 500 裸栈 |
| Python LLM | `_get_llm` 加 `timeout=settings.ai_llm_timeout(60s)` + `max_retries=settings.ai_llm_max_retries(1)` | OpenAI SDK 层重试，工具循环不因网络抖动整轮失败 |
| agnes | 已有 connect 10s/read 600s，不动 | — |

## 3. Part 4 计量与成本

| 决策 | 选择 | 理由 |
|---|---|---|
| 存储 | 新表 `ai_usage_daily(id, tenant_id, stat_date, calls, prompt_tokens, completion_tokens, update_time)`，uk(tenant_id, stat_date) | Redis 日计数 26h 过期无历史；月账单要落库 |
| 采集 | Part 1 的 usage → Java 侧统一落账：非流式读 `data.usage`；SSE 路径 flux 中旁路解析 `type":"done"` 行提取 usage（解析后原样转发，不影响前端） | 计费口径收口在 Java（billing 域既有模式） |
| 落账 | `AiUsageDailyMapper` INSERT...ON DUPLICATE KEY UPDATE calls+1/tokens 累加；无租户上下文（匿名/平台）跳过 | 与 AiQuotaChecker 同语义 |
| 查询 | `GET /ai/usage/daily?days=30`：本租户近 N 天明细+合计（登录即可，租户域数据） | 供后续租户 UI/月账单页挂接，本轮不做 UI |
| 调用计数 | AiQuotaChecker.checkAndIncr 不动（次数口径保持） | 兼容既有 429 语义 |

## 4. Part 2 RAG 检索质量（rerank）

| 决策 | 选择 | 理由 |
|---|---|---|
| 重排 | siliconflow `/rerank`（`BAAI/bge-reranker-v2-m3`）：FAISS 先取 top_n×4，重排取 top_n | 向量召回快但精排弱；cross-encoder 重排是性价比最高的一步 |
| 配置 | `rerank_enabled=True / rerank_model / rerank_top_n=5`，与 embedding 同 base_url 同 api_key | 零新密钥 |
| 降级 | 重排请求失败/超时(5s) → 按原 FAISS 顺序返回，日志告警 | 检索可用性优先 |
| 生效范围 | `rag_service.search`（知识库聊天 + 导购 RAG 上下文注入同源） | 单点收口 |

## 5. 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | curl Python /ai/chat/stream（导购模式） | token 事件**增量到达**（首 token ≪ 全图耗时），products/done 收尾 |
| 2 | Java /product/ai-guide/chat/stream | SSE 透传正常，done 事件含 usage |
| 3 | 非流式 /product/ai-guide/chat | 返回含 usage 字段 |
| 4 | 停 Python 服务 → Java 聊天 | ≤6s 返回友好错误（非 500 裸栈/非无限等待） |
| 5 | 一次聊天后查 ai_usage_daily | 当日行 calls+1，tokens>0；重跑累计不重复建行 |
| 6 | GET /ai/usage/daily?days=30 | 本租户明细+合计 |
| 7 | 有知识库时 RAG 检索带 rerank | 结果顺序经重排；断网/失败时回退原序 |
| 8 | 回归：知识库 SSE 聊天、AI 配额 429、租户隔离抽查 | 不变 |

## 6. 回滚

四部分相互独立：Part1 前端/Java 不传 stream 即走旧非流式；Part2 `rerank_enabled=false`；Part4 不消费端点即无影响（表可 DROP）；Part3 超时参数可调大；`git revert`。
