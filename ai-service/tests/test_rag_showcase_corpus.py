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
