# 平台规则 RAG 亮点工程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `ai-service` 内落地一套可评测、可解释、可降级的平台规则 RAG：文件化政策语料 → 结构感知切片 → BM25+向量混合召回 → rerank → 引用/拒答 → golden set 评测 → 接入导购 Agent。

**Architecture:** 语料以 Markdown + YAML front matter 存在 Git 中，索引是派生产物（FAISS + chunks/vectors/manifest 文件）。检索层先做版本/生效日期过滤，再并行 BM25 与向量召回，用 RRF 融合、cross-encoder 重排，最后生成带 `[n]` 引用的答案；无依据时拒答。评测脚本对比 4 组 baseline，产出可复现报告。

**Tech Stack:** Python 3.11+（本机 `py` 为 3.14，依赖已装）、FastAPI、SQLAlchemy（仅现有代码）、FAISS、NumPy、PyYAML、bge-m3 embedding、bge-reranker-v2-m3、pytest。

**Spec:** `docs/superpowers/specs/2026-10-26-rag-showcase-design.md`

## Global Constraints

- 不新增第三方依赖：tokenizer/BM25/RRF 自己实现；YAML 用已安装的 PyYAML。
- 不修改现有 `ai_doc` / `ai_doc_slice` 表结构；本工程索引是文件派生产物。
- 平台规则语料是**虚构演示数据**，README 必须标注，不得宣称真实生产规则。
- 所有单元测试不依赖网络/DB/Redis；embedding、rerank、LLM 全部可注入 fake。
- 在线评测失败时报告 degraded，不伪造数字。
- 现有通用知识库 RAG 保持不动；新增能力走 `search_platform_rules` 与 `/ai/rag-showcase/*`。
- 前端架构不变量：若后续加 UI，必须经 Java BFF，不直连 Python。
- 代码风格跟随 `ai-service`：模块 docstring、类型标注、中文注释解释「为什么」。
- 提交信息用 `feat(rag)` / `test(rag)` / `docs(rag)` 前缀。

## Review Focus

- **不可回答问题**：语料没有海外购关税/客服电话。期望：`answerable=false` + 明确拒答，绝不编造。测试放在 Task 4/5。
- **历史版本问题**：「2025 年退款审核要几天？」期望：允许召回 superseded，答案标注 v1 与生效日期。测试放在 Task 4/5。
- **外部依赖故障**：embedding 或 rerank API 失败。期望：BM25-only / RRF 原序降级，不抛 500。测试放在 Task 4。
- **索引缺失/陈旧**：期望：明确 503 或自动重建，不静默返回空答案。测试放在 Task 3/7。
- **Prompt 注入**：语料或查询含「忽略以上规则」。期望：注入内容被标记为不可信，不覆盖系统指令。测试放在 Task 5。

---

### Task 1: 语料解析与结构感知切片

**Files:**
- Create: `ai-service/app/services/rag_showcase/__init__.py`
- Create: `ai-service/app/services/rag_showcase/corpus.py`
- Test: `ai-service/tests/test_rag_showcase_corpus.py`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `DocMeta(doc_id: str, title: str, category: str, version: str, effective_date: date, status: str, supersedes: str | None, tags: tuple[str, ...])`
  - `Chunk(chunk_id: str, doc_id: str, title: str, category: str, version: str, effective_date: date, status: str, section_path: tuple[str, ...], content: str, char_count: int)`
  - `CorpusSnapshot(chunks: tuple[Chunk, ...], corpus_hash: str, doc_hashes: dict[str, str])`
  - `parse_doc(path: Path) -> tuple[DocMeta, str]`
  - `chunk_doc(meta: DocMeta, body: str, *, max_chars: int = 500, target_chars: int = 350, overlap: int = 40) -> list[Chunk]`
  - `load_corpus(corpus_dir: Path) -> CorpusSnapshot`

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_corpus.py
from datetime import date
from pathlib import Path

import pytest

from app.services.rag_showcase.corpus import chunk_doc, load_corpus, parse_doc


def _write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def test_parse_front_matter(tmp_path: Path):
    p = tmp_path / "a.md"
    _write(p, """---
doc_id: returns
title: 退换货政策
category: after_sales
version: v2
effective_date: 2026-01-01
status: active
tags: [退货, 退款]
---

## 一、总则

正文。
""")
    meta, body = parse_doc(p)
    assert meta.doc_id == "returns"
    assert meta.effective_date == date(2026, 1, 1)
    assert meta.tags == ("退货", "退款")
    assert body.lstrip().startswith("## 一、总则")


def test_chunk_keeps_section_path_and_heading(tmp_path: Path):
    p = tmp_path / "a.md"
    _write(p, """---
doc_id: returns
title: 退换货政策
category: after_sales
version: v2
effective_date: 2026-01-01
status: active
---

## 三、退款时效

审核通过后 5 个工作日。

### 3.2 原路退回

退至原支付渠道。
""")
    meta, body = parse_doc(p)
    chunks = chunk_doc(meta, body)
    assert [c.section_path for c in chunks] == [
        ("三、退款时效",),
        ("三、退款时效", "3.2 原路退回"),
    ]
    assert all(c.content.startswith("#") for c in chunks)
    assert all(c.doc_id == "returns" and c.version == "v2" for c in chunks)


def test_long_section_splits_without_crossing_sections(tmp_path: Path):
    p = tmp_path / "a.md"
    long_body = "\n\n".join(f"第{i}段。" * 40 for i in range(5))
    _write(p, f"""---
doc_id: long
title: 长文档
category: test
version: v1
effective_date: 2026-01-01
status: active
---

## 第一节

{long_body}

## 第二节

短内容。
""")
    meta, body = parse_doc(p)
    chunks = chunk_doc(meta, body, max_chars=200, target_chars=120, overlap=20)
    first = [c for c in chunks if c.section_path == ("第一节",)]
    second = [c for c in chunks if c.section_path == ("第二节",)]
    assert len(first) > 1
    assert len(second) == 1
    assert all(c.char_count <= 200 for c in chunks)


def test_load_corpus_hash_changes_with_content(tmp_path: Path):
    _write(tmp_path / "a.md", """---
doc_id: a
title: A
category: test
version: v1
effective_date: 2026-01-01
status: active
---

## 一

内容一。
""")
    first = load_corpus(tmp_path)
    _write(tmp_path / "a.md", """---
doc_id: a
title: A
category: test
version: v2
effective_date: 2026-02-01
status: active
---

## 一

内容二。
""")
    second = load_corpus(tmp_path)
    assert first.corpus_hash != second.corpus_hash
    assert second.chunks[0].version == "v2"


def test_parse_rejects_missing_fields(tmp_path: Path):
    p = tmp_path / "bad.md"
    _write(p, "---\ntitle: A\n---\n正文")
    with pytest.raises(ValueError, match="doc_id"):
        parse_doc(p)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_corpus.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase`.

- [ ] **Step 3: Implement `corpus.py`**

Implementation notes:
- `parse_doc` reads UTF-8; front matter is between the first two `---` lines; `yaml.safe_load`.
- Required front matter fields: `doc_id,title,category,version,effective_date,status`; `status` must be `active|superseded`; `effective_date` parse `date.fromisoformat`.
- `tags` default `()`; `supersedes` default `None`.
- Chunking: split body by heading lines (`^(#{1,3})\s+(.+)$`). Track heading path: `#` resets, `##` at level 2, `###` at level 3. The heading line is included in `content`.
- A section body is text after its heading until the next heading. If section text (heading + body) ≤ `max_chars`, one chunk. Else split paragraphs (`\n\n+`) into pieces of ~`target_chars`, char-level overlap `overlap` when a single paragraph is too long. Never merge across sections.
- `chunk_id = f"{doc_id}#{index:03d}"` in document order.
- `char_count = len(content)`.
- `load_corpus` sorts `*.md` by filename; computes `doc_hashes[doc_id] = sha256(file_bytes)`; `corpus_hash = sha256(json.dumps(sorted(doc_hashes.items())))`.
- Empty directory → `CorpusSnapshot((), hash, {})`; index build will reject empty.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_corpus.py -v`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/__init__.py ai-service/app/services/rag_showcase/corpus.py ai-service/tests/test_rag_showcase_corpus.py
git commit -m "feat(rag): 政策语料解析与结构感知切片"
```

---

### Task 2: 中文 tokenizer 与 BM25

**Files:**
- Create: `ai-service/app/services/rag_showcase/tokenizer.py`
- Create: `ai-service/app/services/rag_showcase/bm25.py`
- Test: `ai-service/tests/test_rag_showcase_bm25.py`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `tokenize(text: str) -> list[str]`
  - `class BM25: __init__(documents: Sequence[str], *, k1: float = 1.5, b: float = 0.75); scores(query: str) -> list[float]; top_n(query: str, n: int) -> list[tuple[int, float]]`

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_bm25.py
from app.services.rag_showcase.bm25 import BM25
from app.services.rag_showcase.tokenizer import tokenize


def test_tokenize_chinese_bigrams_and_numbers():
    tokens = tokenize("退款审核 5 个工作日，48小时到账")
    assert "退款" in tokens and "审核" in tokens
    assert "5" in tokens and "48" in tokens
    assert "工作日" in tokens


def test_bm25_exact_number_ranks_first():
    docs = [
        "退款审核需要 5 个工作日。",
        "退款审核需要 3 个工作日。",
        "退货期限为 7 天。",
    ]
    bm25 = BM25(docs)
    top = bm25.top_n("退款审核 3 个工作日", 3)
    assert top[0][0] == 1
    assert top[0][1] > 0


def test_bm25_no_match_returns_empty():
    bm25 = BM25(["退货运费由卖家承担"])
    assert bm25.top_n("海外购关税", 3) == []


def test_bm25_empty_corpus():
    bm25 = BM25([])
    assert bm25.top_n("任何问题", 3) == []
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_bm25.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase.bm25`.

- [ ] **Step 3: Implement `tokenizer.py` and `bm25.py`**

`tokenize`:
- Lowercase.
- Regex `r"[一-鿿]+|[a-z0-9]+(?:[.%][0-9]+)?%?"`.
- For CJK run: append each character and each overlapping bigram (if len ≥ 2).
- For alnum: append the token as-is.
- Do not dedupe (term frequency matters).

`BM25`:
- Precompute token lists, document frequencies, avgdl.
- `scores(query)`: for each token in query tokens, `idf = log((N - df + 0.5)/(df + 0.5) + 1)`; tf component `tf*(k1+1)/(tf + k1*(1-b+b*dl/avgdl))`.
- `top_n`: score all docs, filter `score > 0`, sort by `(-score, index)`, return first n.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_bm25.py -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/tokenizer.py ai-service/app/services/rag_showcase/bm25.py ai-service/tests/test_rag_showcase_bm25.py
git commit -m "feat(rag): 中文 bigram tokenizer 与 BM25"
```

---

### Task 3: 文件索引构建、缓存与陈旧检测

**Files:**
- Create: `ai-service/app/services/rag_showcase/index.py`
- Test: `ai-service/tests/test_rag_showcase_index.py`

**Interfaces:**
- Consumes: `CorpusSnapshot`, `Chunk`, `load_corpus`, `embed_texts` (default), `faiss`, `numpy`.
- Produces:
  - `class RulebookIndex(corpus_dir: Path, index_dir: Path, *, embedder: Callable[[list[str]], Awaitable[list[list[float]]]] | None = None)`
  - `async def build(self, *, force: bool = False) -> dict` — returns manifest.
  - `async def load(self) -> bool` — loads files into memory; returns False if missing.
  - `def is_stale(self) -> bool` — compares current corpus hash to manifest.
  - `@property chunks: list[Chunk]`
  - `def search_vector(self, query_vector: np.ndarray, k: int) -> list[tuple[int, float]]`
  - `@property manifest: dict`

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_index.py
import asyncio
import math
from pathlib import Path

import numpy as np

from app.services.rag_showcase.index import RulebookIndex


def _doc(doc_id: str, content: str) -> str:
    return f"""---
doc_id: {doc_id}
title: {doc_id}
category: test
version: v1
effective_date: 2026-01-01
status: active
---

## 一

{content}
"""


async def _fake_embed(texts: list[str]) -> list[list[float]]:
    out = []
    for t in texts:
        v = np.zeros(8, dtype="float32")
        for i, ch in enumerate(t):
            v[i % 8] += ord(ch) % 17
        norm = np.linalg.norm(v) or 1.0
        out.append((v / norm).tolist())
    return out


def test_build_load_and_search(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "退款审核 5 个工作日"), encoding="utf-8")
    (corpus / "b.md").write_text(_doc("b", "禁售电子烟"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)

    manifest = asyncio.run(index.build(force=True))
    assert manifest["chunk_count"] == 2
    assert (tmp_path / "index" / "rulebook.faiss").exists()

    loaded = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    assert asyncio.run(loaded.load()) is True
    assert [c.doc_id for c in loaded.chunks] == ["a", "b"]
    assert loaded.is_stale() is False

    q = np.zeros(8, dtype="float32")
    q[0] = 1.0
    hits = loaded.search_vector(q, 2)
    assert len(hits) == 2
    assert all(isinstance(i, int) for i, _ in hits)


def test_build_reuses_vectors_for_unchanged_chunks(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("a", "第一版内容"), encoding="utf-8")
    calls = []

    async def counting_embed(texts):
        calls.append(list(texts))
        return await _fake_embed(texts)

    index = RulebookIndex(corpus, tmp_path / "index", embedder=counting_embed)
    asyncio.run(index.build(force=True))
    assert len(calls) == 1

    (corpus / "b.md").write_text(_doc("b", "新增文档"), encoding="utf-8")
    asyncio.run(index.build(force=False))
    assert len(calls) == 2
    assert calls[1] == ["## 一\n\n新增文档"]  # 只重嵌新增块

    asyncio.run(index.build(force=False))
    assert len(calls) == 2  # 无变化不重嵌


def test_build_rejects_empty_corpus(tmp_path: Path):
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    try:
        asyncio.run(index.build(force=True))
    except ValueError as e:
        assert "empty" in str(e).lower()
    else:
        raise AssertionError("expected ValueError")


def test_missing_index_load_returns_false(tmp_path: Path):
    index = RulebookIndex(tmp_path / "corpus", tmp_path / "index", embedder=_fake_embed)
    assert asyncio.run(index.load()) is False
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_index.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase.index`.

- [ ] **Step 3: Implement `index.py`**

Implementation notes:
- File names: `rulebook.faiss`, `rulebook.chunks.jsonl`, `rulebook.vectors.npy`, `rulebook.manifest.json` under `index_dir`.
- Manifest: `{"corpus_hash": str, "built_at": ISO8601, "embedding_model": settings.embedding_model, "chunk_count": int, "doc_hashes": dict, "chunk_hashes": dict}`.
- Chunk JSONL: one JSON object per chunk with `effective_date` as ISO string; reconstruct `Chunk` with `date.fromisoformat`.
- Vector cache: `chunk_hash = sha256(chunk.content.encode("utf-8")).hexdigest()`; load existing chunks+vectors; map hash→vector; reuse for matching hashes; embed only new hashes in one batch.
- Build: if `not force` and manifest exists and `corpus_hash` matches and all files exist → return manifest without embedding.
- FAISS: `IndexFlatIP(dim)`, `normalize_L2` on a copy of the matrix.
- Empty chunks → `raise ValueError("corpus is empty")`; remove stale index files.
- `load()` reads chunks, vectors, manifest, builds FAISS in memory from vectors (or reads .faiss). Keep vectors for cache.
- `is_stale()`: load corpus snapshot and compare hash; if no manifest → True.
- `search_vector`: normalize query, `index.search`, return `[(int(i), float(score))]` for valid indices.
- Use `asyncio.to_thread` for file/FAISS sync work? The index build is called at startup/rebuild; embedding is async. For simplicity, sync file I/O in async method is acceptable for small files; note in docstring. Tests don't care. For production, wrap heavy parts in `to_thread` if needed. We can keep simple.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_index.py -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/index.py ai-service/tests/test_rag_showcase_index.py
git commit -m "feat(rag): 文件索引构建、向量缓存与陈旧检测"
```

---

### Task 4: 检索管线（版本过滤 + 混合召回 + RRF + rerank + 拒答）

**Files:**
- Create: `ai-service/app/services/rag_showcase/retrieval.py`
- Test: `ai-service/tests/test_rag_showcase_retrieval.py`

**Interfaces:**
- Consumes: `RulebookIndex`, `Chunk`, `BM25`, `embed_query` (default), `settings`.
- Produces:
  - `RetrievalConfig(top_k: int = 5, recall_k: int = 20, rrf_k: int = 60, use_bm25: bool = True, use_rerank: bool = True, include_superseded: bool = False, min_rerank_score: float = 0.05, min_vector_score: float = 0.30)`
  - `Candidate(chunk: Chunk, vector_score: float | None, bm25_score: float | None, rrf_score: float | None, rerank_score: float | None)`
  - `RetrievalResult(candidates: list[Candidate], answerable: bool, refusal_reason: str | None, degraded: list[str], trace: dict)`
  - `async def retrieve(index: RulebookIndex, query: str, *, config: RetrievalConfig, embedder: Callable[[str], Awaitable[list[float]]] | None = None, reranker: Callable[[str, list[str]], Awaitable[list[float] | None]] | None = None, today: date | None = None) -> RetrievalResult`

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_retrieval.py
import asyncio
from datetime import date
from pathlib import Path

import numpy as np

from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.retrieval import RetrievalConfig, retrieve


def _doc(doc_id: str, status: str, content: str, effective: str = "2026-01-01") -> str:
    return f"""---
doc_id: {doc_id}
title: {doc_id}
category: test
version: v1
effective_date: {effective}
status: {status}
---

## 一

{content}
"""


async def _fake_embed(texts: list[str]) -> list[list[float]]:
    out = []
    for t in texts:
        v = np.zeros(16, dtype="float32")
        for i, ch in enumerate(t):
            v[ord(ch) % 16] += 1
        out.append((v / (np.linalg.norm(v) or 1)).tolist())
    return out


async def _fake_query_embed(text: str) -> list[float]:
    return (await _fake_embed([text]))[0]


async def _fake_rerank(query: str, docs: list[str]) -> list[float]:
    return [1.0 if "退款" in d else 0.0 for d in docs]


def _build(tmp_path: Path) -> RulebookIndex:
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "v1.md").write_text(_doc("returns-v1", "superseded", "退款审核 3 个工作日"), encoding="utf-8")
    (corpus / "v2.md").write_text(_doc("returns-v2", "active", "退款审核 5 个工作日"), encoding="utf-8")
    (corpus / "other.md").write_text(_doc("other", "active", "禁售电子烟"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    asyncio.run(index.build(force=True))
    asyncio.run(index.load())
    return index


def test_version_filter_excludes_superseded(tmp_path: Path):
    index = _build(tmp_path)
    result = asyncio.run(retrieve(index, "退款审核几天", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=_fake_rerank, today=date(2026, 10, 26)))
    assert result.answerable is True
    assert all(c.chunk.status == "active" for c in result.candidates)


def test_historical_query_includes_superseded(tmp_path: Path):
    index = _build(tmp_path)
    result = asyncio.run(retrieve(index, "2025 年退款审核几天", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=_fake_rerank, today=date(2026, 10, 26)))
    docs = {c.chunk.doc_id for c in result.candidates}
    assert "returns-v1" in docs


def test_unanswerable_is_refused(tmp_path: Path):
    index = _build(tmp_path)

    async def low_rerank(query, docs):
        return [0.0 for _ in docs]

    result = asyncio.run(retrieve(index, "海外购关税怎么算", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=low_rerank, today=date(2026, 10, 26)))
    assert result.answerable is False
    assert result.refusal_reason


def test_embedding_failure_degrades_to_bm25(tmp_path: Path):
    index = _build(tmp_path)

    async def broken_embed(text):
        raise RuntimeError("embedding down")

    result = asyncio.run(retrieve(index, "退款审核 5 个工作日", config=RetrievalConfig(), embedder=broken_embed, reranker=_fake_rerank, today=date(2026, 10, 26)))
    assert "embedding_unavailable" in result.degraded
    assert result.candidates


def test_rerank_failure_keeps_rrf_order(tmp_path: Path):
    index = _build(tmp_path)

    async def broken_rerank(query, docs):
        return None

    result = asyncio.run(retrieve(index, "退款审核", config=RetrievalConfig(), embedder=_fake_query_embed, reranker=broken_rerank, today=date(2026, 10, 26)))
    assert "rerank_unavailable" in result.degraded
    assert result.candidates
    assert all(c.rerank_score is None for c in result.candidates)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_retrieval.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase.retrieval`.

- [ ] **Step 3: Implement `retrieval.py`**

Implementation notes:
- `_include_superseded(query, override)`: if override is not None return it; else true if query contains any of `("以前", "旧版", "历史", "去年", "2025", "2024")`.
- Filter: `status == "active"` and `effective_date <= today` unless include_superseded. Note: a superseded doc may have effective_date <= today; keep it only when historical query.
- Vector path: `qvec = await embedder(query)`; on exception append `"embedding_unavailable"` and skip. `index.search_vector(np.array(qvec), min(len(chunks), recall_k*3))`; filter to allowed indices; take top `recall_k` by score.
- BM25 path: build BM25 over allowed chunk contents (small corpus; rebuild per query is O(N), fine; for scale, cache by filter key). Take top `recall_k`.
- RRF: map index→rank; `score += 1/(rrf_k+rank)`; if one list empty, the other still contributes.
- Rerank: take fused top `max(top_k, settings.rerank_top_n)`; call `reranker(query, [content...])`. If None → degraded, keep RRF order. Else assign scores and sort desc.
- Refusal: if no candidates → false reason `"no_candidates"`. If rerank available and top.rerank_score < `min_rerank_score` → false reason `"low_rerank_score"`. If rerank unavailable and top vector score is not None and top.vector_score < `min_vector_score` and top bm25 score <= 0 → false reason `"low_vector_score"`.
- Trace: `{"query": query, "include_superseded": bool, "filters": {...}, "vector_hits": [...], "bm25_hits": [...], "fused": [...], "reranked": [...], "degraded": [...], "latency_ms": {...}}`. Each hit is `{"chunk_id", "doc_id", "score"}`. Keep top 10.
- Timing with `time.perf_counter()`.
- Default embedder: `embedding_service.embed_query`; default reranker: `_rerank_documents` calling SiliconFlow `/rerank` (reuse settings; return None on any exception).
- `Candidate` should include `vector_score`, `bm25_score`, `rrf_score`, `rerank_score`; final candidates sorted by rerank if available else rrf, truncated to `top_k`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_retrieval.py -v`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/retrieval.py ai-service/tests/test_rag_showcase_retrieval.py
git commit -m "feat(rag): 版本过滤、BM25+向量混合召回、RRF 与 rerank 降级"
```

---

### Task 5: 生成、引用与抽取式降级

**Files:**
- Create: `ai-service/app/services/rag_showcase/generate.py`
- Test: `ai-service/tests/test_rag_showcase_generate.py`

**Interfaces:**
- Consumes: `RetrievalResult`, `Candidate`, `sanitize_rag_context` (from `app.services.agent.nodes`), `_get_llm` (from `app.services.agent.llm`).
- Produces:
  - `Citation(n: int, chunk_id: str, doc_id: str, title: str, version: str, effective_date: str, section: str, score: float)`
  - `RagAnswer(answer: str, answerable: bool, citations: list[Citation], refusal_reason: str | None, degraded: str | None)`
  - `async def generate_answer(query: str, result: RetrievalResult, *, llm=None) -> RagAnswer`

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_generate.py
import asyncio
from datetime import date

from app.services.rag_showcase.corpus import Chunk
from app.services.rag_showcase.generate import generate_answer
from app.services.rag_showcase.retrieval import Candidate, RetrievalResult


def _candidate(doc_id: str, content: str, rerank: float = 0.9) -> Candidate:
    chunk = Chunk(
        chunk_id=f"{doc_id}#000", doc_id=doc_id, title=doc_id, category="test",
        version="v2", effective_date=date(2026, 1, 1), status="active",
        section_path=("一",), content=content, char_count=len(content),
    )
    return Candidate(chunk=chunk, vector_score=0.8, bm25_score=1.0,
                     rrf_score=0.03, rerank_score=rerank)


class _FakeLLM:
    def __init__(self, text: str):
        self.text = text

    async def ainvoke(self, messages):
        return type("R", (), {"content": self.text})()


def test_cited_answer_keeps_only_cited_sources():
    result = RetrievalResult(
        candidates=[_candidate("a", "退款审核 5 个工作日"), _candidate("b", "禁售电子烟")],
        answerable=True, refusal_reason=None, degraded=[], trace={},
    )
    answer = asyncio.run(generate_answer("退款审核几天", result, llm=_FakeLLM("审核需要 5 个工作日 [1]。")))
    assert answer.answerable is True
    assert [c.doc_id for c in answer.citations] == ["a"]
    assert answer.degraded is None


def test_answer_without_citation_falls_back_to_extractive():
    result = RetrievalResult(
        candidates=[_candidate("a", "退款审核 5 个工作日")],
        answerable=True, refusal_reason=None, degraded=[], trace={},
    )
    answer = asyncio.run(generate_answer("退款审核几天", result, llm=_FakeLLM("5 个工作日。")))
    assert answer.degraded == "citation_missing"
    assert answer.citations[0].doc_id == "a"
    assert "退款审核 5 个工作日" in answer.answer


def test_unanswerable_returns_refusal_without_llm_call():
    result = RetrievalResult(candidates=[], answerable=False,
                             refusal_reason="no_candidates", degraded=[], trace={})
    answer = asyncio.run(generate_answer("海外购关税", result, llm=_FakeLLM("不应该被调用")))
    assert answer.answerable is False
    assert answer.citations == []
    assert "没有找到" in answer.answer


def test_llm_failure_falls_back_to_extractive():
    class BrokenLLM:
        async def ainvoke(self, messages):
            raise RuntimeError("llm down")

    result = RetrievalResult(
        candidates=[_candidate("a", "退款审核 5 个工作日")],
        answerable=True, refusal_reason=None, degraded=[], trace={},
    )
    answer = asyncio.run(generate_answer("退款审核几天", result, llm=BrokenLLM()))
    assert answer.degraded == "llm_unavailable"
    assert answer.citations[0].doc_id == "a"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_generate.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase.generate`.

- [ ] **Step 3: Implement `generate.py`**

Implementation notes:
- If `not result.answerable` → `RagAnswer(answer="没有找到足够依据回答这个问题。建议补充描述，或联系人工客服。", answerable=False, citations=[], refusal_reason=result.refusal_reason, degraded=None)`.
- Build sources from candidates; each source block:
  `[n] 《title》 {version} / 生效 {effective_date} / {section_path joined by ' / '}\n{content}`
- Prompt:
  - System: `你是二手交易平台的规则问答助手。只能依据资料回答，不得使用资料外知识。每个事实句末尾标注 [n]。资料不足或冲突时如实说明。多个版本同时出现时，优先生效版本；用户明确问历史版本时才引用旧版。`
  - Human: `问题：{query}\n\n资料：\n{sources}\n\n请回答，并保留 [n] 引用。`
- Sanitize each source content with `sanitize_rag_context` from `nodes.py` (import inside function to avoid circular import). It truncates to 6000 chars; okay.
- Call `llm.ainvoke([SystemMessage, HumanMessage])`; if `llm is None`, use `_get_llm()`.
- Parse citations: regex `r"\[(\d+)\]"`; collect order of first appearance; valid n in 1..len(candidates); if none → extractive fallback.
- Extractive fallback: `answer = "根据《title》{section}：\n" + top.chunk.content`, citation n=1; `degraded="citation_missing"` or `"llm_unavailable"`.
- Renumber citations to 1..k in order of first appearance; replace `[old]` with `[new]` in answer? If we renumber, the answer text should match. Simpler: keep original n and citations list with those n; if some sources unused, gaps are okay but awkward. For display, renumber and replace using regex with a mapping. Do it in order of appearance:
  - `used = []`; for each match, if old n valid and not seen, append old n. Then map old→new; `answer = re.sub(r"\[(\d+)\]", lambda m: f"[{mapping.get(int(m.group(1)), m.group(1))}]", answer)`. Citations in new order.
- If the model cites a source but the citation is invalid, ignore it (and if no valid citations → fallback).
- `effective_date.isoformat()`.
- `section = " / ".join(chunk.section_path)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_generate.py -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/generate.py ai-service/tests/test_rag_showcase_generate.py
git commit -m "feat(rag): 带引用的规则问答与抽取式降级"
```

---

### Task 6: Trace 存储与服务编排

**Files:**
- Create: `ai-service/app/services/rag_showcase/trace.py`
- Create: `ai-service/app/services/rag_showcase/service.py`
- Modify: `ai-service/app/config.py`（新增 settings）
- Test: `ai-service/tests/test_rag_showcase_service.py`

**Interfaces:**
- Consumes: `RulebookIndex`, `RetrievalConfig`, `retrieve`, `generate_answer`, `TraceStore`.
- Produces:
  - `class TraceStore(path: Path | None, limit: int = 100)`: `add(trace: dict) -> None`, `recent(limit: int = 50) -> list[dict]`
  - `class RagShowcaseService(index: RulebookIndex, trace_store: TraceStore, config: RetrievalConfig)`: `async def query(query: str, *, include_superseded: bool | None = None, debug: bool = False) -> dict`; `async def rebuild(force: bool = False) -> dict`; `def corpus(self) -> list[dict]`
  - `def get_service() -> RagShowcaseService`（lazy singleton；索引缺失时尝试 build）
  - `def reset_service_for_tests() -> None`

Settings additions in `app/config.py`:
```python
# ── 平台规则 RAG（rag_showcase）──
rag_showcase_corpus_path: str = "./data/rag_corpus/rules"
rag_showcase_index_dir: str = "./data/faiss_indexes"
rag_showcase_trace_path: str = "./data/rag_traces.jsonl"
rag_showcase_trace_limit: int = 100
rag_showcase_top_k: int = 5
rag_showcase_recall_k: int = 20
rag_showcase_min_rerank_score: float = 0.05
rag_showcase_min_vector_score: float = 0.30
rag_showcase_auto_rebuild: bool = True
```

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_service.py
import asyncio
import json
from datetime import date
from pathlib import Path

import numpy as np

from app.services.rag_showcase.index import RulebookIndex
from app.services.rag_showcase.service import RagShowcaseService
from app.services.rag_showcase.trace import TraceStore


def _doc(doc_id: str, content: str) -> str:
    return f"""---
doc_id: {doc_id}
title: {doc_id}
category: test
version: v1
effective_date: 2026-01-01
status: active
---

## 一

{content}
"""


async def _fake_embed(texts: list[str]) -> list[list[float]]:
    out = []
    for t in texts:
        v = np.zeros(8, dtype="float32")
        for i, ch in enumerate(t):
            v[ord(ch) % 8] += 1
        out.append((v / (np.linalg.norm(v) or 1)).tolist())
    return out


async def _fake_query_embed(text: str) -> list[float]:
    return (await _fake_embed([text]))[0]


async def _fake_rerank(query: str, docs: list[str]) -> list[float]:
    return [0.9 if "退款" in d else 0.0 for d in docs]


class _FakeLLM:
    async def ainvoke(self, messages):
        return type("R", (), {"content": "审核需要 5 个工作日 [1]。"})()


def _service(tmp_path: Path) -> RagShowcaseService:
    corpus = tmp_path / "corpus"
    corpus.mkdir()
    (corpus / "a.md").write_text(_doc("returns", "退款审核 5 个工作日"), encoding="utf-8")
    index = RulebookIndex(corpus, tmp_path / "index", embedder=_fake_embed)
    asyncio.run(index.build(force=True))
    asyncio.run(index.load())
    return RagShowcaseService(
        index=index,
        trace_store=TraceStore(tmp_path / "traces.jsonl", limit=10),
        config=None,  # 测试里替换为默认 RetrievalConfig
    )


def test_query_returns_answer_citations_and_trace(tmp_path: Path, monkeypatch):
    service = _service(tmp_path)
    monkeypatch.setattr("app.services.rag_showcase.service.embed_query", _fake_query_embed)
    monkeypatch.setattr("app.services.rag_showcase.service._rerank_documents", _fake_rerank)
    monkeypatch.setattr("app.services.rag_showcase.service._get_llm", lambda: _FakeLLM())
    result = asyncio.run(service.query("退款审核几天", debug=True))
    assert result["answerable"] is True
    assert result["citations"][0]["doc_id"] == "returns"
    assert "trace" in result
    assert service.trace_store.recent(1)


def test_corpus_lists_documents(tmp_path: Path):
    service = _service(tmp_path)
    docs = service.corpus()
    assert docs[0]["doc_id"] == "returns"
    assert docs[0]["chunk_count"] == 1


def test_trace_store_persists_jsonl(tmp_path: Path):
    store = TraceStore(tmp_path / "traces.jsonl", limit=2)
    store.add({"query": "a"})
    store.add({"query": "b"})
    store.add({"query": "c"})
    assert [t["query"] for t in store.recent(10)] == ["b", "c"]
    lines = (tmp_path / "traces.jsonl").read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 3
    assert json.loads(lines[-1])["query"] == "c"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_service.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase.service`.

- [ ] **Step 3: Implement `trace.py`, `service.py`, and settings**

`trace.py`:
- `TraceStore(path, limit)` holds `deque(maxlen=limit)`; `add` appends dict with `timestamp` (UTC ISO) if absent; writes one JSON line to path (create parent dirs); failures only log warning.
- `recent(limit)` returns last `limit` in chronological order.

`service.py`:
- `RagShowcaseService.query`:
  - `result = await retrieve(self.index, query, config=config, today=date.today())`; if `include_superseded` is not None, override config via `dataclasses.replace`.
  - `answer = await generate_answer(query, result)`.
  - Build dict:
    ```python
    {
      "query": query,
      "answer": answer.answer,
      "answerable": answer.answerable,
      "citations": [asdict(c) for c in answer.citations],
      "refusal_reason": answer.refusal_reason,
      "degraded": answer.degraded,
      "trace": result.trace if debug else {"degraded": result.degraded, "latency_ms": result.trace.get("latency_ms", {})},
    }
    ```
  - Add trace record with query, answerable, citations doc_ids, degraded, `result.trace`; `trace_store.add`.
- `rebuild(force)` calls `index.build`; reload; return manifest.
- `corpus()` groups chunks by doc_id; returns `[{doc_id,title,version,effective_date,status,category,chunk_count,sections}]`.
- `get_service()` lazy:
  - parse settings paths to `Path`; create `RulebookIndex`, `TraceStore`, `RetrievalConfig` from settings.
  - `loaded = await index.load()`; if not loaded or stale and `auto_rebuild` → `await index.build()` then `load()`.
  - Cache module-level `_service`.
- `reset_service_for_tests()` sets `_service = None`.
- Avoid circular imports: import `_get_llm` inside `generate_answer` default, not at module top; service imports `embed_query`, `_rerank_documents` at module level so tests can monkeypatch. To make monkeypatch work, `retrieve` should default to those module attributes passed as arguments; service passes `embedder=embed_query, reranker=_rerank_documents`. Tests monkeypatch service module attributes. Good.
- Settings: add fields with defaults; `rag_showcase_auto_rebuild: bool = True`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_service.py -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/trace.py ai-service/app/services/rag_showcase/service.py ai-service/app/config.py ai-service/tests/test_rag_showcase_service.py
git commit -m "feat(rag): 服务编排、trace 存储与配置"
```

---

### Task 7: Python API 路由与注册

**Files:**
- Create: `ai-service/app/routers/rag_showcase.py`
- Modify: `ai-service/app/main.py`（注册 router；若 main 不存在，找实际的 app 入口）
- Test: `ai-service/tests/test_rag_showcase_router.py`

**Interfaces:**
- Consumes: `get_service`, `ApiResponse`, `settings.internal_api_token`.
- Produces:
  - `router = APIRouter(prefix="/ai/rag-showcase", tags=["RAG 规则问答"])`
  - `POST /query`, `POST /rebuild`, `GET /corpus`, `GET /traces?limit=`, `GET /eval/latest`.

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_router.py
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.routers.rag_showcase import router


class _FakeService:
    async def query(self, query, include_superseded=None, debug=False):
        return {"query": query, "answer": "ok", "answerable": True, "citations": [], "trace": {}}

    async def rebuild(self, force=False):
        return {"chunk_count": 1}

    def corpus(self):
        return [{"doc_id": "a"}]


def _client(monkeypatch):
    import app.routers.rag_showcase as mod
    monkeypatch.setattr(mod, "get_service", lambda: _FakeService())
    app = FastAPI()
    app.include_router(router)
    return TestClient(app)


def test_query_endpoint(monkeypatch):
    client = _client(monkeypatch)
    resp = client.post("/ai/rag-showcase/query", json={"query": "退款几天"})
    assert resp.status_code == 200
    assert resp.json()["data"]["answer"] == "ok"


def test_query_rejects_empty(monkeypatch):
    client = _client(monkeypatch)
    resp = client.post("/ai/rag-showcase/query", json={"query": "  "})
    assert resp.status_code == 200
    assert resp.json()["code"] == 400


def test_corpus_endpoint(monkeypatch):
    client = _client(monkeypatch)
    resp = client.get("/ai/rag-showcase/corpus")
    assert resp.json()["data"][0]["doc_id"] == "a"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_router.py -v`
Expected: FAIL with `ModuleNotFoundError: app.routers.rag_showcase`.

- [ ] **Step 3: Implement router and register**

- Router endpoints:
  - `POST /query`: body `dict`; `query = (body.get("query") or "").strip()`; if empty → `ApiResponse(code=400, msg="query 不能为空")`; try `get_service().query(...)`; catch Exception → log + `ApiResponse(code=500, msg="RAG 服务暂时不可用")`.
  - `POST /rebuild`: optional body; if `settings.internal_api_token` and header `X-Internal-Token` mismatch → `ApiResponse(code=401, msg="unauthorized")`; call `get_service().rebuild(force=bool(body.get("force")))`.
  - `GET /corpus`, `GET /traces` (limit clamp 1..200), `GET /eval/latest` (read `docs/rag-showcase/eval-report.json` relative to repo? Use `Path(__file__).resolve().parents[3] / "docs/rag-showcase/eval-report.json"`; if missing → `ApiResponse(code=404, msg="暂无评测报告")`).
- Register in `app/main.py`: inspect existing imports/includes and add `from app.routers.rag_showcase import router as rag_showcase_router` + `app.include_router(rag_showcase_router)`.
- If `TestClient` requires `httpx` (installed).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_router.py -v`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/routers/rag_showcase.py ai-service/app/main.py ai-service/tests/test_rag_showcase_router.py
git commit -m "feat(rag): /ai/rag-showcase API"
```

---

### Task 8: 真实演示语料（12 篇）

**Files:**
- Create: `ai-service/data/rag_corpus/rules/returns-refund-v1.md`
- Create: `.../returns-refund-v2.md`
- Create: `.../refund-timeline.md`
- Create: `.../shipping-fee.md`
- Create: `.../prohibited-items.md`
- Create: `.../dispute-sop.md`
- Create: `.../community-convention.md`
- Create: `.../credit-score.md`
- Create: `.../seller-fees.md`
- Create: `.../runner-rules.md`
- Create: `.../account-faq.md`
- Create: `.../secondhand-warranty.md`

**Interfaces:**
- Consumes: Task 1 front matter format.
- Produces: 12 valid Markdown docs; `load_corpus` returns ≥ 40 chunks.

- [ ] **Step 1: Write the 12 docs**

Content requirements (exact values must be consistent across docs):
- `returns-refund-v1` (superseded, effective 2025-01-01): 退款审核 3 个工作日；质量问题退货运费买家承担；声明旧版。
- `returns-refund-v2` (active, effective 2026-01-01, supersedes v1): 退款审核 5 个工作日；质量问题退货运费卖家承担；非质量问题买家承担；7 天无理由；旧版失效。
- `refund-timeline`: 审核通过后，支付宝 1–2 个工作日、微信 1–3 个工作日、银行卡 3–7 个工作日；跨境/大额 7–15 个工作日。
- `shipping-fee`: 质量问题卖家承担；非质量问题买家承担；运费险赔付上限 12 元；大件家具/家电退货运费双方协商，平台可补贴最高 50 元；同城自提退货买家自提。
- `prohibited-items`: 药品/处方药、电子烟、易燃易爆、管制刀具、野生动物制品、盗版、个人信息、虚拟账号、活体宠物禁售；二手手机/电脑允许但需如实描述。
- `dispute-sop`: 买家申请售后 → 上传凭证 → 卖家 48 小时响应 → 平台 3 个工作日介入 → 判定；凭证类型；卖家拒绝后流程。
- `community-convention`: 面交选公共场所；定金不超过 20%；放鸽子扣信用分；骚扰/辱骂处罚。
- `credit-score`: 完成交易 +2；取消 -5；纠纷败诉 -10；刷单 -20；90 天滚动；信用分低于 60 限制发布。
- `seller-fees`: 平台服务费 1%；提现手续费 0.6%，每月前 1000 元免费；大额提现 T+1。
- `runner-rules`: 保证金 50 元；超时 30 分钟赔付订单金额 10%（上限 20 元）；虚假接单 -10 信用分。
- `account-faq`: 实名认证；换绑手机 24 小时生效；注销账号需无进行中订单，7 天冷静期。
- `secondhand-warranty`: 默认无保修；卖家承诺例外；7 天质量问题可退；电子产品 48 小时验机期。

- [ ] **Step 2: Validate corpus loads**

Run:
```bash
cd ai-service && py -c "from pathlib import Path; from app.services.rag_showcase.corpus import load_corpus; s=load_corpus(Path('data/rag_corpus/rules')); print(len(s.chunks), s.corpus_hash[:12]); print([c.doc_id for c in s.chunks][:5])"
```
Expected: `>= 40` chunks and a hash.

- [ ] **Step 3: Commit**

```bash
git add ai-service/data/rag_corpus/rules
git commit -m "docs(rag): 演示用平台规则语料（12 篇，含版本冲突与不可回答面）"
```

---

### Task 9: Golden set（60+ 条）与离线校验

**Files:**
- Create: `ai-service/tests/rag_eval/cases.jsonl`
- Create: `ai-service/tests/rag_eval/README.md`（可选）
- Test: `ai-service/tests/test_rag_showcase_eval_cases.py`

**Interfaces:**
- Consumes: corpus docs from Task 8.
- Produces: JSONL cases with fields `id, query, type, expected_doc_ids, expected_sections, answer_keywords, answerable`.

- [ ] **Step 1: Write the failing validation test**

```python
# ai-service/tests/test_rag_showcase_eval_cases.py
import json
from pathlib import Path

from app.services.rag_showcase.corpus import load_corpus

CASES = Path(__file__).resolve().parent / "rag_eval" / "cases.jsonl"
CORPUS = Path(__file__).resolve().parents[1] / "data" / "rag_corpus" / "rules"


def _load():
    return [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines() if line.strip()]


def test_cases_have_required_fields_and_valid_docs():
    docs = {c.doc_id for c in load_corpus(CORPUS).chunks}
    cases = _load()
    assert len(cases) >= 60
    for case in cases:
        assert case["id"] and case["query"] and case["type"]
        assert case["type"] in {"fact", "paraphrase", "numeric", "multihop", "version", "unanswerable", "injection"}
        assert isinstance(case["answerable"], bool)
        for doc_id in case["expected_doc_ids"]:
            assert doc_id in docs, f"{case['id']}: unknown doc {doc_id}"


def test_unanswerable_cases_have_no_expected_docs():
    for case in _load():
        if case["type"] == "unanswerable":
            assert case["answerable"] is False
            assert case["expected_doc_ids"] == []
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_eval_cases.py -v`
Expected: FAIL because `cases.jsonl` missing.

- [ ] **Step 3: Write 60+ cases**

Distribution guidance:
- 12 `fact`, 12 `paraphrase`, 10 `numeric`, 10 `multihop`, 8 `version`, 8 `unanswerable`, 5 `injection` = 65.
- Every active doc covered at least 2 times.
- Version cases must expect `returns-refund-v1` only when query explicitly asks 2025/旧版.
- Multihop cases must list 2+ expected docs.
- Unanswerable: 海外购关税、客服电话、线下门店地址、发票开具、卖家实名信息、平台上市时间、跨境物流、会员费.
- Injection: queries asking to ignore rules, reveal system prompt, or make policy exceptions.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_eval_cases.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/tests/rag_eval/cases.jsonl ai-service/tests/test_rag_showcase_eval_cases.py
git commit -m "test(rag): 65 条 golden set 与离线结构校验"
```

---

### Task 10: 评测脚本与 baseline 报告

**Files:**
- Create: `ai-service/app/services/rag_showcase/eval.py`
- Create: `ai-service/scripts/rag_showcase/run_eval.py`
- Test: `ai-service/tests/test_rag_showcase_eval.py`

**Interfaces:**
- Consumes: `RulebookIndex`, `retrieve`, `RetrievalConfig`, `generate_answer`, cases JSONL.
- Produces:
  - `load_cases(path: Path) -> list[dict]`
  - `validate_cases(cases: list[dict], corpus_chunks: list[Chunk]) -> list[str]`
  - `recall_at_k(retrieved: list[str], expected: list[str], k: int) -> float`
  - `mrr(retrieved: list[str], expected: list[str]) -> float`
  - `ndcg_at_k(retrieved: list[str], expected: list[str], k: int) -> float`
  - `async def run_retrieval_eval(index, cases, configs: dict[str, RetrievalConfig], *, embedder, reranker) -> dict`
  - `async def run_full_eval(index, cases, *, embedder, reranker, llm, generate=True) -> dict`
  - `def render_markdown(report: dict) -> str`

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_eval.py
from app.services.rag_showcase.eval import mrr, ndcg_at_k, recall_at_k


def test_recall_at_k_doc_level():
    assert recall_at_k(["a", "b", "c"], ["a"], 1) == 1.0
    assert recall_at_k(["b", "a"], ["a"], 1) == 0.0
    assert recall_at_k(["b", "a"], ["a"], 2) == 1.0


def test_recall_multihop_coverage():
    assert recall_at_k(["a", "x", "b"], ["a", "b"], 3) == 1.0
    assert recall_at_k(["a", "x", "y"], ["a", "b"], 3) == 0.5


def test_mrr_first_relevant_rank():
    assert mrr(["x", "a", "b"], ["a", "b"]) == 0.5
    assert mrr(["a"], ["a"]) == 1.0
    assert mrr(["x"], ["a"]) == 0.0


def test_ndcg_at_k_binary_relevance():
    assert round(ndcg_at_k(["a", "x", "b"], ["a", "b"], 3), 4) == 1.0
    assert round(ndcg_at_k(["x", "a"], ["a"], 2), 4) > 0
    assert ndcg_at_k(["x", "y"], ["a"], 2) == 0.0
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_eval.py -v`
Expected: FAIL with `ModuleNotFoundError: app.services.rag_showcase.eval`.

- [ ] **Step 3: Implement metrics and eval runner**

Implementation notes:
- Retrieval lists are doc_id sequences in rank order (dedupe preserving order).
- `recall_at_k`: coverage of expected docs in top-k; if expected empty → 1.0.
- `mrr`: 1/rank of first expected doc; 0 if none.
- `ndcg_at_k`: binary gains; IDCG from min(len(expected), k); DCG = sum(gain/log2(i+2)).
- `run_retrieval_eval`: for each config, for each case call `retrieve`; record retrieved doc IDs, answerable, degraded, latency. Metrics per config overall and per type.
- Configs:
  - `B0_vector_only`: `use_bm25=False, use_rerank=False, include_superseded=True`
  - `B1_vector_filter`: `use_bm25=False, use_rerank=False, include_superseded=False`
  - `B2_hybrid`: `use_bm25=True, use_rerank=False, include_superseded=False`
  - `B3_hybrid_rerank`: `use_bm25=True, use_rerank=True, include_superseded=False`
- `run_full_eval`: run B3 retrieval + generation for each case; compute refusal accuracy and citation precision; include retrieval metrics from B3.
  - Refusal accuracy: for `answerable=False` expected, count `answer.answerable=False`; for `answerable=True`, count `answer.answerable=True`. Overall accuracy = correct/total.
  - Citation precision: for answerable cases, fraction of citations whose doc_id in expected; if no citations → 0.
- Report dict structure:
  ```json
  {"generated_at": "...", "case_count": 65, "configs": {"B0...": {"metrics": {...}, "by_type": {...}}}, "full": {...}, "failures": [...]}
  ```
- `render_markdown`: table of configs × (Recall@1/3/5, MRR, nDCG@5, refusal_acc, citation_precision, p50/p95), per-type breakdown, failure examples.
- CLI `run_eval.py`:
  - args: `--online`, `--limit`, `--output-dir docs/rag-showcase`, `--judge` (reserved; if set, log "judge not implemented" and continue? Better implement a simple judge in Task 10 if time; otherwise omit flag).
  - Offline default: validate cases against corpus, print distribution; exit 0/1.
  - Online: build/load index (with real embedder), run configs, write `eval-report.json` and `eval-report.md` to `docs/rag-showcase/` (repo root relative to ai-service: `../docs/rag-showcase`).
  - If `EMBEDDING_API_KEY` missing → print error, exit 2.
- Use `date.today()` for filtering.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_eval.py -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/rag_showcase/eval.py ai-service/scripts/rag_showcase/run_eval.py ai-service/tests/test_rag_showcase_eval.py
git commit -m "feat(rag): 4 组 baseline 评测与报告生成"
```

---

### Task 11: 导购 Agent 集成 `search_platform_rules`

**Files:**
- Modify: `ai-service/app/services/agent/tools.py`（新增工具、加入工具清单）
- Modify: `ai-service/app/services/agent/nodes.py`（`TOOL_STATUS_LABELS` 增加标签）
- Test: `ai-service/tests/test_rag_showcase_agent_tool.py`

**Interfaces:**
- Consumes: `app.services.rag_showcase.service.get_service`.
- Produces: `search_platform_rules(query: str) -> str` tool; present in `PRE_SALE_TOOLS` and `AFTER_SALE_TOOLS`.

- [ ] **Step 1: Write the failing test**

```python
# ai-service/tests/test_rag_showcase_agent_tool.py
import asyncio
import json

from app.services.agent.tools import AFTER_SALE_TOOLS, PRE_SALE_TOOLS, search_platform_rules


def test_tool_is_available_in_pre_and_after_sale():
    assert search_platform_rules in PRE_SALE_TOOLS
    assert search_platform_rules in AFTER_SALE_TOOLS


def test_tool_returns_answer_and_citations(monkeypatch):
    class _FakeService:
        async def query(self, query, include_superseded=None, debug=False):
            return {
                "answer": "审核需要 5 个工作日 [1]。",
                "answerable": True,
                "citations": [{"n": 1, "doc_id": "returns-refund-v2", "title": "退换货政策", "version": "v2", "section": "退款时效", "score": 0.9}],
                "refusal_reason": None,
                "degraded": None,
            }

    monkeypatch.setattr("app.services.agent.tools._get_rag_service", lambda: _FakeService())
    raw = asyncio.run(search_platform_rules.ainvoke({"query": "退款审核几天"}))
    data = json.loads(raw)
    assert data["answerable"] is True
    assert data["answer"].startswith("审核需要")
    assert data["citations"][0]["doc_id"] == "returns-refund-v2"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_agent_tool.py -v`
Expected: FAIL with `ImportError: cannot import name 'search_platform_rules'`.

- [ ] **Step 3: Implement tool and prompts**

- In `tools.py`:
  - Add a lazy accessor:
    ```python
    def _get_rag_service():
        from app.services.rag_showcase.service import get_service
        return get_service()
    ```
    Tests monkeypatch this function.
  - Add `@tool async def search_platform_rules(query: str) -> str`: docstring `[L1] 查询平台规则/售后政策/禁售清单...`; call `_get_rag_service().query(query)`; truncate citations to 3; return `json.dumps({"answer":..., "answerable":..., "citations":..., "degraded":...}, ensure_ascii=False)`; on exception return `{"error": "...", "answerable": False}`.
  - Add to `PRE_SALE_TOOLS` and `AFTER_SALE_TOOLS`.
- In `nodes.py` `TOOL_STATUS_LABELS`: add `"search_platform_rules": "正在查阅平台规则"`.
- In `GUIDE_SYSTEM_PROMPT` or `skills/guide-agent/SKILL.md`: add a rule: 平台规则/退款时效/运费/禁售/纠纷流程问题优先调用 `search_platform_rules`，转述时保留 `[n]` 引用；查不到时如实告知。
  - Prefer editing the Skill Markdown if it exists; otherwise the built-in prompt. Check `ai-service/skills/guide-agent/SKILL.md`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai-service && py -m pytest tests/test_rag_showcase_agent_tool.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ai-service/app/services/agent/tools.py ai-service/app/services/agent/nodes.py ai-service/skills/guide-agent/SKILL.md ai-service/tests/test_rag_showcase_agent_tool.py
git commit -m "feat(rag): 导购 Agent 接入 search_platform_rules"
```

---

### Task 12: CLI demo、README、最终验证

**Files:**
- Create: `ai-service/scripts/rag_showcase/build_index.py`
- Create: `ai-service/scripts/rag_showcase/demo.py`
- Create: `docs/rag-showcase/README.md`
- Create: `docs/rag-showcase/eval-report.md`（Task 10 生成）
- Test: 手动 smoke + 全量单测。

**Interfaces:**
- Consumes: everything above.
- Produces: reproducible commands.

- [ ] **Step 1: Implement `build_index.py`**

Args: `--force`, `--corpus-dir`, `--index-dir`; `async def main()`:
```python
index = RulebookIndex(Path(args.corpus_dir), Path(args.index_dir))
manifest = await index.build(force=args.force)
print(json.dumps(manifest, ensure_ascii=False, indent=2))
```
Run:
```bash
cd ai-service && py scripts/rag_showcase/build_index.py --force
```
Expected: manifest with `chunk_count >= 40`.

- [ ] **Step 2: Implement `demo.py`**

Args: `query` positional; `--debug`; call `get_service().query(query, debug=True)`; print answer, citations (title/version/section/score), and a compact trace table (vector/BM25/RRF/rerank top 5). Exit 0.

- [ ] **Step 3: Run online eval**

```bash
cd ai-service && py scripts/rag_showcase/run_eval.py --online
```
Expected: `docs/rag-showcase/eval-report.json` + `.md`; B3 metrics printed. If network fails, report degraded and do not fabricate; record the failure in README.

- [ ] **Step 4: Write `docs/rag-showcase/README.md`**

Contents:
- What it is; why RAG here; architecture diagram (ASCII).
- Corpus layout and front matter.
- Commands: build index, demo, eval.
- Eval table (paste from generated report).
- Design tradeoffs: hybrid, RRF, version filter, refusal, FAISS vs Milvus.
- Failure modes and degradations.
- Interview talking points (short).
- Disclaimer: 语料为虚构演示数据.
- Phase 2: Java/UI debug console.

- [ ] **Step 5: Run full test suite**

```bash
cd ai-service && py -m pytest -q
```
Expected: all new tests pass; existing tests no regression. If pre-existing failures, record them and compare to baseline.

- [ ] **Step 6: Commit**

```bash
git add ai-service/scripts/rag_showcase docs/rag-showcase
git commit -m "docs(rag): 复现命令、评测报告与面试讲述材料"
```

---

## Self-Review

**Spec coverage:**
- 语料 12 篇 → Task 8.
- 结构感知切片 → Task 1.
- 混合检索/BM25/RRF/rerank → Task 2/4.
- 版本/生效日期过滤 → Task 4.
- 拒答/引用/抽取式降级 → Task 5.
- 服务/API/trace → Task 6/7.
- Golden set/评测/baseline 报告 → Task 9/10.
- Agent 集成 → Task 11.
- CLI/README/面试材料 → Task 12.
- 安全注入/降级 → Task 4/5 tests + Task 12 README.
- Phase 2 Java/UI → 设计文档已列为可选，不在本计划（非阻塞）。

**Type consistency:**
- `Chunk` fields used consistently in index/retrieval/generate/eval.
- `RetrievalConfig` flags used by eval baselines.
- `Citation` fields match service output and agent tool test.
- `RulebookIndex` interface used by service/eval/scripts.

**Review Focus tests:**
- Unanswerable → Task 4 `test_unanswerable_is_refused`, Task 5 `test_unanswerable_returns_refusal_without_llm_call`.
- Historical version → Task 4 `test_historical_query_includes_superseded`.
- External failure → Task 4 embedding/rerank failure tests.
- Missing/stale index → Task 3 `test_missing_index_load_returns_false`, Task 6 service auto-rebuild.
- Prompt injection → Task 5 generation prompt sanitize + Task 9 injection cases; README documents behavior. (Add a unit test in Task 5 for injection if sanitize changes content? `sanitize_rag_context` only prefixes a warning; the prompt already says sources are untrusted. The eval injection cases measure refusal/ignore.)
