# RAG golden set

68 条平台规则问答用例，覆盖 7 类：`fact` / `paraphrase` / `numeric` / `multihop` / `version` / `unanswerable` / `injection`。

## 字段

| 字段 | 含义 |
|---|---|
| `id` | 稳定用例 ID |
| `query` | 用户问法 |
| `type` | 用例类型，用于分类型指标 |
| `expected_doc_ids` | 检索应命中的文档；multihop 需要 2+ 篇 |
| `expected_sections` | 期望引用的小节（人读辅助，暂不参与自动打分） |
| `answer_keywords` | 答案应包含的关键事实；用于关键词召回 |
| `answerable` | 标准答案是否存在 |
| `forbidden_keywords` | 注入/越权用例中不应出现的表述 |

## 运行

```bash
# 只做结构与覆盖率校验（不需要 embedding key）
py scripts/rag_showcase/run_eval.py

# 离线 baseline：TF-IDF+SVD 替代 embedding，不调用外部 API
py scripts/rag_showcase/run_eval.py --online --local-embedding --no-generate

# 在线 baseline：使用 bge-m3 + bge-reranker（需要余额）
py scripts/rag_showcase/run_eval.py --online
```

离线 fallback 只用于在无外部 embedding 余额时复现管线；报告中的 `embedding_mode`
会显式标注，不能与在线 bge-m3 数字混用。
