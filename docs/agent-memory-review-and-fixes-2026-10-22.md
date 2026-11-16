# Agent 长期记忆方案：评审结论与改进指令

> **面向读者**：负责本功能后续改进实施的 Agent。
> **评审日期**：2026-10-22
> **评审对象**：`docs/superpowers/specs/2026-10-22-agent-long-term-memory-design.md`（651 行）、`docs/superpowers/plans/2026-10-22-agent-long-term-memory.md`（1057 行）、已落地代码（截至 commit `040f596`）。
> **一句话结论**：架构选型成立、工程质量明显高于同类设计，Task 5–12 可以继续按 plan 实施；但**有一条硬伤会让 trait 层在生产环境永不生效**（P0-1），必须先决断再往下做。

本文件分两部分：**第 3 节**是缺陷修复项（P 系列，改已有实现）；**第 6 节**是能力升级路线（U 系列，借鉴 Mem0 / Zep / A-MEM / MemOS 中可迁移到本架构的能力，按投入产出比排序）。两部分都给出**依据**（文件＋行号＋推理链）与**验收方法**；凡是没有依据的判断，一律标注为「推测」或「不确定度」。

## 决策摘要（已拍板 / 待数据）

> **这一节放在最前面，是有意的**：本功能由多个 Agent 接力实施，接手者应**先看清哪些是必须遵守、哪些可自行裁量**，再看后面的依据。
> 本功能由**多个 Agent 接力实施**（本文件更新时，Task 1–6 已落地并核实）。因此明确区分「必须遵守」与「可自行裁量」，比罗列建议更重要。

| 项 | 状态 | 结论 |
|---|---|---|
| **P0-1** trait 层判据 | ✅ **已拍板** | 采用**时间窗判据**：`换了 session_id` **或** `距上次命中 ≥ agent_memory_session_gap_hours`（默认 **6 小时**）即计一次独立命中。**必须在 Task 9 之前生效** |
| P0-1 的「trait 层是否保留」 | ⏳ **待数据** | 先把计数改对；trait 层去留由 P0-2 的跨时间窗分布决定。届时按 P0-1「若数据不支持」条目收尾，**无需回滚计数改动** |
| **P0-2** 观测与评测集 | ✅ **已拍板** | 加极简轮次日志表 + 手工评测集，**与 Task 9 同批落地** |
| **P2-1** 租户归属 | ✅ **已拍板** | **写路径以 `sys_user.tenant_id` 为准**；读路径可用 `X-Tenant-Id` 做快速路径 |
| **P2-2** spec §8.3 列清单 | ✅ **已拍板** | 按 `store.py` 的 `_ROW_FIELDS` 回填（文档落后于代码） |
| **P2-4** Task 10 两条约束 | ✅ **已拍板** | 提取任务自建 DB session；`create_task` 必须登记强引用（照抄 `home_widget.py:370-377`）。无裁量空间 |
| P1-3 淘汰口径 | ✅ **已拍板** | 采纳 §6 的 U1 统一口径（`score`）；**若 U1 暂不做**，退化为按 `effective_confidence` 淘汰 |
| P1-1 / P1-4 / P2-3 | ✅ **已给方案** | 随 Task 7 / 8 / 12 顺带落地，插入点见 §5 的表 |
| P1-2 置信度下限、U1 的权重与半衰期、P3-1 提取闸门 | ⏳ **待数据** | 参数值属产品判断；输入是 P0-2 的评测集与真实分布 |
| U1–U6 | ⏳ **有前置** | 见 §6.0；前置未满足不要提前做 |

> 标注「待数据」的项，**不要凭猜测取值**。

---

## 0. 开工前必读

1. **遵循项目既有工作流**：`CLAUDE.md:3` 要求"做任何工作都需要先阅读 `Superpowers新功能开发指令.md`，按照规范去工作"。本文件的改进项应当按该规范拆成 spec 修订 + plan 任务，再逐任务实施。
2. **架构不变量**（`CLAUDE.md:19-22`）：前端只与 Java BFF(8080) 通信，任何 `/api/**` 直连 8001 都是 bug；Python 侧部分接口**强制要求 `X-User-Id`**（只有 Java 会注入），直连必然 401。这会直接影响 Task 12 的验收方式，见 P2-3。
3. **文档与代码必须同步**：`ai-service/app/services/memory/constants.py` 的模块 docstring 明确写了"这里是 spec §3.2 / §7.2 / §10.1 的代码真源，改动必须与文档同步"。因此**任何改进项落地后都要回写 spec**，否则会累积 P2-2 那类漂移。
4. **不要在实施中顺手"优化"第 2 节列出的设计决定**——它们是被刻意选择的。

---

## 1. 现状基线（可复现）

### 1.1 任务进度（快照，非实时状态）

> ⚠️ **本表是某一时刻的快照**。本功能由多个 Agent 接力实施，进度会持续变化。
> 接手前请先跑 §1.1.1 的自检，**不要依据本表做"某文件还不存在 / 已存在"的判断**。

| 状态 | Task | 内容 | commit |
|---|---|---|---|
| ✅ | Task 1 | 建表脚本 + ORM 模型 + 配置项 | `7eef68b` |
| ✅ | Task 2 | `memory/constants.py` + `memory/logic.py`（词表 / PII / 衰减 / 渲染） | `0e80eaf` |
| ✅ | Task 3 | `logic.plan_upsert`（UPSERT 规划与证据滚动） | `f256ad2`、`7b58292` |
| ✅ | Task 4 | `memory/store.py`（DB 层） | `81de96d`、`040f596` |
| ✅ | Task 5 | `memory/context.py` + `memory_recall` 节点接入主图 + 读取路径降级 | `b55b668` |
| ✅ | Task 6 | `_ensure_persona` 注入记忆块 + 每轮剔除历史 system 快照 | `e54a72e` |
| ⬜ | Task 7 | `interestHint` 全链路修复（state 字段化） | — |
| ⬜ | Task 8 | `_trace_tool` 参数白名单 + `entry_node` 重置 `trace` | — |
| ⬜ | Task 9 | `memory/extract.py` 规则层（提示词 / 输入构造 / 解析 / 行为证据） | — |
| ⬜ | Task 10 | 提取编排与 runner 接线（异步、不阻塞回复） | — |
| ⬜ | Task 11 | `memory/aggregate.py` —— 聚合器与特质重算 | — |
| ⬜ | Task 12 | 端到端验证与全量回归 | — |

**快照基准**：commit `e54a72e`。已核实 Task 5 / 6 的代码确实落地（`graph.py:63`、`:78-79` 接入节点；`nodes.py:248-267` 召回节点、`:482`/`:498-500` 人设注入、`:309-311` chitchat 注入）。

### 1.1.1 自检（接手前先跑）

```powershell
cd F:\code\weiwei

# 1) 主图是否已接入记忆召回（Task 5）
Select-String -Path ai-service\app\services\agent\graph.py -Pattern 'memory_recall' -SimpleMatch

# 2) 人设是否已注入记忆块（Task 6）
Select-String -Path ai-service\app\services\agent\nodes.py -Pattern 'memory_context' -SimpleMatch

# 3) 写入侧（Task 9-11）是否已开始 —— 这两个文件是 P0-1 决策的落地对象
Test-Path ai-service\app\services\memory\extract.py
Test-Path ai-service\app\services\memory\aggregate.py

# 4) 本功能相关提交
git log --oneline --grep 'memory' -20
```

> **P0-1 的时间窗**：只要 `memory/extract.py` **仍不存在**，写入侧判定就还没固化，P0-1 可直接按第 3 节的决策实现，**无需迁移**。

### 1.2 测试基线

```powershell
cd F:\code\weiwei\ai-service
C:\Users\lixin\AppData\Local\Python\pythoncore-3.14-64\python.exe -m pytest tests/test_agent_memory.py -q
# 实测：52 passed in 3.15s
```

> 说明：这 52 个用例全部是**结构性断言**（DDL↔ORM 防漂移、PII 拦截、UPSERT 三分支、衰减数学、空块省略）。它们能防回归，**但回答不了"记忆有没有让推荐更准"**——这正是 P0-2 要解决的问题。

### 1.3 已验证的既有实现（评审确认无误，不必重查）

- `_load_row` 故意**不带 `status` 条件**（`store.py:307-326`）：软删行仍占 `uk_user_mem` 键位，漏读会让后续 INSERT 撞 `Duplicate entry` 并被吞成 warning。这条是模块里最不能省的一行。
- 特质重算走 **UPSERT 而非"先软删再 INSERT"**（spec §7.3、`store.py:329-409`）：抓的是真 bug，改法正确。
- `_UPDATE_SQL` 的 WHERE 除主键外再带唯一键五列（`store.py:74-84`）+ `rowcount == 0` 显式失败（`store.py:397-403`）：防"报告成功但什么都没写"，正确。
- `roll_evidence` 兼容 `None / list / JSON 字符串`，脏数据只告警且**绝不吞掉新证据**（`logic.py:205-231`）：正确。
- 异步后台任务的写法在项目里**有正确先例**：`home_widget._spawn_weather_refresh`（`home_widget.py:370-377`）把 task 登记进 `_weather_inflight` 并 `add_done_callback` 摘除——这是 asyncio 官方要求的**强引用**做法。Task 10 必须照抄这个模式，见 P2-4。

---

## 2. 不要动的设计决定（刻意的正确选择）

| 决定 | 为什么是对的 | 依据 |
|---|---|---|
| `mem_key` 封闭词表（12 事实 + 4 特质） | 正面解决 Mem0 / A-MEM 最难治的病：同一件事记成不同 key、画像发散。Zep 靠实体消解、A-MEM 靠 link generation，成本高得多 | spec §3.2、`constants.py:14-35` |
| 行数常数（用户数 × ≤50，与对话数无关） | 多租户 SaaS 的决定性优势：每租户成本可预测。对比 Mem0 的库随对话线性增长 | spec §4.2、§11 |
| 不上向量检索 | ≤50 行 + 封闭词表，按置信度排序足够；向量收益极低而成本明确（embedding 调用 + 索引维护） | spec §2「不做（YAGNI）」 |
| 不上图（Zep / HippoRAG 式） | 导购场景是单用户偏好查询，**没有 multi-hop 需求**；图记忆的建图成本 + 抽取误差 + 调试难度在这里是纯负债 | spec §2 |
| 记忆不落 Redis | 记忆必须长期存活，Redis 有 TTL | spec §2 |
| `evidence` 内联 JSON + 滚动 5 条 | spec §11 已算过：**证据流水表才是会炸的设计**（550 万行/年），行数不是 | spec §4.1、§11 |
| 读时算衰减、零定时任务 | 衰减是纯计算、无状态；定时任务只会引入"何时跑/跑多久/失败怎么办" | spec §8.1 |
| 全链路静默降级，但日志不静默 | 记忆是增强功能，不该拖垮主链路；但静默失败必须可 grep | spec §12、`logic.py` / `store.py` 的 warning 密度 |

> ⚠️ 唯一建议推翻的 YAGNI 是 **P1-1**（`evidence` 里补存被覆盖的旧值）——它不是"额外功能"，而是**覆盖语义的必要补偿**。

---

## 3. 改进项

### P0-1 trait 层判据与前端 session 语义不匹配 → trait 层在生产环境永不激活

#### 问题

升格为特质的门槛是 `hit_count >= 3 且 session_count >= 2`，但**写入侧的"同一会话"判定，在当前前端实现下会永久成立**，导致这两个计数永远停在 1，门槛永远不满足。

#### 依据（三段链，三段都成立，结论才成立）

**第 1 段 — 判据要求"跨会话"**

- `store.py:41-49`（`_LOAD_PROMOTABLE_SQL`）：`AND hit_count >= :min_hits AND session_count >= :min_sessions`，默认 `min_hits=3`、`min_sessions=2`（`config.py:155-156`）。
- spec §7.1（`2026-10-22-agent-long-term-memory-design.md:304-308`）：同上。

**第 2 段 — 同一 `session_id` 内计数不增长**

- `logic.py:283-286`：

  ```python
  if str(_get(existing, "last_session_id") or "") == str(session_id):
      hit, sessions = old_hit, old_sessions  # B：同会话去重
  else:
      hit, sessions = old_hit + 1, old_sessions + 1  # C：跨会话累加
  ```

- 而每次写入都会把 `last_session_id` 更新为当前 `session_id`（`logic.py:274`、`logic.py:293`）。
- 所以：**只要 `session_id` 不变，此后每次命中都走 B 分支，`hit_count` / `session_count` 恒为 1。**

**第 3 段 — 前端 `session_id` 是 localStorage 持久化的，只在用户主动点「新对话」时才轮换**

- `ui/src/pages/marketplace/AiGuideFloat.tsx:49-56`：`getSessionId()` 先读 `localStorage.getItem(SESSION_KEY)`，只有取不到时才生成新 id 并写回。
- `AiGuideFloat.tsx:69`：`useRef<string>(getSessionId())` → 组件挂载即复用持久化的 id。
- `AiGuideFloat.tsx:87-93`：唯一的轮换点是 `newChat()`，即用户点「新对话」按钮。
- 同样的模式存在于另外两个入口：`ui/src/pages/AI/ShoppingGuide/index.tsx:53-57`、`:144-146`；`ui/src/pages/AI/Butler/index.tsx:69-73`、`:160-162`。**这是三个入口的系统性行为，不是某一处漏写。**

**推理链**：导购悬浮入口是一个抽屉，用户会反复打开使用，但极少点击「新对话」。于是 `session_id` 可能数月不变 → 永远命中 B 分支 → `hit_count` / `session_count` 恒为 1 → **升格门槛永不满足**。

**反证（什么条件下才不成立）**：只有当"用户会频繁点击新对话"或"前端每次进入都轮换 id"时，判据才有效。而后者会摧毁 LangGraph checkpoint 的多轮上下文（`thread_id = session_id`，见 spec §1.1），不能采用。

#### 波及范围（必须说准，避免过度修复）

| 层 | 是否受影响 | 说明 |
|---|---|---|
| 事实层（fact） | **不受影响** | `mem_value` / `confidence` / `evidence` 照常更新（`logic.py:288-298`），预算、类目、明确拒绝这些硬约束仍然可用 |
| 特质层（trait） | **完全失效** | 门槛永不满足，`aggregate`（Task 11）在生产上是死代码 |
| 注入块计数展示 | 错误 | `logic.py:163-165` 渲染的"（证据 N 次 / 跨 N 次会话）"恒为"1 次 / 1 次"。用户看不到（只在 system prompt 内），但**模型会据此判断证据强度**——一条置信度 0.9 的稳定偏好永远被标成弱信号 |
| spec §1.3 成功标准 2 | **无法达成** | "在 3 个不同会话反复表达要便宜 → 第 4 次会话话术优先突出性价比" |

#### 改法（**已拍板：采用方案 A**）

> **这是决策，不是建议。** 写入侧判定必须在 Task 9 实现 `memory/extract.py` **之前**按下列口径落地——判定一旦实现就会固化，之后再改需要迁移（当前 `memory/extract.py` 尚不存在，窗口仍然开着）。

**A. 用"时间窗"替代 `session_id` 作为独立性判据（零 schema 改动）**

把 C 分支的触发条件从"换了会话"改为"距上次命中已超过 N 小时"：

```python
# logic.py，plan_upsert 内的 B/C 分流处（当前 :283-286）
# 判据：换会话 或 距上次命中超过间隔窗口，才计一次独立命中
same_session = str(_get(existing, "last_session_id") or "") == str(session_id)
fresh = (now - (logic._naive(_get(existing, "last_seen")) or now)) >= timedelta(hours=gap_hours)
if same_session and not fresh:
    hit, sessions = old_hit, old_sessions          # B：同一会话且窗口内 → 去重
else:
    hit, sessions = old_hit + 1, old_sessions + 1  # C：独立命中
```

配套改动：

1. `config.py:148-159` 追加 `agent_memory_session_gap_hours: int = 6`（命名与注释需说明它替代了 `session_id` 语义）。
2. spec §6.3 的 A/B/C 表格、§7.1 的触发条件、§13 的配置项表同步更新。
3. **语义重命名**：`session_count` 的含义从"跨会话次数"变成"独立命中批次数"。列名不改（改动成本高于收益），但 `logic.py:124-176` 的渲染文案与 spec §9.2 的示例需要改口径，否则注释会误导后来人。

**为什么改在写入侧而不是读取侧**：`evidence` 只保留最近 5 条（`constants.py:48`），无法从 evidence 重建完整的命中时间分布——所以必须在写入时就把"独立命中"判定下来。

**B.（不推荐）前端每次打开抽屉轮换 `session_id`**：会摧毁 `thread_id=session_id` 的 checkpoint 上下文，等于把 spec §1.1 要修的问题自己造回来。

#### 验收

1. 单测（`tests/test_agent_memory.py`）：
   - 同一 `session_id`、`last_seen` 距今 **1 小时**、连续 UPSERT 3 次 → `hit_count == 1`、`session_count == 1`（B 分支仍生效，保住"一句话刷成特质"的防线）。
   - 同一 `session_id`、`last_seen` 距今 **7 小时** → `hit_count == 2`、`session_count == 2`（C 分支生效）。
   - 3 个不同 `session_id` → 计数正常累加（原有用例不许回归）。
2. 端到端：同一 `userId` + **同一个** `sessionId`，跨 >6 小时的三轮表达"要便宜" → 第 4 轮应产生 `memory_type='trait'` 行。
3. 回归：`store.py` 的 `_LOAD_PROMOTABLE_SQL` 不改（它只读计数，判定已在上游完成）。

#### 风险与备选

- **窗口取值（已拍板）**：采用 **6 小时** 作为默认值，并做成配置项 `agent_memory_session_gap_hours`。这是**推测性默认值**（依据：一个连续购物会话通常不超过数小时），**待 P0-2 的真实会话节奏校准**——校准之前不要凭感觉调整。
- **若数据不支持（已定的收尾方式）**：若 P0-2 显示"跨时间窗重复"的用户占比极低，则**把 trait 层从这一版砍掉**，并在 spec 里显式记为「未交付」（fact 层 + 行为证据照旧保留）。**注意：这与上面的计数改动不冲突**——把计数改对是无论如何都要做的（否则注入块的"证据 N 次"恒为 1，永远在误导模型），trait 的去留是另一件独立的事。**不要**因为"将来可能要砍 trait"就推迟计数修正。

---

### P0-2 关键决策缺少数据来源：跨会话率无法度量，评测集也没有语料

#### 问题

trait 层的全部价值建立在"用户会跨会话/跨时间段反复表达同一偏好"这一假设上，但**当前没有任何数据能验证这个假设**；同时 Task 12 的验收只有人肉手工一条路，无法回归。

#### 依据

1. **导购对话不落库**：spec §10.3（`:513-517`）明确"不存完整对话（对话本来也只存 Redis 7 天）"。
2. **库里唯一的 `ai_chat_history` 不是导购会话表**：`docs/db/twelvet-fast_2026-10-21.sql:65-76` 显示其列是 `msg_id / knowledge_id / session_id / role / content`，并有 `idx_knowledge_id`；`:85` 的实测数据只有 19 行、`session_id` 全为 `NULL`、内容全是知识库问答（"项目使用什么语言开发的"）。**它是 RAG 知识库问答的历史表，与导购 Agent 无关。**
3. 全库 `ai_` 前缀表清单（`docs/db/twelvet-fast_2026-10-21.sql`）中不存在导购会话/轮次表：`ai_chat_history / ai_creation_project / ai_doc / ai_doc_slice / ai_image_task / ai_knowledge / ai_model / ai_moderation_log / ai_music_session / ai_music_work / ai_persona / ai_storyboard_shot / ai_usage_daily / ai_usage_log / ai_video_task / ai_viral_template / ai_voice_profile`。
4. Redis checkpoint 的 `thread_id=session_id`、TTL 7 天（spec §1.1），**过期即不可回溯**。

**结论**：`hit_count` / `session_count` 的分布、以及"有多少用户会产生跨时间窗命中"，现在都算不出来。没有它，就无法回答"trait 层值不值得做"，也无法在 Task 12 之后发现效果退化。

#### 改法

1. **加一张极简轮次日志表**（建议名 `ai_agent_turn_log`），每轮一行，**只存结构化元数据，不存对话原文**（守住 spec §10.3 的数据最小化）：
   `id / tenant_id / user_id / session_id / intent / has_tool_calls / ts`。
   写入点：放在 Task 10 的提取调度附近（`runner.chat` / `chat_stream` 拿到最终结果后），与提取任务共用一次 DB 写入机会。
   **落 `backend/data/` 下一个独立的幂等 SQL 脚本**，遵循 `agent-memory-init.sql` 的惯例（头部注释 + 回滚 SQL + `CREATE TABLE IF NOT EXISTS`）。
2. **评测集单独手工构造，不要靠回放生产对话**。原因：存原文与 §10.3 冲突；而"回放"必须先有原文。可行做法是从你自己手工测试的真实对话里整理 30–50 条"应当被记住"的问题，写成断言式 fixture：
   - 输入：一段对话（user/assistant 交替）
   - 断言：期望产出哪些 `(memory_type, mem_key, scope, mem_value 近似)`；以及**不期望**产出什么（负例同样重要，例如"帮朋友买"不应计入本人偏好，见 spec §6.2 判断规则 2）
   - 这一步不需要启动服务，可以直接驱动 `memory/extract.py` 的纯函数层（Task 9 的设计已支持无真实 LLM 测试）
3. 若不想加表，**最低限度**要把 `_log_agent_trace`（`runner.py:130`）的输出改成可结构化解析（JSON 行），用日志做统计——但 SQL 查询与回归回放会麻烦得多，不推荐。

#### 验收

- 新表能被 `SHOW CREATE TABLE` 验证，唯一键/索引合理（建议 `KEY (tenant_id, user_id, ts)`）。
- 用一条 SQL 能算出跨时间窗分布，例如：

  ```sql
  -- 每个用户产生了多少个"独立命中批次"（P0-1 改完之后）
  SELECT batches, COUNT(*) AS users FROM (
    SELECT user_id, MAX(session_count) AS batches
    FROM ai_user_memory WHERE memory_type='fact' GROUP BY user_id
  ) t GROUP BY batches ORDER BY batches;
  ```

- 评测集能作为 pytest 用例运行，且在改动检索/提取逻辑时会失败/通过（证明它真的在测行为，而不是在测结构）。

---

### P1-1 冲突覆盖会丢掉"当时的值"，且现有 `evidence` 无法完全补偿

#### 问题

用户说"现在预算 1000 了"，`plan_upsert` C 分支**直接覆盖** `mem_value`。旧值不是完全丢失，但可恢复性很差。

#### 依据

- `logic.py:288-298`：返回的 `dict` 里 `"mem_value": value`（新值直接覆盖），`"confidence": new_conf`（取新观测值）。
- `logic.py:205-231`：`roll_evidence` 存的是 `{"text": <原话片段>, "at": ..., "session": ...}`——**是原话，不是结构化的旧 value**；且只保留 `EVIDENCE_KEEP = 5` 条（`constants.py:48`）。
- spec §8.2（`:370-374`）自述："不再保留 50 作为有效值（旧证据仍在 evidence 窗口里，可审计）"。

**因此风险有三点**（`evidence` 只能算部分补偿）：
1. **无法直接查询"当时的值"**：`mem_value` 历史只以非结构化原话残留在 JSON 里，`SELECT` 不出来。
2. **第 6 次覆盖后彻底消失**：滚动窗口只有 5 条。
3. **原话 ≠ 结构化值**：`text` 由 LLM 抽取（spec §6.2 要求 evidence 必须能摘出原话），未必忠实于当时的 `mem_value`。

#### 改法

C 分支覆盖前，把被覆盖的旧值推进 evidence 条目（**不新增列**）：

```python
# logic.py，plan_upsert 的 C 分支（非首次写入时）
# 覆盖前留存旧值：旧 evidence 保留原话，新条目额外带 old_value 便于结构化回溯
entry = {"text": ..., "at": at, "session": session_id, "old_value": existing["mem_value"]}
```

更省的做法：给 evidence 条目加一个可选字段 `superseded`（被本次覆盖的旧值），读取侧/运营侧可用一条 SQL 或小脚本还原"值演化链"。

#### 依据为什么值得做

- 这是**用最低成本换取 Zep 双时间轴约 80% 的价值**：Zep 的核心竞争力就是"历史不覆盖、可回溯"（`arxiv.org/abs/2501.13956` 摘要：edge 带 `t_valid/t_invalid`）。
- spec §1.3 成功标准里没有"历史查询"，所以 spec 按 YAGNI 不做完整时间轴是对的；但**完全不留结构化旧值**会让日后无法回答"他以前预算多少"，而这在真实客服/纠纷场景里是会被问到的。属"现在 5 行、以后省一次重构"的投入。

#### 验收

- 用例：`预算上限` 从 `50元以内` 覆盖为 `1000元以内` 后，`evidence` 中能找到 `old_value == "50元以内"` 的条目。
- 滚动窗口行为不变：仍只保留 5 条，最新在前。

#### 风险与备选

若判定"值演化链"确实无用（例如产品明确不需要历史），**也可以不做**——但要在 spec 里显式记为已知取舍，而不是默默覆盖。

---

### P1-2 覆盖 + "confidence 取新观测值" 会互相放大，可能让确证偏好被降级

#### 问题

`confidence` 取**新观测值**（`logic.py:263`、`:290`），逻辑自洽（注释理由：它描述"当前这个值有多可信"，把旧的 0.9 带到新的 0.7 观测上等于让新值冒领旧权重）。但它与 P1-1 的覆盖组合后，会出现：用户某轮含糊说了句 → 新 `confidence=0.5` **直接盖掉**原来 `0.9` 的确证偏好，且旧值只留在 evidence 里。

#### 依据

- `logic.py:263`：`new_conf = _round_confidence(confidence)`。
- `logic.py:288-298`：返回值里 `"confidence": new_conf`，无论 A/B/C 分支。
- spec §6.2 提示词第 5 条：`confidence` 明确陈述 0.8~0.9、间接体现 0.6~0.7、语气含糊 ≤0.5——即模型会给出低置信度观测。
- 交互风险：**低置信度新值 + 覆盖 + 无下限** → 一条已确证的硬约束被降权。

#### 改法（二选一）

- **A（推荐，最小改动）**：覆盖时给置信度一个衰减下限，`new_conf = max(new_conf, old_conf * 0.7)`，避免单次含糊观测把确证偏好打回原形。需在 `logic.py` 与 spec §6.3 同步说明。
- **B（更稳，稍重）**：连续两次低置信度新值才允许替换（需要一个小的状态位，例如在 `evidence` 记 `pending_value`）。

#### 验收

- 用例：`confidence=0.9` 的行被 `confidence=0.5` 覆盖 → 结果 `confidence >= 0.63`（A 方案）。
- 反向用例：同一值被更高置信度观测时，`confidence` 应如实升到新值。

#### 不确定度

这是**产品体验层面的风险，不是正确性 bug**；spec 未讨论过该场景。建议在 Task 12 端到端跑通后再定，避免提前过度设计。

---

### P1-3 `evict_overflow` 按 `last_seen` 淘汰，会优先杀掉高置信度的低频偏好

#### 问题

`last_seen` 只在**命中**时更新（`logic.py:293` 的 `"last_seen": now`）。用户一年前提的"不要小米"是核心约束、`confidence` 很高，但一年没再提 → 行数超 50 时**它最先被淘汰**。

#### 依据

- `store.py:92-97`（`_EVICT_SQL`）：`ORDER BY last_seen ASC LIMIT :overflow`，`SET status = 0`。
- `store.py:524-550`（`evict_overflow`）：超限即调用，阈值为 `agent_memory_max_rows=50`（`config.py:152`）。
- 反面参照：读取侧用的是**衰减后置信度**（`logic.py:97-121`、`:139-153`），两者口径不一致——读取时认为"高置信度长期偏好应保留"，淘汰时却认为"久未命中就该死"。

#### 改法

把淘汰排序从"最久未命中"改为"**衰减后置信度最低**"，与读取侧口径统一：

```sql
-- MySQL 可在 SQL 内算衰减，避免把全部行拉到内存
UPDATE ai_user_memory
SET status = 0
WHERE tenant_id = :tenant_id AND user_id = :user_id AND status = 1
ORDER BY confidence * EXP(-LN(2) / :half_life_days * DATEDIFF(NOW(), last_seen)) ASC
LIMIT :overflow
```

若不想引入时间依赖的 SQL，退一步按 `confidence ASC, last_seen ASC` 排序（至少不让高置信度行先死）。

#### 验收

- 用例：构造 51 行，其中一行 `confidence=0.95`、`last_seen` 一年前，其余 50 行 `confidence=0.4` 且 `last_seen` 昨天 → 被淘汰的**不应**是那一行。
- 边界：淘汰后有效行数 == `max_rows`。

---

### P1-4 `deactivate_traits_except` 的空 `keep` 会一次清空该用户全部 trait

#### 问题

聚合器返回合法 JSON `{"traits":[]}` 时（语义是"本次没有特质"），会退役该用户**所有** trait 行。

#### 依据

- `store.py:470-521`（`deactivate_traits_except`）：`keep` 为空时展开成无 `AND NOT (...)` 的 SQL（`store.py:490-498`），即 `UPDATE ... SET status=0 WHERE ... AND memory_type='trait' AND status=1` —— 全部退役。
- 代码已经留了可 grep 的 warning（`store.py:494-497`），且 `parse_aggregate` 返回 `None` 可区分"解析失败"——**但 LLM 返回 `{"traits":[]}` 是合法结果，不走 `None` 分支**。
- 相关设计参照：`last_trait_seen` 故意不过滤 `status`（`store.py:278-301`），理由是防止"特质被清空后冷却失效、聚合器每轮重跑"。**P1-4 与它是同一个防范思路的补全**：那条防的是"冷却失效"，这条防的是"内容被误清空"。

#### 改法

加阻尼，二选一：

- **A（推荐）**：本次产出 ≥1 条 trait 才允许退役旧行；空结果直接跳过退役步骤（保留旧特质，等下一轮）。
- **B**：空结果需要连续 2 次才允许清空（需一个轻量状态，例如复用 `last_trait_seen` 或新增一行 `source='aggregate'` 的标记）。

#### 验收

- 用例：现有 3 条有效 trait，聚合器返回 `{"traits":[]}` → 这 3 行**仍是 `status=1`**。
- 用例：聚合器返回 1 条新 trait → 不在结果里的旧行被置 `status=0`（原行为不变）。
- 用例：`keep` 为 `None`（解析失败）→ 不发生任何退役。

---

### P2-1 `resolve_tenant_id` 以请求头优先，存在记忆串租户的风险

#### 问题

正常路径下租户直接采信 `X-Tenant-Id` 头（零额外查询），只有头缺失时才回查 `sys_user.tenant_id`。若 Java 侧传错头，记忆会写进**错误的租户桶**——而 `sys_user.tenant_id` 明明是对的。

#### 依据

- `store.py:194-222`（`resolve_tenant_id`）：`if hint_id > 0: return hint_id`（`:202-207`）优先于回查 `sys_user`（`:209-221`）。
- spec §9.1.1（`:427-437`）把 `sys_user.tenant_id` 定义为"权威归属"，但描述的是"头缺失/为 0 时回查"——**代码与文档一致，但都选择了头优先**。
- 严重性：记忆是**用户画像**。串租户意味着 A 租户的运营/用户能读到 B 租户用户的偏好，属**安全事故级**问题，而不是显示错误。
- 项目有多租户隔离的专项投入（`docs/tenant-isolation-2-summary.md`、`docs/tenants-round*.md`），说明这是该项目的红线区域。

#### 改法（二选一）

- **A（推荐）**：**写路径永远以 `sys_user.tenant_id` 为准**（可加进程内小缓存，避免每轮一次查询），**读路径**才允许用头做快速路径。理由：写错了会污染另一个租户的数据，读错了只是本次注入偏差。
- **B（更省）**：保留头优先，但做一次轻校验——对 `(user_id, tenant_id)` 做短 TTL 缓存比对，不一致时以 `sys_user` 为准并记 warning。

#### 验收

- 用例：`hint=9` 但 `sys_user.tenant_id=1` → 写入落在 `tenant_id=1`（A 方案），且留一条可 grep 的 warning。
- 用例：头缺失 + `sys_user` 有值 → 写入 `sys_user.tenant_id`，**不是 0**（这是 spec §9.1.1 原本的目标，不能回归）。
- 用例：两者都拿不到 → 不读不写。

---

### P2-2 文档漂移：spec §8.3 的 SELECT 漏了渲染必需的列

#### 问题

- spec §8.3（`:378-384`）的 SQL 只 `SELECT memory_type, mem_key, mem_value, confidence, scope, last_seen` 六列。
- 但渲染文案需要 `hit_count` 与 `session_count`（spec §9.2 示例 `:445`："（证据 5 次 / 跨 3 次会话）"，代码在 `logic.py:163-165`）。
- 实现侧已经补上了：`store.py:26-29` 的 `_ROW_FIELDS` 含这两列。

**所以这是"文档落后于代码"，不是代码 bug**——但按第 0 节第 3 条的一致性要求必须回填，否则下一个读 spec 的人会照抄一份缺列的 SQL。

#### 改法

把 spec §8.3 的列清单与 `store.py:26-29` 对齐，并顺手注明"列集合的真源是 `_ROW_FIELDS`"。

#### 验收

读 spec §8.3 手抄的 SQL 能直接喂给 `render_block` 而不缺字段。

---

### P2-3 Task 12 的验收方式与架构不变量冲突

#### 问题

plan Task 12 Step 2（plan `:1023`）建议 `uvicorn app.main:app --port 8001` 直连验证。但 `CLAUDE.md:19-22` 明确：Python 侧部分接口**强制要求 `X-User-Id`**（只有 Java 注入），直连必然 401。

#### 依据

- plan `:1021-1030` 的 Step 2 原文："`uvicorn app.main:app --port 8001`（另开终端直连，验证期绕开 Java）"。
- `CLAUDE.md:19-22`。

#### 改法

Step 2 补一句：直连需自行构造请求头 `X-User-Id`（以及按 §9.1.1 需要的 `X-Tenant-Id`，或故意不带以验证回查路径）。同时在验收记录里写明"该验证绕开了 Java 的鉴权/租户/配额链路，不能替代一次端到端验证"。

#### 验收

验收记录包含实际的 `curl` / HTTP 请求样例（含请求头），使别人能复现。

---

### P2-4 Task 10 的两个实现约束（spec 未写明，写错会静默失败）

#### 问题 1：提取任务的 DB session 必须新开，不能复用请求作用域的

请求早已返回（这正是异步的目的），请求作用域的 `AsyncSession` 此时已关闭或属于另一个生命周期。复用会出现**间歇性失败**，而失败会被"只告警"规则吞掉——正是本模块反复强调的最难排查的一类故障。

- 依据：spec §6.1（`:164-175`）只写了"`asyncio.create_task` 跑、异常全捕获只告警"，未提 session 归属。
- 改法：Task 10 里显式在任务内新建 session（与项目其他后台任务一致），并在 docstring 说明。

#### 问题 2：`create_task` 必须持有强引用

asyncio 官方文档明确警告：不保存返回值引用，任务可能在完成前被 GC。项目里有**正确先例**可照抄：

- `home_widget.py:370-377`（`_spawn_weather_refresh`）：`task = asyncio.get_running_loop().create_task(...)` → 登记进 `_weather_inflight` → `task.add_done_callback(...)` 摘除。
- 顺带收益：这个"在途登记表"可用于观测（在途提取数、异常率），与 spec §12「对日志不静默」的要求天然契合。

#### 验收

- 用例：在无 DB / DB 抛错的模拟下，提取任务吞异常且只留 warning，主链路不受影响（spec §14 已有此用例，需覆盖到新开 session 的路径）。
- 长跑验证（推测性建议）：连发 200 轮后确认没有"提取任务莫名不执行"的现象。

---

### P3-1（可选）用 `intent` 做提取闸门，可再省约一半提取成本

#### 依据

- spec §6.1（`:177-187`）的跳过条件不含 `intent`；`chitchat` / `clarify` 轮只要用户消息 ≥6 字就会跑一次提取 LLM。
- spec §6.4.1（`:273-282`）已经用 `intent` 决定**是否把 trace 当行为证据**，但**没有用它决定是否提取**。
- 成本侧（spec §11）：约 1.5 万次 flash/天。

#### 改法

在 `should_extract` 里加一条：`intent` 不在业务域集合内时跳过提取。**注意副作用**：闲聊里的稳定事实（"家里有猫"）就再也记不住了；因此建议**只跳过 `clarify`（纯流程轮）**，保留 `chitchat`。这一条需要产品判断，**不是纯技术优化**。

#### 验收 / 不确定度

成本下降可测；但对"记忆召回率"的影响需要用 P0-2 的评测集衡量。**建议在评测集建好之后再决定**。

---

## 4. 经核实的排除项（不要重复挖）

| 曾经的怀疑 | 结论 | 依据 |
|---|---|---|
| `_LOAD_MEMORIES_SQL` 按**基础** `confidence` 排序 + `LIMIT` 会挤掉"刚命中但基础分略低"的记忆 | **不成立** | spec §8.3 已钉死 `LIMIT 50`，而 `agent_memory_max_rows=50`（`config.py:152`）⇒ 每次都是全量读取，Python 侧再按衰减后置信度排序（`logic.py:139-153`）。**前提：Task 5 不要自作主张传 `inject_limit`(20)。** |
| `_load_row` 不带 `status` 条件是漏写 | **不是 bug，是必需的** | `store.py:307-326` 的 docstring 已说明：软删行仍占 `uk_user_mem` 键位 |
| 特质"先软删再 INSERT" | **已在设计层修掉** | spec §7.3；代码走 UPSERT（`store.py:391-404`） |
| 注入记忆会破坏提示词结构（换行伪造块级标记） | **已有防护** | `logic.py:59-66` 压平空白；`logic.py:171-176` 复用 `_DANGEROUS_PATTERNS` 加警示前缀 |
| PII 只扫了 `mem_value`，`evidence` 会漏 | **已有防护** | `logic.py:35-46` 是 varargs，两个参数都扫；spec §14 有用例钉死 |
| 记忆抽取会自我强化（把 Agent 自己编的内容记成用户偏好） | **已有防护** | spec §6.2 输入构造过滤 `SystemMessage`；§6.4.2 助手内容仅用于消解指代 |

---

## 5. 建议执行顺序

1. **先决断 P0-1**（改判据 / 或砍掉 trait 层并在 spec 记录）。这一条不决断，Task 11 可能白做。
2. **Task 5–8 照 plan 实施**（铺管道），同时落实 **P2-2**（回填 spec §8.3）与 **P2-4**（session 与强引用两条实现约束）。
3. **Task 9 落地时顺手做 P0-2 的表与评测集**——它是后续所有决策的度量基础，越早越好。
4. **Task 9–11 完成后立刻做 Task 12**，不要插入别的功能；Task 12 顺带落实 **P2-3**。
5. **Task 12 拿到真实数据后**再决定 P1-2、P1-3、P3-1 的具体参数（置信度下限、淘汰排序口径、提取闸门）。P1-1、P1-4、P2-1 属小改动，可随 Task 5–8 一起做。
6. 每一项落地后**回写 spec**（第 0 节第 3 条），并在 spec §17 的评审记录表里追加一行，说明本文件的哪一条被采纳/被否决及理由。
7. **缺陷修完之后**，再按**第 6 节**的 U 系列推进能力升级。U 系列每项都注明了前置条件，**前置未满足时不要提前做**。

**按 Task 顺序插入改进项**（实施 Agent 是逐 Task 顺序推进的，所以按「动手前必读」给出插入点）：

| 你正要做的 Task | 动手前必读 | 原因 |
|---|---|---|
| Task 7 | P2-3、spec §9.3 原有的 5 条 | 与 `interestHint` 修复同处 `runner.py` / `chat.py`，避免二次改动 |
| Task 8 | — | 无改进项依赖 |
| **Task 9** | **P0-1（已拍板）、P0-2、P2-4、P1-1** | 写入侧判定在此固化；轮次日志与评测集应同批落地。P1-2 只需留出位置，参数待数据 |
| Task 10 | **P2-4、P2-1** | 自建 DB session + 强引用登记表；写路径租户以 `sys_user` 为准 |
| Task 11 | **P0-1 的判据口径、P1-4、P1-3 或 §6 的 U1** | 升格门槛与特质退役都在这一个文件里 |
| Task 12 | P2-3、P0-2 的评测集 | 把成功标准 1 变成确定性断言（§6 U3 的验收） |

---

## 6. 能力升级路线（非缺陷类，U 系列）

> **与第 3 节的区别**：第 3 节修的是"已经写错 / 写漏"的地方（P 系列）；本节是"现在没错，但可以更强"的方向（U 系列）。
> **排序原则**：投入产出比。每项都注明**前置条件**；前置未满足的一律不要提前做。
> **外部依据的可信度说明**：本节借鉴的四篇论文——Mem0（[arXiv:2504.19413](https://arxiv.org/abs/2504.19413)）、Zep / Graphiti（[2501.13956](https://arxiv.org/abs/2501.13956)）、A-MEM（[2502.12110](https://arxiv.org/abs/2502.12110)）、MemOS（[2507.03724](https://arxiv.org/abs/2507.03724)）——其效果数字多为**厂商自测**，且 Mem0 的公开仓库存在复现争议（[issue #2800](https://github.com/mem0ai/mem0/issues/2800)、[#3944](https://github.com/mem0ai/mem0/issues/3944)）。因此本节**只借鉴机制，不引用其效果数字**；凡涉及收益的判断，都以本项目的架构约束为前提重新推导。

### 6.0 路线总览

| 编号 | 方向 | 借鉴自 | 投入 | 前置 | 建议 |
|---|---|---|---|---|---|
| **U1** | `mem_key` 元数据表（半衰期 / 权重 / 硬约束） | Generative Agents 的检索三因子；Zep 的时间语义 | 小（纯代码常量 + 排序口径） | P0-1 决断后 | **先做** |
| **U2** | `scope` 解析优先级（类目版覆盖全局版） | A-MEM 的 link expansion（用现成的 `scope` 结构替代图） | 小 | U1（需要"硬约束"标记） | **先做** |
| **U3** | 硬约束从提示词落到工具参数 | Mem0「写入即决策」思路的反向应用 | 中 | U1、Task 8 | **做**（决定成功标准 1 是"概率"还是"保证"） |
| **U4** | 只读诊断能力（provenance 轻量版） | MemOS 的 MemCube（provenance / versioning） | 小 | 无 | 做 |
| **U5** | 后台记忆巩固（合并抽象） | A-MEM 的 memory evolution；LightRAG 的增量 / 离线张力 | 中 | P0-2 评测集 | **默认不做**，让数据说话 |
| **U6** | 明确不采纳清单 | — | — | — | 见 6.6（防范围蔓延） |

---

### 6.1 U1 `mem_key` 元数据表：让"时效"与"重要度"按 key 分别配置

#### 机会

现在所有 key 共用一条衰减曲线、且排序里没有"重要度"维度。四个问题其实是**同一个根因**：key 缺少元数据。

#### 依据

1. `logic.py:97-121`（`effective_confidence`）对**所有** key 使用同一个 `half_life_days`；其唯一来源是 `config.py:153` 的 `agent_memory_half_life_days = 90`。
2. `constants.py:14-35` 把语义**完全相反**的 key 放在同一词表里：`决策阶段`（"在 A 和 B 之间纠结，倾向 A"）是**当前轮次的状态**，`常驻城市` / `明确拒绝` 是**长期事实**。统一 90 天衰减 ⇒ 三个月前的"正在纠结 A 和 B"仍以约 0.5 权重被注入，直接污染推荐话术。
3. `logic.py:139-153`（`render_block`）的排序键只有 `(-effective, mem_key)`：**没有重要度维度**。于是 `明确拒绝`（"不买小米"，强约束）与 `稳定身份`（"家里有猫"，弱相关）在同置信度下**排序完全相同**。
4. `store.py:92-97`（`_EVICT_SQL`）的淘汰只看 `last_seen`（已在 P1-3 指出）。

#### 改法

在 `constants.py` 增加 key 元数据表（纯代码常量，**零 schema 改动**）：

```python
# 每个 mem_key 的时效与重要度。未登记的 key 走 settings 的默认半衰期与默认权重。
# weight 参与两个地方：注入排序 与 淘汰排序（二者必须同口径，见 P1-3）。
KEY_META: dict[str, dict] = {
    "明确拒绝":   {"half_life_days": 365, "weight": 1.00, "hard": True},
    "硬性条件":   {"half_life_days": 180, "weight": 1.00, "hard": True},
    "预算上限":   {"half_life_days": 30,  "weight": 0.90, "hard": True},
    "预算区间":   {"half_life_days": 30,  "weight": 0.90, "hard": True},
    "成色要求":   {"half_life_days": 180, "weight": 0.80, "hard": True},
    "常驻城市":   {"half_life_days": 365, "weight": 0.80, "hard": False},
    "稳定身份":   {"half_life_days": 365, "weight": 0.70, "hard": False},
    "决策阶段":   {"half_life_days": 7,   "weight": 0.50, "hard": False},
    # 其余（类目偏好 / 类目排除 / 属性偏好 / 交易习惯）用默认值
}
_DEFAULT_META = {"half_life_days": None, "weight": 0.60, "hard": False}  # None ⇒ 用配置项
```

统一打分口径（`render_block` 的过滤与排序、以及淘汰 SQL 都用它）：

```
score = effective_confidence(confidence, last_seen, half_life=KEY_META 的 half_life)
        × KEY_META 的 weight
```

配套：
- `config.py:148-159` 的 `agent_memory_half_life_days` **降级为默认值**（key 未登记时使用），注释要改，否则会与 KEY_META 冲突。
- **U1 取代 P1-3 的公式**：P1-3 的"按 `effective` 淘汰"是"若不做 U1"时的最小改动；做了 U1 之后应改为按 `score`。
- spec §8.1 / §13 同步更新；`logic.py:163-165` 的渲染文案不变（计数照旧）。

#### 验收

- `决策阶段` 的行 `last_seen` 在 30 天前 → **不注入**（7 天半衰期衰减到阈值以下）。
- `明确拒绝` 的行 `last_seen` 在 1 年前 → **仍在注入**，且在同置信度下排在 `稳定身份` 之前。
- 淘汰行为：51 行超限时被淘汰的是 `score` 最低者（与注入排序同口径）。

#### 风险与不确定度

- 表里的 `half_life_days` 与 `weight` 是**产品判断，不是技术推导**，本文件给的是**推测性默认值**。建议先只给三个极端 key 配置（`决策阶段` / `明确拒绝` / `预算上限`），其余走默认——一上来就维护 13 行参数会成为负担，且没有数据支撑。
- `hard` 标记在本节只被 U2 / U3 消费；单独做 U1 不会有行为变化以外的好处，所以 U1 与 U2 / U3 应视为一组。

---

### 6.2 U2 `scope` 解析优先级：类目版覆盖全局版

#### 机会

你在 `uk_user_mem` 里放了 `scope`（`agent-memory-init.sql:38`），这是**现成的关联结构**——A-MEM 靠 link generation 建的东西，你用唯一键已经有了。但**读取侧没有利用它**。

#### 依据

1. spec §4.1（`:122-128`）明确"与具体类目绑定的偏好 `scope` 写 `category:<一级类目>`"，并举例"相机预算 3000"应写成 `预算上限@category:数码`。
2. 但**两行并存时没有优先级定义**：spec §8.3 的 SQL（`:378-384`）只按 `confidence DESC, last_seen DESC` 排序，`logic.py:139-153` 也只按衰减置信度排序 ⇒ `预算上限@global=50` 与 `预算上限@category:数码=3000` 会**同时进入注入块**，模型看到两条互相矛盾的价格约束。
3. 图结构约束（spec §9.1，`:394-404`）：`memory_recall` 在 `router` **之前**执行（`START → entry → memory_recall → rag_retrieval → router`）⇒ **召回时还不知道本轮属于哪个类目**，读取侧无法据此选行。
4. 相关但不等价：spec §8.2（`:370-374`）只定义了"同一唯一键内新值覆盖旧值"，**没有**跨 scope 的规则。

#### 改法（两层配合，不改图结构）

1. **提示词层**（`_ensure_persona`，`nodes.py:446`）：在注入块**头部**加一条解析规则——"同一 `mem_key` 的类目版优先于全局版；当前类目确定后以类目版为准"。成本一行文案。
2. **工具参数层（确定性）**：在类目已知的调用点做覆盖，见 **U3**——那里才是能真正消除歧义的位置。
3. **不建议**为此外改图结构（把 `memory_recall` 拆成两次：router 前注入 global、域子图内按类目补注入）。spec §9.1 已经论证过插入位置的取舍，为此付回归代价不划算。

#### 验收

同时存在 `预算上限@global=50` 与 `预算上限@category:数码=3000` 时：

- 问"推荐个数码相机" → 生效约束为 **3000**；
- 问"推荐个宠物用品" → 生效约束为 **50**。

> 这就是"用 `scope` 结构低成本实现 A-MEM 沿链扩展效果"的具体形态：关联维度不必新建，**只是读取时没打开**。此外它还顺带补上一个当前未定义的行为（两行同时注入 ⇒ 矛盾信息），所以 U2 兼具"升级"与"补漏"两重性质。

---

### 6.3 U3 硬约束落到工具参数：让成功标准 1 从"概率"变成"保证"

#### 机会 / 问题

spec §1.3 成功标准 1（`:23-28`）要求"商品卡价格全部 ≤50（或明确告知放宽原因）"。但当前机制是：

```
记忆 → 注入 system prompt → LLM 自行决定是否把 price_max=50 传给 search_products
```

这是**概率性遵守**，不是保证。spec §14 的用例表（`:578-605`）里**没有任何一条**能验证"卡价真的 ≤50"。

#### 依据

1. **反方向已有先例**：spec §6.4（`:254-267`）把 `search_products` 的参数当作**行为证据**读取（`price_max` / `price_min` / `condition` / `cat_l1` / `city`），说明这组参数名是**稳定接口**——把同一份白名单**反向**用于参数注入即可。
2. **不违反项目约束**：plan `:23-24` 要求"不直写业务表；Agent 工具保持只读"——参数注入只影响**查询条件**，不产生任何写入。
3. **代码位置现成**：工具调用点在 `nodes.py:652-800` 的 `run_agent_tool_loop` 内，与 Task 8 要改的 `_trace_tool`（plan `:631-694`）**同一片区域**，可一并实现，不必新开代码路径。
4. **异步带来的时序缺口**：提取是每轮结束后异步跑的（spec §6.1，`:164-175`）⇒ 本轮用户说"这次预算 800"时，库里的 `预算上限` 还是 50。所以**必须**有第 3 条例外机制，否则硬注入会当场违背用户意图。

#### 改法

1. **白名单 + 映射**（与 §6.4 的读取白名单共用一份定义，方向相反）：

   | 记忆 key | 工具参数 | 语义 |
   |---|---|---|
   | `预算上限` | `price_max` | 价格上限 |
   | `预算区间` | `price_min` / `price_max` | 区间 |
   | `成色要求` | `condition` | 成色下限 |
   | `类目排除` | 类目排除项 | 不展示 |
   | `硬性条件` | 关键字约束 | 必须带 / 必须满足 |

2. **只收紧、不放宽**：LLM 未传 → 用记忆值补；LLM 传了更宽松的 → 收紧到记忆值；LLM 传了更严格的 → 尊重 LLM。
3. **例外机制（必需，不是可选）**：**本轮用户消息含显式新价格表述时，以本轮为准**（不注入记忆值）。理由是上面第 4 条依据。
4. **可观测**：每次注入记一条结构化日志（`user_id / key / 注入值 / 是否覆盖了 LLM 参数`）。没有这条日志，第 6.5 节和"约束到底生效了没有"都无法回答。

#### 验收

在商品卡回传处加**断言式**用例：注入 `预算上限=50` 后，无论模型行为如何，返回卡片 `price <= 50`。

这条用例把 spec §1.3 标准 1 从"手工观察"提升为"自动化确定性断言"——这也是 P0-2 评测集里最该优先写的一条。

#### 风险

硬注入会让 Agent 显得"不听话"：用户明确要放宽却被收紧。第 3 条例外机制是它的唯一防线，**必须与映射逻辑同批实现**，不能分两次交付。

---

### 6.4 U4 只读诊断能力：provenance 的轻量落地

#### 机会

MemOS（`2507.03724` 摘要）把 provenance 与 versioning 作为基本单元 MemCube 的核心属性。而你的表**已经具备 provenance 的全部原料**，却没有任何工具能把它读出来：

- `source`（`dialog` / `behavior`）：`store.py:26-29`
- `evidence[{text, at, session}]`：`logic.py:205-231`
- `first_seen` / `last_seen` / `status`：`agent-memory-init.sql:34-36`
- `hit_count` / `session_count`：同表 `:28-29`

叠加两条已知约束——spec §12（`:538-551`）要求"对用户静默"（故障只留 warning）、spec §1.2（`:21`）要求"黑箱、无 UI、无删除入口"——结论是：**出问题时运营端完全无法自查**，只能靠 grep 日志。

#### 改法

加一个**只读**诊断脚本（放项目既有的 `ai-service/scripts/`），输入 `user_id [+ tenant_id]`，输出：

1. 该用户全部记忆行（含 `status`、`hit/session_count`、衰减后有效置信度、`evidence`）；
2. **未升格原因**：哪一条不满足 `hit>=3` 或 `session>=2`，还差多少；
3. 最近一次召回注入了什么（依赖 §6.3 第 4 条的日志）。

明确**不面向用户**（不加 UI、不加 Java 接口）⇒ 与 spec §1.2 的黑箱决定**不冲突**。

#### 验收

一条命令能回答："这个用户的 trait 为什么没生效？"

这也是排查 P0-1 类问题的必备工具——没有它，前端 session 语义这类跨层问题只能靠人肉推断。

---

### 6.5 U5 后台记忆巩固（合并抽象）— 默认不做

- **借鉴**：A-MEM 的 memory evolution（`2502.12110`：新记忆触发旧笔记的上下文 / 关键词更新），以及 LightRAG 的"增量更新 vs 离线社区摘要"张力（`2410.05779`）。
- **内容**：低频（例如每 7 天）把同一 `scope` 下多条相近 fact 合并为一条更抽象的 fact。
- **前置**：**必须先有 P0-2 的评测集**。没有它，无法判断合并是收益还是损伤。
- **硬约束**：合并只能"**追加新行 + 旧行 `status=0`**"，**绝不覆盖**。你的 trait 重算已经是这个安全形态，照抄即可——A-MEM 的教训正是"演化会改写历史笔记"。
- **结论**：**默认不做。** 你的 trait 层本身就是一种巩固形态；在评测集证明"抽象能提升召回"之前，这一层是纯风险。

---

### 6.6 U6 明确不采纳清单（写进来是为了防止范围蔓延）

| 方向 | 为什么不做 | 依据 |
|---|---|---|
| 向量检索（Mem0 式语义召回） | ≤50 行 + 封闭词表 ⇒ 向量收益极低，而成本（embedding 调用 + 索引维护）确定 | spec §2 YAGNI；第 2 节 |
| 图记忆 / PPR 多跳（Zep、HippoRAG 2 `2502.14802`） | 导购是单用户偏好查询，**无 multi-hop 需求** | spec §2；第 2 节 |
| 双时间轴全量版（Zep 的 `t_valid` / `t_invalid`） | 代价高；**P1-1 的 `old_value` 已拿到约 80% 价值** | P1-1 |
| Global / 主题归纳（GraphRAG `2404.16130`、LightRAG） | 你的"语料"是单个用户的 ≤50 条偏好，不存在"整个语料在讲什么"这类问题 | 架构前提 |
| 参数记忆（MemOS 把记忆训进权重） | 成本收益在本项目规模上不成立 | spec §2；第 2 节 |
| 用户可见的偏好管理页 / 删除入口 | 产品已明确黑箱（spec §1.2 / §2） | spec §2 |
| **但：非 UI 的删除能力** | 画像属个人信息，投诉 / 合规场景需要"能删"；成本极低（后台接口或 SQL） | **合规要求，非技术偏好** |

---

## 7. 附：证据采集命令

```powershell
# 基线测试
cd F:\code\weiwei\ai-service
C:\Users\lixin\AppData\Local\Python\pythoncore-3.14-64\python.exe -m pytest tests/test_agent_memory.py -q

# 已落地提交
cd F:\code\weiwei ; git log --oneline -6

# 落库形态抽查（Task 12 用）
# SELECT memory_type, mem_key, mem_value, scope, hit_count, session_count, source
# FROM ai_user_memory WHERE user_id = <uid>;

# 确认 ai_chat_history 不是导购会话表（P0-2 依据 2）
# 见 docs/db/twelvet-fast_2026-10-21.sql:65-85

# 确认前端 session_id 的持久化行为（P0-1 依据 3）
# ui/src/pages/marketplace/AiGuideFloat.tsx:49-56, 69, 87-93
# ui/src/pages/AI/ShoppingGuide/index.tsx:53-57
# ui/src/pages/AI/Butler/index.tsx:69-73
```