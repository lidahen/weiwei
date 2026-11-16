"""商品向量服务（product_vector）单元测试：文本组装、增量 diff、路由参数校验。

FAISS / DB / embedding 依赖全部 mock，不落盘、不联网。
"""

from datetime import datetime
from unittest.mock import patch

from app.routers.recommend import CandidatesRequest, ReindexRequest
from app.services.product_vector import ProductVectorUnavailable, diff_rows, product_text


def _row(pid, title="iPhone 13", cat_l1="digital", cat_l2="phone",
         desc="95新 无拆修", price=2999.0, utime=None):
    return {
        "product_id": pid,
        "title": title,
        "cat_l1": cat_l1,
        "cat_l2": cat_l2,
        "description": desc,
        "price": price,
        "update_time": utime or datetime(2026, 9, 1, 12, 0, 0),
    }


class TestProductText:
    def test_full_text(self):
        text = product_text(_row(1))
        assert "iPhone 13" in text
        assert "类目：digital/phone" in text
        assert "95新 无拆修" in text
        assert "价格2999.0元" in text

    def test_desc_truncated(self):
        text = product_text(_row(1, desc="好" * 500))
        assert "好" * 200 in text
        assert "好" * 201 not in text

    def test_optional_fields_skipped(self):
        row = _row(1, cat_l1=None, cat_l2=None, desc="", price=None)
        text = product_text(row)
        assert text == "iPhone 13"

    def test_cat_l1_only(self):
        text = product_text(_row(1, cat_l2=None))
        assert "类目：digital" in text
        assert "phone" not in text

    def test_newline_stripped(self):
        text = product_text(_row(1, desc="第一行\n第二行"))
        assert "\n" not in text


class TestDiffRows:
    def test_all_new_when_no_cache(self):
        changed, unchanged = diff_rows([_row(1), _row(2)], {})
        assert [r["product_id"] for r in changed] == [1, 2]
        assert unchanged == []

    def test_unchanged_kept(self):
        row = _row(1)
        changed, unchanged = diff_rows([row], {1: "2026-09-01T12:00:00"})
        assert changed == []
        assert [r["product_id"] for r in unchanged] == [1]

    def test_changed_on_update_time(self):
        row = _row(1, utime=datetime(2026, 9, 5, 8, 0, 0))
        changed, unchanged = diff_rows([row], {1: "2026-09-01T12:00:00"})
        assert [r["product_id"] for r in changed] == [1]
        assert unchanged == []

    def test_removed_not_returned(self):
        """已下架商品不在 rows 中：不进 changed/unchanged，重建时自然从索引剔除。"""
        changed, unchanged = diff_rows([_row(2)],
                                       {1: "2026-09-01T12:00:00", 2: "2026-09-01T12:00:00"})
        assert changed == []
        assert [r["product_id"] for r in unchanged] == [2]


class TestReindexUnavailable:
    def test_reindex_raises_when_embedding_missing(self):
        import asyncio

        import pytest

        from app.services import product_vector as pv

        with patch("app.services.product_vector.embedding_enabled", return_value=False):
            with pytest.raises(ProductVectorUnavailable):
                asyncio.run(pv.service.reindex(1))


class TestRouterModels:
    def test_reindex_default_all_tenants(self):
        assert ReindexRequest().tenant_id is None
        assert ReindexRequest(tenant_id=2).tenant_id == 2

    def test_candidates_defaults(self):
        req = CandidatesRequest(product_ids=[1, 2])
        assert req.limit == 20
