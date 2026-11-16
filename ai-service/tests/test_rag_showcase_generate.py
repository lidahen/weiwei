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
