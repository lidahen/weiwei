"""平台规则语料：front matter 解析与结构感知切片。

语料以 Markdown 存在 Git 中，每个文件一篇政策；索引是派生产物。
切片按标题层级切分，保留 heading path，长小节再按段落/字符切，绝不跨小节合并。
"""
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable

import yaml

_REQUIRED_FIELDS = ("doc_id", "title", "category", "version", "effective_date", "status")
_FRONT_MATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?(.*)\Z", re.S)
_HEADING_RE = re.compile(r"^(#{1,3})\s+(.+?)\s*$")
_PARAGRAPH_RE = re.compile(r"\n\s*\n+")


@dataclass(frozen=True)
class DocMeta:
    doc_id: str
    title: str
    category: str
    version: str
    effective_date: date
    status: str
    supersedes: str | None
    tags: tuple[str, ...]


@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    doc_id: str
    title: str
    category: str
    version: str
    effective_date: date
    status: str
    section_path: tuple[str, ...]
    content: str
    char_count: int


@dataclass(frozen=True)
class CorpusSnapshot:
    chunks: tuple[Chunk, ...]
    corpus_hash: str
    doc_hashes: dict[str, str]


def parse_doc(path: Path) -> tuple[DocMeta, str]:
    """解析一篇 Markdown 的 YAML front matter 与正文。"""
    text = path.read_text(encoding="utf-8")
    match = _FRONT_MATTER_RE.match(text)
    if not match:
        raise ValueError(f"{path.name}: missing YAML front matter")
    raw_meta = yaml.safe_load(match.group(1)) or {}
    if not isinstance(raw_meta, dict):
        raise ValueError(f"{path.name}: front matter must be a mapping")
    for key in _REQUIRED_FIELDS:
        if key not in raw_meta or raw_meta[key] in (None, ""):
            raise ValueError(f"{path.name}: missing required field {key}")

    status = str(raw_meta["status"]).strip()
    if status not in ("active", "superseded"):
        raise ValueError(f"{path.name}: status must be active or superseded")
    try:
        effective = date.fromisoformat(str(raw_meta["effective_date"]))
    except ValueError as exc:
        raise ValueError(f"{path.name}: invalid effective_date") from exc

    tags_raw = raw_meta.get("tags") or ()
    if isinstance(tags_raw, str):
        tags_raw = (tags_raw,)
    meta = DocMeta(
        doc_id=str(raw_meta["doc_id"]).strip(),
        title=str(raw_meta["title"]).strip(),
        category=str(raw_meta["category"]).strip(),
        version=str(raw_meta["version"]).strip(),
        effective_date=effective,
        status=status,
        supersedes=str(raw_meta["supersedes"]).strip() if raw_meta.get("supersedes") else None,
        tags=tuple(str(t).strip() for t in tags_raw if str(t).strip()),
    )
    return meta, match.group(2)


def _split_long_text(text: str, budget: int, overlap: int) -> list[str]:
    """把超长单段切成窗口，相邻窗口保留 overlap 字符。"""
    if budget <= 0:
        raise ValueError("budget must be positive")
    if len(text) <= budget:
        return [text]
    step = max(1, budget - overlap)
    return [text[i:i + budget] for i in range(0, len(text), step) if text[i:i + budget].strip()]


def _split_section(heading: str, body: str, max_chars: int, target_chars: int, overlap: int) -> list[str]:
    """切一个小节：短则整段，长则按段落累积；单段过长再按字符窗口切。"""
    whole = f"{heading}\n\n{body.strip()}".strip()
    if len(whole) <= max_chars:
        return [whole]

    budget = max(1, max_chars - len(heading) - 2)
    paragraphs = [p.strip() for p in _PARAGRAPH_RE.split(body.strip()) if p.strip()]
    pieces: list[str] = []
    current = ""
    for para in paragraphs:
        candidates = [para] if len(para) <= budget else _split_long_text(para, budget, overlap)
        for candidate in candidates:
            if not current:
                current = candidate
                continue
            merged = f"{current}\n\n{candidate}"
            if len(merged) <= budget:
                current = merged
            else:
                pieces.append(current)
                # 段落间的重叠：把上一块尾部带入下一块，保证上下文连贯
                tail = current[-overlap:] if overlap > 0 else ""
                current = f"{tail}\n\n{candidate}".strip() if tail else candidate
    if current:
        pieces.append(current)
    return [f"{heading}\n\n{piece}".strip() for piece in pieces if piece.strip()]


def chunk_doc(
    meta: DocMeta,
    body: str,
    *,
    max_chars: int = 500,
    target_chars: int = 350,
    overlap: int = 40,
) -> list[Chunk]:
    """按标题层级切片；一节可切成多块，但绝不跨节合并。"""
    sections: list[tuple[tuple[str, ...], str, list[str]]] = []
    heading_by_level: dict[int, str] = {}
    path: tuple[str, ...] = ()
    heading = ""
    body_lines: list[str] = []

    def flush() -> None:
        nonlocal body_lines
        if heading:
            sections.append((path, heading, body_lines))
        body_lines = []

    for line in (body or "").splitlines():
        m = _HEADING_RE.match(line)
        if m:
            flush()
            level = len(m.group(1))
            text = m.group(2).strip()
            heading_by_level[level] = text
            for deeper in [l for l in heading_by_level if l > level]:
                del heading_by_level[deeper]
            path = tuple(heading_by_level[l] for l in sorted(heading_by_level) if l <= level)
            heading = line.strip()
        elif heading:
            body_lines.append(line)
    flush()

    if not sections:
        # 无标题的文档：整篇作为一个块，避免静默丢内容
        text = (body or "").strip()
        if not text:
            return []
        sections = [((), "", [text])]

    chunks: list[Chunk] = []
    for section_path, section_heading, lines in sections:
        section_body = "\n".join(lines).strip()
        if not section_body:
            # 纯标题（如文档大标题）不单独成块：没有可检索的内容，只会稀释 top-k
            continue
        pieces = _split_section(section_heading or meta.title, section_body, max_chars, target_chars, overlap)
        for piece in pieces:
            idx = len(chunks)
            chunks.append(
                Chunk(
                    chunk_id=f"{meta.doc_id}#{idx:03d}",
                    doc_id=meta.doc_id,
                    title=meta.title,
                    category=meta.category,
                    version=meta.version,
                    effective_date=meta.effective_date,
                    status=meta.status,
                    section_path=section_path,
                    content=piece,
                    char_count=len(piece),
                )
            )
    return chunks


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_corpus(corpus_dir: Path) -> CorpusSnapshot:
    """加载目录下全部 .md，返回切片、语料哈希与逐文档哈希。"""
    corpus_dir = Path(corpus_dir)
    if not corpus_dir.exists():
        raise FileNotFoundError(f"corpus dir not found: {corpus_dir}")

    chunks: list[Chunk] = []
    doc_hashes: dict[str, str] = {}
    for path in sorted(corpus_dir.glob("*.md")):
        meta, body = parse_doc(path)
        if meta.doc_id in doc_hashes:
            raise ValueError(f"duplicate doc_id: {meta.doc_id}")
        doc_hashes[meta.doc_id] = _sha256(path.read_bytes())
        chunks.extend(chunk_doc(meta, body))

    corpus_hash = _sha256(
        json.dumps(sorted(doc_hashes.items()), ensure_ascii=False).encode("utf-8")
    )
    return CorpusSnapshot(chunks=tuple(chunks), corpus_hash=corpus_hash, doc_hashes=doc_hashes)
