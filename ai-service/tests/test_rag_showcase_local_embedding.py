import numpy as np
import pytest

from app.services.rag_showcase.local_embedding import LocalLsaEmbedder


def _cos(a: list[float], b: list[float]) -> float:
    va, vb = np.asarray(a), np.asarray(b)
    return float(va @ vb / (np.linalg.norm(va) * np.linalg.norm(vb) + 1e-9))


def test_local_lsa_ranks_related_query_nearer():
    texts = [
        "退款审核需要 5 个工作日，审核通过后原路退回。",
        "退款到账时效：支付宝 1–2 个工作日，银行卡 3–7 个工作日。",
        "禁售商品包括电子烟、处方药和管制刀具。",
    ]
    embedder = LocalLsaEmbedder(dim=8).fit(texts)
    query = embedder.embed_query("退款审核要几天")
    docs = embedder.embed_documents(texts)
    assert 0 < len(query) <= 8
    assert len(query) == len(docs[0])
    assert _cos(query, docs[0]) > _cos(query, docs[2])


def test_local_lsa_is_deterministic_across_fits():
    texts = ["七天无理由退货", "质量问题退货运费由卖家承担", "信用分低于 60 分限制发布"]
    first = LocalLsaEmbedder(dim=6).fit(texts).embed_documents(texts)
    second = LocalLsaEmbedder(dim=6).fit(texts).embed_documents(texts)
    np.testing.assert_allclose(first, second, rtol=0, atol=1e-6)


def test_local_lsa_requires_fit_before_embed():
    with pytest.raises(RuntimeError):
        LocalLsaEmbedder().embed_query("退款")
