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
                "citations": [
                    {"n": 1, "doc_id": "returns-refund-v2", "title": "退换货政策", "version": "v2",
                     "section": "退款时效", "score": 0.9},
                    {"n": 2, "doc_id": "refund-timeline", "title": "退款到账时效", "version": "v1",
                     "section": "到账时效", "score": 0.8},
                    {"n": 3, "doc_id": "shipping-fee", "title": "运费规则", "version": "v1",
                     "section": "责任划分", "score": 0.7},
                    {"n": 4, "doc_id": "dispute-sop", "title": "纠纷流程", "version": "v1",
                     "section": "处理流程", "score": 0.6},
                ],
                "refusal_reason": None,
                "degraded": None,
            }

    monkeypatch.setattr("app.services.agent.tools._get_rag_service", lambda: _FakeService())
    raw = asyncio.run(search_platform_rules.ainvoke({"query": "退款审核几天"}))
    data = json.loads(raw)
    assert data["answerable"] is True
    assert data["answer"].startswith("审核需要")
    assert data["citations"][0]["doc_id"] == "returns-refund-v2"
    assert len(data["citations"]) == 3


def test_tool_returns_error_payload_on_failure(monkeypatch):
    class _BrokenService:
        async def query(self, query, include_superseded=None, debug=False):
            raise RuntimeError("index unavailable")

    monkeypatch.setattr("app.services.agent.tools._get_rag_service", lambda: _BrokenService())
    raw = asyncio.run(search_platform_rules.ainvoke({"query": "退款审核几天"}))
    data = json.loads(raw)
    assert data["answerable"] is False
    assert data["error"]
