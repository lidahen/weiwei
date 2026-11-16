# RAG Showcase 完成度、未做与已知缺陷

> 截至 **2026-10-27**；在线 bge-m3 + reranker 已重跑，报告见 `eval-report.md`。
> 语料是**虚构的演示数据**（12 篇 / 57 个切片），指标只代表这套语料，不代表真实平台政策。

## 0. 一句话结论

已经交付一套**可离线复现、可评测、可降级、带引用**的平台规则 RAG 垂直切片，并接入了导购 Agent；
2026-10-27 补齐 SiliconFlow 欠费后，拿到了真实 bge-m3 + bge-reranker 的在线数字。
检索排序 **B2（向量+BM25）** 最好；**B3 加 rerank** 后用 R@1/nDCG 换到拒答 0.9706（unanswerable 0.875）。
最大剩余缺陷是 paraphrase 引用精确率 0.7486、unanswerable 仍有 1/8 误答，以及还没接 Java BFF / UI。

## 1. 已完成

### 1.1 语料与切片

| 项 | 状态 | 证据 |
|---|---|---|
| 12 篇平台规则语料，含 YAML front matter、版本、生效日期、上下架状态 | ✅ | `ai-service/data/rag_corpus/rules/*.md` |
| 版本冲突场景（退款审核 3 vs 5 个工作日），旧版标 `superseded` | ✅ | `returns-refund-v1.md` / `returns-refund-v2.md` |
| 不可回答面、提示注入面 | ✅ | 语料 + golden set |
| 结构感知切片：按 `##` / `###` 层级切，保留 `section_path` | ✅ | `app/services/rag_showcase/corpus.py` |
| 长小节按段落累积；单段过长按字符窗口切，40 字重叠 | ✅ | 同上，`max_chars=500` |
| 绝不跨小节合并；纯标题不单独成块 | ✅ | 57 个切片 |
| 切片 ID 稳定为 `doc_id#000` | ✅ | `corpus.py` |

### 1.2 索引

| 项 | 状态 | 证据 |
|---|---|---|
| FAISS `IndexFlatIP` + L2 normalize | ✅ | `app/services/rag_showcase/index.py` |
| 文件索引：`.faiss` / `.chunks.jsonl` / `.vectors.npy` / `.manifest.json` | ✅ | `ai-service/data/faiss_indexes/local/` |
| 语料哈希 + 逐块内容哈希：语料没变跳过重建 | ✅ | `test_build_reuses_vectors_for_unchanged_chunks` |
| 只重嵌变更块，未变块复用旧向量 | ✅ | 同上 |
| embedding 模型变化 / 索引格式变化时全量重嵌 | ✅ | `INDEX_VERSION`，3 个回归测试 |
| 陈旧检测覆盖语料、模型、索引格式 | ✅ | `is_stale()` + 测试 |
| 本地索引提交入库，可离线加载 | ✅ | 57 chunks，`local-lsa-zh-v1` |
| 在线 bge-m3 索引（57 chunks，可选提交） | ✅ | `ai-service/data/faiss_indexes/rulebook.*` |

### 1.3 检索

| 项 | 状态 | 证据 |
|---|---|---|
| 元数据过滤：`status=active`、`effective_date <= today` | ✅ | `retrieval.py` |
| 历史问法（2025 / 旧版 / 以前 / 历史 / 去年）放开 `superseded` | ✅ | `_HISTORICAL_KEYWORDS` |
| 向量召回：生产 bge-m3 / 离线 TF-IDF+SVD | ✅ | `embedding_service.py` / `local_embedding.py` |
| BM25：中文字符 unigram + bigram + trigram，不依赖 jieba | ✅ | `tokenizer.py` / `bm25.py` |
| RRF 融合，`k=60` | ✅ | `retrieval.py` |
| Cross-encoder rerank：SiliconFlow `BAAI/bge-reranker-v2-m3`，失败回退 RRF | ✅ 在线实测；B3 `degraded_rate=0` | `_rerank_documents` |
| 拒答判定：低 rerank 分，或低向量分且 BM25 无正分 | ✅ 代码路径；在线 unanswerable 0.875 | 见第 4 节 |
| 全链路 trace：vector / BM25 / RRF / rerank / 延迟 | ✅ | `/traces`、`TraceStore` |

### 1.4 生成

| 项 | 状态 | 证据 |
|---|---|---|
| System prompt 约束：只用编号资料、事实句标 `[n]`、资料不足要说明 | ✅ | `generate.py` |
| 多版本时优先生效版本；历史问法才引用旧版 | ✅ | prompt 规则 |
| 引用解析、非法引用剔除、重新编号 | ✅ | `_CITATION_RE` |
| 模型漏引用 / 调用失败 → 抽取式回答 | ✅ | `_extractive` |
| 无依据 → 固定拒答文案、不带引用 | ✅ | `_refusal` |
| 资料来源包裹，防提示注入 | ✅ | `sanitize_rag_context` |

### 1.5 服务与 API

| 项 | 状态 | 证据 |
|---|---|---|
| `POST /ai/rag-showcase/query` | ✅ | `app/routers/rag_showcase.py` |
| `POST /ai/rag-showcase/rebuild`（配置 token 时校验 `X-Internal-Token`） | ✅ | 同上 |
| `GET /ai/rag-showcase/corpus` | ✅ | 同上 |
| `GET /ai/rag-showcase/traces` | ✅ | 同上 |
| `GET /ai/rag-showcase/eval/latest` | ✅ | 同上 |
| 懒加载索引；缺失/首次加载陈旧时自动构建 | ✅ | `RagShowcaseService.ensure_ready` |
| 拒答路径不构造 LLM | ✅ | 本轮修复 + 回归测试 |
| 查询 trace 落 JSONL | ✅ | `TraceStore` |

### 1.6 Agent 集成

| 项 | 状态 | 证据 |
|---|---|---|
| L1 工具 `search_platform_rules` | ✅ | `app/services/agent/tools.py` |
| 加入 `PRE_SALE_TOOLS` / `AFTER_SALE_TOOLS` | ✅ | 同上 |
| 导购 prompt 第 13 条：规则问题优先调用，保留 `[n]` | ✅ | `nodes.py` |
| Skill 文档同步 | ✅ | `skills/guide-agent/SKILL.md` |
| 工具失败返回 `{error, answerable: false}`，不阻断主流程 | ✅ | `search_platform_rules` |
| Agent 端到端工具选择/答案质量评测 | ❌ 没做 | 只有工具单测 + Agent 回归 |

### 1.7 评测

| 项 | 状态 | 证据 |
|---|---|---|
| 68 条 golden set，7 类：fact / paraphrase / numeric / multihop / version / unanswerable / injection | ✅ | `tests/rag_eval/cases.jsonl` |
| 4 组 baseline B0–B3 | ✅ | `eval.py` |
| 指标：Recall@k、MRR、nDCG@5、拒答准确率、引用精确率/覆盖率、关键词召回、注入越权率、p50/p95 | ✅ | `docs/rag-showcase/eval-report.md` |
| 离线结构校验（不需要 embedding key） | ✅ | `run_eval.py` 默认模式 |
| 离线 LSA fallback，报告显式标注不是 bge-m3 | ✅ | `local_embedding.py` / `eval-report-offline-lsa.*` |
| 在线 bge-m3 + reranker 实测（B0–B3 + 全链路） | ✅ | `eval-report.md`，2026-10-27，B3 `degraded_rate=0` |
| 全链路 68/68 生成成功、0 条降级（4.5s 限速规避 429） | ✅ | `eval-report.json` 的 `generation` 降级计数 |
| LLM-as-judge / 答案忠实度 / 人工评测 | ❌ 没做 | 见第 3 节 |
| 真实语料、真实用户 query 分布 | ❌ 没做 | 当前全是合成数据 |

### 1.8 测试与文档

| 项 | 状态 | 证据 |
|---|---|---|
| RAG 专项测试 54 个 | ✅ | `pytest tests/test_rag_showcase_*.py` → 54 passed |
| 全量测试 643 passed / 1 skipped | ✅ | `pytest -q -W ignore` |
| 设计文档、实现计划、评测报告、面试讲述点 | ✅ | `docs/superpowers/specs/`、`docs/superpowers/plans/`、`docs/rag-showcase/` |
| README 复现命令 | ✅ | `docs/rag-showcase/README.md` |

## 2. 评测结果

### 2.1 检索 baseline（真实在线：bge-m3 + reranker）

> 2026-10-27 补齐 SiliconFlow 欠费后重跑；`embedding_mode=BAAI/bge-m3`，`rerank_mode=BAAI/bge-reranker-v2-m3`，B3 `degraded_rate=0`。

| 方案 | R@1 | R@3 | R@5 | MRR | nDCG@5 | 拒答准确率 | 降级率 | p50 | p95 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| B0 纯向量、含旧版 | 0.6864 | 0.9746 | 0.9915 | 0.8828 | 0.9044 | 0.8676 | 0 | 102.60 ms | 141.75 ms |
| B1 纯向量 + 版本过滤 | 0.7458 | 0.9831 | 1.0000 | 0.9223 | 0.9380 | 0.8676 | 0 | 106.86 ms | 122.28 ms |
| B2 向量 + BM25 | **0.7627** | **1.0000** | 1.0000 | **0.9379** | **0.9526** | 0.8676 | 0 | 114.99 ms | 130.58 ms |
| B3 向量 + BM25 + rerank | 0.7458 | 1.0000 | 1.0000 | 0.9153 | 0.9364 | **0.9706** | 0 | 368.04 ms | 419.98 ms |

**结论**：

- 版本过滤有效：B1 比 B0 的 R@1 高 5.9 个百分点。
- 真实 bge-m3 下 hybrid 赢了：B2 的 R@1/MRR/nDCG 全面高于 B1；离线 LSA 下 B2 < B1 的负结果保留在 `eval-report-offline-lsa.md`。
- rerank 是取舍：B3 的 R@1/nDCG 略低于 B2，但拒答准确率 0.9706 vs 0.8676，unanswerable 0.875 vs 0。

> 离线 LSA 旧数字（仅 fallback 对照）：B0 / B1 / B2 / B3 的 R@1 分别为 0.6356 / 0.7034 / 0.6864 / 0.6864；B3 `degraded_rate=1.0`。不与上表混用。

### 2.2 全链路（B3 + LLM 生成）

> 为规避 Agnes 免费额度 429，评测每次 LLM 调用前加 4.5 秒限速；68/68 生成成功、0 条降级。p50/p95 含等待，不代表线上生成延迟。

| 指标 | 值 | 说明 |
|---|---:|---|
| 拒答准确率 | 0.9706 | 68 条中 66 条判断正确 |
| 引用精确率 | 0.8678 | 引用文档命中预期文档的比例（文档级） |
| 引用覆盖率 | 0.9831 | 可回答用例中 59/60 给出了引用 |
| 答案关键词召回 | 0.9576 | 关键数字/责任方是否出现 |
| 注入越权率 | 0.0000 | 6 条带 forbidden 关键字，0 条越权 |
| p50 | 13812.04 ms | 含 4.5s 限速等待 + LLM 生成 |
| p95 | 60270.20 ms | 长答案/免费额度限速 |

### 2.3 分类型（全链路）

| 类型 | 条数 | 拒答准确率 | 引用精确率 | 关键词召回 |
|---|---:|---:|---:|---:|
| fact | 13 | 1.0000 | 0.8846 | 0.9615 |
| numeric | 12 | 1.0000 | 0.9792 | 1.0000 |
| multihop | 10 | 1.0000 | 0.9467 | 0.9500 |
| version | 8 | 1.0000 | 0.8125 | 1.0000 |
| paraphrase | 12 | 0.9167 | **0.7486** | **0.8750** |
| injection | 5 | 1.0000 | 0.7500 | 1.0000 |
| unanswerable | 8 | **0.8750** | — | — |

## 3. 明确没做的

以下不是“忘了”，而是本期范围外，按影响排序：

| 项 | 状态 | 影响 |
|---|---|---|
| Java BFF `/api/ai/rag-showcase/*` 转发与权限 | ❌ | 前端目前无法通过 BFF 使用；Python 端点只能本地调用 |
| UI 调试台（输入问题、看答案/引用/trace/评测） | ❌ | 演示只能走命令行或 curl |
| Agnes Token Plan / 稳定高并发 LLM | ❌ | 免费额度会 429；评测用 4.5s 限速，生产需付费额度/队列 |
| 真实平台规则语料 | ❌ | 当前 12 篇合成数据，不能证明真实分布效果 |
| LLM-as-judge / 答案忠实度 / 人工评测 | ❌ | 只有关键词和引用文档级指标，不能判断句子是否被引用支持 |
| Query rewrite / multi-query / HyDE | ❌ | 口语化改写仍是弱项 |
| Parent-child chunk / 语义切片 | ❌ | 当前固定“小节 + 窗口”，长条款可能被切碎 |
| Milvus / 生产向量库、元数据过滤规模化 | ❌ | 当前单进程文件索引，适合小语料 |
| 语料治理工作流（草稿→生效→归档） | ❌ | 版本靠 Git + front matter 手工维护 |
| 运行中语料热更新 | ❌ | 启动/首次加载才检测；运行中需 `POST /rebuild` |
| Python 端点鉴权、租户、配额 | ❌ | 默认只适合内网；暴露公网会绕过 Java 的鉴权/租户/配额 |
| 流式输出、端到端超时/重试/熔断 | ❌ | 全链路 p95 60s（含评测限速），生产体验不够 |
| 压测、成本/token 统计、A/B | ❌ | 没有并发与成本数据 |
| Agent 端到端评测（工具选择、多轮） | ❌ | 只验证了工具本身可用 |
| 离线模式接入 Agent 工具 | ❌ | Agent 工具走默认在线 embedding；本地离线索引未注入，API 不可用时工具会降级报错 |

## 4. 已知缺陷与风险

| 级别 | 缺陷 | 证据 | 影响 | 下一步 |
|---|---|---|---|---|
| 高 | unanswerable 仍有 1/8 误答 | 分类型 0.8750 | OOD 问题仍可能被当成可回答 | reranker 阈值校准 / OOD 检测 / LLM 拒答兜底 |
| 高 | 没有 Java BFF / UI | `backend/`、`ui/` 无 rag-showcase 引用 | 端到端产品链路未闭合 | Phase 2 接 BFF + 调试台 |
| 中 | B3 rerank 用 R@1/nDCG 换拒答 | B3 R@1 0.7458 < B2 0.7627；拒答 0.9706 > 0.8676 | 排序指标不是最高，但规则问答更看重不乱答 | 明确取舍；若 top-1 优先选 B2 |
| 中 | 口语化 paraphrase 弱 | 引用 0.7486 / 关键词 0.8750 / 拒答 0.9167 | 用户换个说法就可能引到错误文档 | query rewrite、同义词/领域词典、句子级校验 |
| 中 | 全链路 LLM 429/延迟 | 免费额度 429；4.5s 限速后 p50 13.8s / p95 60.3s | 生产体验和成本不稳 | 付费额度、请求队列、超时/重试/流式 |
| 中 | 引用只校验到文档级 | `citation_precision` 按 doc_id 命中 | 引用对了文档、但句子未必被该条款支持 | 句子级 NLI / LLM judge |
| 中 | 语料和评测规模小 | 12 篇 / 57 块 / 68 条 | 指标统计意义有限，不能外推 | 扩语料、真实 query、分层评测 |
| 中 | 运行中语料变更不自动生效 | `ensure_ready` 只在首次加载检查 | 改了语料不 `/rebuild` 会继续答旧规则 | 文件监听/定时检查 + 版本化索引 |
| 中 | Python 端点默认无鉴权 | `INTERNAL_API_TOKEN` 默认空 | 一旦暴露公网，可绕过 BFF 鉴权/租户/配额 | 强制 token 或只监听 127.0.0.1 |
| 中 | Agent 工具未接离线索引 | 工具调用 `get_service()` 默认在线配置 | embedding 不可用时 Agent 的规则工具直接报错 | 增加 local 模式配置并注入 embedder/index |
| 低 | BM25 每次查询重建 | `retrieve()` 内 `BM25(...)` | 57 块约 2.6ms；语料变大后线性增长 | 缓存 BM25 结构/倒排 |
| 低 | trace JSONL 无轮转/脱敏 | `TraceStore` 只追加 | 长期运行文件膨胀，query 原文落盘 | 采样、脱敏、按大小轮转 |
| 低 | `target_chars` 未实际使用 | `chunk_doc` 接口保留但 `_split_section` 不使用 | 块大小靠 `max_chars` 控制，参数有误导性 | 要么实现 ~350 字聚块，要么删参数 |
| 低 | 历史意图靠关键词 | `_HISTORICAL_KEYWORDS` | “2025 年新规”这类问法可能误放开旧版 | 意图分类/显式版本参数 |
| 低 | embedding 无显式超时/端到端 deadline | `embedding_service.py` 未设 timeout | API 挂起时请求可能长时间占用 | 加超时、重试、熔断 |
| 低 | 引用可能重复同一文档 | 生成解析按 `[n]` 保留 | 同一文档多块引用会拉低文档级精确率 | 按文档去重或分句归并 |
| 低 | 离线 baseline 评测默认覆盖全链路报告 | `run_eval.py` 无条件写 `eval-report.json/md` | 跑 `--no-generate` 会覆盖已提交的 full 指标 | 分开文件名或加 `--no-write-report`；临时跑请加 `--output-dir` |

## 5. 关键设计判定

1. **先离线兜底，后补在线实测**：账户欠费时免费 bge-m3 也会 402；补缴后用真实 bge-m3 + reranker 重跑，报告 `embedding_mode=BAAI/bge-m3`。离线 LSA 保留为 fallback 对照（`eval-report-offline-lsa.*`），不冒充在线结论。
2. **nDCG 用标准 `log2(rank+1)`**：修正计划里“相关文档在 rank 1/3 就得 1.0”的错误期望，保留理想排序=1.0 的断言。
3. **版本冲突在检索前硬过滤**：`status/effective_date` 是元数据，不交给 LLM 自己选版本；只有用户明确问历史才放开 `superseded`。
4. **拒答不依赖 LLM**：检索判定不可回答时直接返回拒答文案；LLM 配置故障不影响拒答。
5. **demo 默认不放 unanswerable**：避免把不可回答样例当默认演示；报告里仍有 1/8 失败，照实记录。
6. **运行中语料变更用显式 `/rebuild`**：不逐查询重读并哈希语料，避免把检索延迟抬高；README 已按实际行为写明。
7. **注入判定排除否定语境**：`不是无限期`、`非由买家承担` 不算越权，避免把正确拒答误判为注入成功。
8. **注入判定关键词要具体到注入结论**：q064 原 forbid「由买家承担」会把正确答案里的「非质量问题…由买家承担」误判；收紧为「质量问题退货运费由买家承担」后 0/6。评测规则本身也要可审计。

## 6. 复现与验收

```bash
cd ai-service

# 1) RAG 专项测试：54 passed
py -m pytest tests/test_rag_showcase_*.py -q

# 2) 全量测试：643 passed, 1 skipped
py -m pytest -q -W ignore

# 3) 完全离线 demo：现行 v2 = 5 个工作日；2025 旧版 = 3 个工作日
py scripts/rag_showcase/demo.py --local-embedding --extractive --debug

# 4) 离线 baseline（不调用 LLM；注意加 --output-dir，避免覆盖已提交的 full 报告）
py scripts/rag_showcase/run_eval.py --online --local-embedding --no-generate --output-dir "$TEMP/rag-eval-baseline"

# 5) 在线索引 + 全链路（真实 bge-m3 + reranker；若 Agnes 429 加 --llm-delay 4.5）
py scripts/rag_showcase/build_index.py --force
py scripts/rag_showcase/run_eval.py --online --llm-delay 4.5

# 6) 只重建本地索引
py scripts/rag_showcase/build_index.py --local-embedding --force
```

**验收口径**：

- `pytest tests/test_rag_showcase_*.py -q` 必须是 54 passed；
- demo 的两个版本数字必须分别是 5 / 3 个工作日；
- `docs/rag-showcase/eval-report.json` 的 `embedding_mode` 必须是 `BAAI/bge-m3`，`rerank_mode` 必须是 `BAAI/bge-reranker-v2-m3`，B3 `degraded_rate=0`；
- 离线 LSA 对照报告是 `eval-report-offline-lsa.json`，其 `embedding_mode` 必须以 `local-lsa-zh-v1` 开头；
- 在线全链路必须报告 `generation_degraded=0/68`（若有 429 降级，不算纯 LLM 指标）。

## 7. 下一阶段优先级

**P0：把结论补成“线上可信”**

1. 保持 SiliconFlow 账户可用（余额/实名/欠费），把在线评测固化成可重复任务；
2. 解决 unanswerable 拒答：reranker 分数阈值、OOD 检测、LLM 拒答兜底；
3. 接 Java BFF `/api/ai/rag-showcase/*` + 最小 UI 调试台；
4. Python 端点强制 token / 只监听内网。

**P1：把检索质量做深**

1. query rewrite / 同义词/领域词典，补 paraphrase；
2. parent-child chunk / 语义切片；
3. 句子级引用校验（NLI 或 LLM-as-judge）；
4. 真实语料与真实 query 分布。

**P2：生产化**

1. Milvus/元数据过滤规模化；
2. 热更新、索引版本切换、灰度；
3. 超时/重试/熔断、流式输出、压测；
4. trace 采样/脱敏/轮转、成本与 token 统计。

## 8. 证据索引

| 内容 | 路径 |
|---|---|
| 设计与取舍 | `docs/superpowers/specs/2026-10-26-rag-showcase-design.md` |
| 实现计划 | `docs/superpowers/plans/2026-10-26-rag-showcase.md` |
| 使用说明 / 面试讲述点 | `docs/rag-showcase/README.md` |
| 在线评测报告 | `docs/rag-showcase/eval-report.md`、`.json` |
| 离线 LSA 对照报告 | `docs/rag-showcase/eval-report-offline-lsa.md`、`.json` |
| 评测 CLI | `ai-service/scripts/rag_showcase/run_eval.py` |
| Golden set | `ai-service/tests/rag_eval/cases.jsonl` |
| 语料 | `ai-service/data/rag_corpus/rules/*.md` |
| 在线索引 | `ai-service/data/faiss_indexes/rulebook.*` |
| 本地离线索引 | `ai-service/data/faiss_indexes/local/` |
| 核心代码 | `ai-service/app/services/rag_showcase/` |
| API | `ai-service/app/routers/rag_showcase.py` |
| Agent 工具 | `ai-service/app/services/agent/tools.py` |
| 测试 | `ai-service/tests/test_rag_showcase_*.py` |
