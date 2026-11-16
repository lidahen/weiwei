# AI 服务优化（四合一：真流式 / 稳定性 / 计量 / RAG 重排）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 设计：`docs/superpowers/specs/2026-08-29-ai-optimize-design.md`
> 摘要：`docs/ai-optimize-summary.md`

## 启用步骤（已有库执行一次）

```bash
mysql -uroot -p twelvet-fast < backend/data/ai-optimize-init.sql   # ai_usage_daily 表
cd ai-service && pip install -r requirements.lock.txt              # 本机此前从未装过依赖
python start.py                                                    # 8001
```

## 四件事

### ① 导购聊天真流式（核心体验）
- 旧 `chat_stream` 是**伪流式**：先 `await _run_graph(...)` 跑完整图（含工具循环），再把最终回复按 2 字符切片——首 token 延迟 = 全图执行时间。
- 新机制：`agent/stream.py` 的 `TokenSink`（ContextVar + asyncio.Queue）跨任务传 token；`run_agent_tool_loop`/`chitchat_node` 的 LLM 调用改 `ainvoke_or_stream`（有汇时 `astream` 逐段推送）。**不改 LangGraph state**（Queue 不可序列化，会破坏 checkpointer）。
- 事件协议：`text` 增量 + `products` + 新增 `done`（含 usage）；商品汇总/模板文案等非流式回复由 `reply_streamed` 标记 + runner 兜底补发（首版用 `emitted_chars==0` 判断被工具迭代的换行符污染，已修）。
- 前端 ShoppingGuide/Java 透传协议兼容，无需改动。
- 实测：首 token 2.7~10.2s（含工具调用轮），总耗时 21s——旧实现要等 21s 才开始吐字。

### ② 稳定性与性能加固
- Java：ai-server/product-server 各自 `AiWebClientConfig`（connect 5s / responseTimeout 180s）——缺省 Builder 无超时，上游挂起=请求线程无限等。
- Python：`_get_llm` 加 `timeout=60s` + `max_retries=1`（SDK 层重试）。
- 实测：杀掉 Python 进程，Java 聊天 **18ms** 返回友好「AI 导购暂时不可用」（连接拒绝快速失败）。

### ③ 计量与成本（token 级）
- 新表 `ai_usage_daily`（租户×日，uk 去重累加）：calls/prompt_tokens/completion_tokens。
- 口径收口：Java 代理透传 `X-Tenant-Id`（TenantContext）→ Python 聊天完成时 upsert（`usage_service.record_usage`，旁路失败只告警）；usage 来源=最终回答的 `usage_metadata`。
- 新端点 `GET /ai/usage/daily?days=30`（登录即可，按当前租户圈定）：日明细+合计，供后续租户 UI/月账单页挂接。
- 实测：连续调用累计（calls 3→6→11，tokens 递增），无重复建行。
- 调用次数口径不变：AiQuotaChecker 仍在代理前计数（429 语义不动）。

### ④ RAG 检索重排
- `rag_service.search`：FAISS 粗排 recall=top_n×4 → siliconflow `/rerank`（`BAAI/bge-reranker-v2-m3`，与 embedding 同 key）精排取 top_k。
- 降级：重排失败/超时(5s) → 回退粗排原序（日志告警）；`rerank_enabled=false` 一键关。
- 实测：返回分数已变为 cross-encoder 相关度（原 FAISS 余弦相似度被替换），真实 API 调用成功。

## 验证记录（2026-08-29 实测）

| # | 用例 | 结果 |
|---|---|---|
| 1 | Python /ai/chat/stream 直连 | text 增量 252/139 事件、首 token 10.2s/7.3s、products+done(usage) ✅ |
| 2 | Java /product/ai-guide/chat/stream 透传 | 69 text 事件真实回复 + done usage ✅ |
| 3 | Java 非流式 /product/ai-guide/chat | 返回 usage {"promptTokens":2385,"completionTokens":194} ✅ |
| 4 | 杀 Python → Java 聊天 | 18ms 友好错误（无 500 裸栈/无挂起）✅ |
| 5 | ai_usage_daily 落账 | calls/tokens 累计、uk 去重 ✅ |
| 6 | GET /ai/usage/daily?days=30 | rows+total 正确 ✅ |
| 7 | rerank 直测 kb_5 | 返回 cross-encoder 分数（非向量分数）✅ |
| 8 | 知识库 SSE 经 Java /ai/chat | 流式正常（你好！我是…逐字）✅ |

## 验证中发现并修复

1. **存量 bug：无 session 的导购对话必挂**——图编译挂了 InMemorySaver checkpointer，thread_id 必填，session_id=None 时 LangGraph 直接抛 "Checkpointer requires thread_id"。`_run_graph` 现为无 session 调用生成临时 thread。
2. 兜底补发条件 `emitted_chars==0` 被工具迭代的前导换行污染 → 改 `reply_streamed` 显式标记。
3. 环境坑：本机 Python 3.14.5 从未装过 ai-service 依赖（此前 round3 验证时「上游拒连仍计数」即因此）；faiss-cpu 1.15.0 在 3.14 有 wheel，`requirements.lock.txt` 可直接装。

## 已知边界

- 工具决策迭代同样 astream：模型「叙述+调工具」同轮时叙述会提前推给用户（DeepSeek 工具调用 content 为空，实测罕见）。
- 知识库聊天（_rag_chat_stream）usage 未采集（只计 calls）；导购路径 token 采集完整。
- 用量 UI 未做（端点已就绪）；`/ai/chat` 的 SSE 在 auth-ignore 白名单（既有设计，Java 层手动 checkLogin）。
- SseEmitter 经 Java 后 data 行无空格（`data:{...}`），前端解析需兼容两种格式。

## 回滚

四部分相互独立：前端不传 stream 走旧非流式；`rerank_enabled=false`；`DROP TABLE ai_usage_daily`；`ai_llm_timeout` 可调大；`git revert`。

## 补充轮：C 端导购入口 + 用量挂接（2026-08-29 当日续做，master `eaee38e`）

| 项 | 内容 |
|---|---|
| C 端 AI 导购入口 | `marketplace/AiGuideFloat`：右下角悬浮按钮 + Drawer 聊天（流式/商品卡片点进详情/confirm-resume/会话持久化）；`app.tsx childrenRender` 按 C 端路径挂载、登录用户可见 |
| eventSource 租户头 | SSE 请求补 `X-Tenant-Code`（与 requestErrorConfig 同口径），C 端 localhost 流量可落账 |
| 用量按租户查询 | `/ai/usage/daily` 支持超管传 `tenantId`/`tenantCode` 查任意租户（非超管强制当前租户，实测 alice 查 default 被强制回本租户） |
| Tenant 页用量 | 操作列「AI 用量」→ Drawer（近 30 天明细+合计），实测显示 thu 14 次/17843/2276 |
| 知识库 usage | `_rag_chat_stream` 补 done 事件，token 落账（实测 tenant1 770/23） |

验证中发现并修复（本轮续做）：
1. `childrenRender` 的 `props.location` 可能为空 → 悬浮按钮不渲染；改用 `history.location.pathname`。
2. 流式回复**重复两遍**：`reply_streamed` 标志在域子图映射中丢失 → runner 兜底误补发；改为「最终回复未包含在已推送文本中才补发」（`TokenSink.emitted_text` 判重）。
3. 环境坑：worktree ui 用 junction 共享 master node_modules 时，`.cache/mfsu` 同目录互踩（MFSU dist not found）——worktree UI 验证不可行，按「先合并、master dev 验证、小修直接提交 master」流程走。
