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
