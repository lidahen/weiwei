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
