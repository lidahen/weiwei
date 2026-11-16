# AI 服务优化 — 上下文摘要（2026-08-29 节点）

> ## 当前快照（新会话先读这 8 行）
> - **master = 商业化 + 租户体系五轮 + AI 优化 + C 端导购入口完成**；租户隔离模块已完成（盘点见 `tenant-round5-summary.md`「完成度盘点」节）。
> - **运行态**：后端 master jar 跑 8080；**Python ai-service 跑 8001（本机 2026-08-29 首次真正跑起来，依赖已装 requirements.lock.txt，Python 3.14.5）**；UI dev 8000；Redis 6379；MySQL root/123456/twelvet-fast。
> - **本轮（AI 优化四合一）**：导购聊天真流式（首 token 2.7~10s，旧=全图跑完才吐字）+ 超时/重试加固 + `ai_usage_daily` token 级计量 + `/ai/usage/daily` 月账单端点 + RAG siliconflow rerank。
> - **文档地图**：本轮细节 `docs/ai-optimize.md`；spec `docs/superpowers/specs/2026-08-29-ai-optimize-design.md`；租户线 `docs/tenant-round5-summary.md`。
> - **验证基线**：8 步矩阵全过（流式/透传/落账/月账单/重排/稳定性/知识库回归），详见 `docs/ai-optimize.md`。
> - **环境坑**：登录期不能 getLoginUser（取租户用 TenantContext）；undici 剥 Host；Windows jar 锁；Git Bash curl 发中文=GBK 会 400（payload 用 UTF-8 文件 --data-binary）；SseEmitter 输出 `data:{` 无空格。
> - **下一步**：见文末（rerank 效果评测 / 缓办项：角色租户化第二步、C 端注册、支付记录页）。

> 下次继续这个工作，先读这个文件。前置：tenant-round5 已合 master。

## 当前位置

- **Branch**: `feature/ai-optimize`（worktree `.worktrees/ai-optimize`，已合并 master 则删）
- **改动面**：`ai-service/`（Python：stream/llm/nodes/runner/config/rag/usage/chat 路由）+ `twelvet-ai-server`（WebClient 超时/租户头/AiUsageController）+ `twelvet-product-server`（同前两者）

## 已完成 ✅（8 步矩阵全过，详见 `docs/ai-optimize.md`）

| 目标 | 状态 |
|---|---|
| 导购聊天真流式（TokenSink ContextVar + astream，不改 LangGraph state） | ✅ 首token 2.7~10.2s（旧=21s 全图跑完才吐字） |
| done 事件带 usage；非流式 ChatResponse 带 usage | ✅ |
| Java WebClient 超时（connect 5s / response 180s，两模块各自配置类，bean 名不同防单体冲突） | ✅ |
| Python LLM timeout 60s + max_retries 1 | ✅ |
| `ai_usage_daily` 表 + Python 落账（X-Tenant-Id 透传）+ `/ai/usage/daily` 端点 | ✅ 实库已执行 |
| RAG rerank（siliconflow bge-reranker-v2-m3，失败回退原序，`rerank_enabled` 开关） | ✅ 真实 API 验证 |
| 验证中修复：无 session 导购对话必挂（checkpointer 缺 thread_id 存量 bug）+ reply_streamed 兜底标记 | ✅ |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 流式通道 | ContextVar[TokenSink(queue)] 跨任务传 token | Queue 不可序列化不能进 LangGraph state（破坏 checkpointer）；子任务继承 context |
| usage 采集 | 最终回答 AIMessage.usage_metadata，done 事件/非流式字段上送 Java→Python 落账 | 口径在 Python 侧单点落库（免 Java mapper 跨模块复制）；Java 只读 |
| 落账触发 | Java 代理透传 X-Tenant-Id，Python chat 完成时 upsert | 无租户头跳过（匿名/平台）；落账失败只告警不阻塞聊天 |
| rerank | siliconflow /rerank（同 embedding key），粗排 top_n×4→精排 top_k | 零新密钥；失败回退原序保可用性 |
| bean 命名 | ai-server `aiWebClient` / product-server `aiGuideWebClient` | 单体 @ComponentScan 下同名配置类冲突（实测 ConflictingBeanDefinitionException） |
| 调用计数 | AiQuotaChecker（Redis 次数+429）不动 | 兼容 round3 语义 |

## 已知边界（不掩盖）

- 工具迭代也 astream：模型「叙述+调工具」同轮会提前推叙述（DeepSeek 罕见）。
- 知识库聊天只计 calls，usage 未采集；用量 UI 未做（端点就绪）。
- C 端（marketplace）没有 AI 导购聊天入口——现有消费方是管理端 ShoppingGuide 页；C 端入口是独立的新 UI 工作。
- 单元测试 0（运行时矩阵惯例）。

## 下次续做的优先级（按 ROI）

- [x] **C 端 AI 导购入口**（AiGuideFloat 悬浮+Drawer，浏览器实测流式/商品卡片/落账，master `eaee38e`+`321c5c7`）
- [x] 租户管理页 AI 用量 Drawer（超管可按租户查，/ai/usage/daily?tenantCode=）
- [x] 知识库聊天 usage 采集（done 事件，实测 default 770/23）
- [ ] rerank 效果评测（构造评测集对比 top-k 命中率）
- [ ] 缓办项：C 端注册（等阿里云短信）、角色租户化第二步、支付记录页、审计日志

## 环境坑（本会话新增）

- **ai-service 依赖此前从未安装**（round3「上游拒连仍计数」的真相）；Python 3.14.5 + requirements.lock.txt 可直装（faiss-cpu 1.15 有 wheel）。
- Git Bash curl `-d '中文'` 发 GBK → FastAPI 400 "error parsing the body"；payload 写 UTF-8 文件 `--data-binary @file`。
- Java SseEmitter 透传后 SSE 行是 `data:{`（无空格），Python 原生是 `data: {`——解析兼容两种。
- Windows 下同名单例配置类冲突（ConflictingBeanDefinitionException）——多模块配置类命名要全局唯一。
- 独立验证脚本跑 asyncio 注意 Windows proactor「Event loop is closed」收尾噪音（用 grep 过滤业务输出）。

## 启动方式

```bash
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes
cd E:/code/weiwei/ai-service && python start.py          # 8001（先确认 pip 依赖已装）
cd E:/code/weiwei/backend && java -jar twelvet-admin/target/twelvet-admin.jar
cd E:/code/weiwei/ui && node_modules/.bin/max dev
# 流式导购：AI知识库 → 导购对话（ShoppingGuide 页）；或 curl /product/ai-guide/chat/stream
# 用量：GET /ai/usage/daily?days=30（带租户上下文登录态）
```

## 回滚

四部分独立：不传 stream=旧非流式；`rerank_enabled=false`；`DROP TABLE ai_usage_daily`；`git revert`。

---

**AI 优化四合一完成。下一步建议：C 端导购入口（把流式能力交付给真实用户）。**
