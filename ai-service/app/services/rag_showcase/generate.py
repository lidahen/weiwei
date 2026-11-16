"""基于检索结果的规则问答：强制引用、拒答、抽取式降级。"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from langchain_core.messages import HumanMessage, SystemMessage

from app.services.rag_showcase.retrieval import Candidate, RetrievalResult

logger = logging.getLogger(__name__)

_CITATION_RE = re.compile(r"\[(\d+)\]")
_MAX_SOURCE_CHARS = 6000

SYSTEM_PROMPT = (
    "你是二手交易平台的规则问答助手。只能依据下方编号资料回答，不得使用资料外知识。\n"
    "要求：\n"
    "1. 每个事实句末尾标注 [n]；\n"
    "2. 资料不足、互相冲突或无法确定时，如实说明，不要编造；\n"
    "3. 多个版本同时出现时，优先生效版本；用户明确问历史版本时才引用旧版；\n"
    "4. 只引用直接支持该句的 1–2 条资料，不要罗列无关资料；\n"
    "5. 回答简洁，使用简体中文。"
)


@dataclass
class Citation:
    n: int
    chunk_id: str
    doc_id: str
    title: str
    version: str
    effective_date: str
    section: str
    score: float


@dataclass
class RagAnswer:
    answer: str
    answerable: bool
    citations: list[Citation]
    refusal_reason: str | None
    degraded: str | None


def _refusal(reason: str | None) -> RagAnswer:
    return RagAnswer(
        answer="没有找到足够依据回答这个问题。建议补充具体场景，或联系人工客服确认。",
        answerable=False,
        citations=[],
        refusal_reason=reason,
        degraded=None,
    )


def _section(candidate: Candidate) -> str:
    return " / ".join(candidate.chunk.section_path) or candidate.chunk.title


def _citation(candidate: Candidate, n: int) -> Citation:
    chunk = candidate.chunk
    score = candidate.rerank_score
    if score is None:
        score = candidate.vector_score if candidate.vector_score is not None else 0.0
    return Citation(
        n=n,
        chunk_id=chunk.chunk_id,
        doc_id=chunk.doc_id,
        title=chunk.title,
        version=chunk.version,
        effective_date=chunk.effective_date.isoformat(),
        section=_section(candidate),
        score=round(float(score), 4),
    )


def _extractive(candidate: Candidate, degraded: str) -> RagAnswer:
    return RagAnswer(
        answer=f"根据《{candidate.chunk.title}》{_section(candidate)}：\n{candidate.chunk.content}",
        answerable=True,
        citations=[_citation(candidate, 1)],
        refusal_reason=None,
        degraded=degraded,
    )


def _build_sources(candidates: list[Candidate]) -> str:
    blocks = []
    for i, candidate in enumerate(candidates, start=1):
        chunk = candidate.chunk
        blocks.append(
            f"[{i}] 《{chunk.title}》 {chunk.version} / 生效 {chunk.effective_date.isoformat()}"
            f" / {_section(candidate)}\n{chunk.content}"
        )
    raw = "\n\n".join(blocks)[:_MAX_SOURCE_CHARS]
    try:
        from app.services.agent.nodes import sanitize_rag_context

        return sanitize_rag_context(raw)
    except Exception:  # noqa: BLE001 —— 清洗是增强项，导入失败不阻断生成
        return raw


async def generate_answer(
    query: str,
    result: RetrievalResult,
    *,
    llm=None,
) -> RagAnswer:
    if not result.answerable or not result.candidates:
        return _refusal(result.refusal_reason or "no_candidates")

    sources = _build_sources(result.candidates)
    try:
        if llm is None:
            from app.services.agent.llm import _get_llm

            llm = _get_llm()
        response = await llm.ainvoke([
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=f"问题：{query}\n\n资料：\n{sources}\n\n请回答，并保留 [n] 引用。"),
        ])
        text = (getattr(response, "content", None) or "").strip()
    except Exception as e:  # noqa: BLE001 —— LLM 失败降级为抽取式回答
        logger.warning("rag generate failed, fallback to extractive: %s", e)
        return _extractive(result.candidates[0], "llm_unavailable")

    if not text:
        return _extractive(result.candidates[0], "llm_unavailable")

    valid: list[int] = []
    for match in _CITATION_RE.finditer(text):
        n = int(match.group(1))
        if 1 <= n <= len(result.candidates) and n not in valid:
            valid.append(n)
    if not valid:
        return _extractive(result.candidates[0], "citation_missing")

    mapping = {old: new for new, old in enumerate(valid, start=1)}
    text = _CITATION_RE.sub(
        lambda m: f"[{mapping[int(m.group(1))]}]" if int(m.group(1)) in mapping else "",
        text,
    )
    citations = [
        _citation(result.candidates[old - 1], mapping[old])
        for old in valid
    ]
    return RagAnswer(
        answer=text,
        answerable=True,
        citations=citations,
        refusal_reason=None,
        degraded=None,
    )
