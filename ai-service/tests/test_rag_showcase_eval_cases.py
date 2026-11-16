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
