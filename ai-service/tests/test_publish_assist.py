"""AI 估价与识图填单的单元测试（纯逻辑 + mock LLM，不连外网/不连库）。"""

from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from app.schemas.assist import (
    ProductRecognitionRequest,
    ProductValuationRequest,
    ProductValuationData,
)
from app.services.product_recognition import (
    build_category_lines,
    classify_category,
    parse_recognition_response,
)
from app.services.valuation import (
    compute_valuation,
    condition_factor,
    percentile,
    split_l2_samples,
)

CATEGORY_ROWS = [
    {"code": "digital", "name": "数码", "parent_code": None, "level": 1},
    {"code": "phone", "name": "手机", "parent_code": "digital", "level": 2},
    {"code": "laptop", "name": "电脑", "parent_code": "digital", "level": 2},
    {"code": "life", "name": "生活用品", "parent_code": None, "level": 1},
    {"code": "bike", "name": "自行车", "parent_code": "life", "level": 2},
]


class TestPercentile:
    def test_empty(self):
        assert percentile([], 50) is None

    def test_single(self):
        assert percentile([10.0], 25) == 10.0

    def test_interpolation(self):
        assert percentile([10.0, 20.0], 50) == 15.0

    def test_quartiles_sorted(self):
        vals = sorted([1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0])
        p25 = percentile(vals, 25)
        p75 = percentile(vals, 75)
        assert p25 < 4 and p75 > 7


class TestConditionFactor:
    def test_known_values(self):
        assert condition_factor("brand_new") == 1.15
        assert condition_factor("95") == 1.0
        assert condition_factor("70_lower") == 0.6

    def test_unknown_falls_back_to_90(self):
        assert condition_factor(None) == 0.92
        assert condition_factor("全新") == 0.92


class TestComputeValuation:
    def test_with_transactions(self):
        r = compute_valuation([100.0] * 5 + [200.0] * 5, [150.0], "95")
        assert r["sample_count"] == 10
        assert r["source"] == "transaction_stats"
        assert r["p25"] <= r["median"] <= r["p75"]
        assert r["price_low"] <= r["price_high"]
        # 95新系数 1.0，中位数应为 150
        assert r["median"] == 150.0
        assert r["suggestion"] == 150.0
        assert "10 件" in r["explanation"]

    def test_condition_adjusts_range(self):
        base = compute_valuation([100.0] * 10, [], "95")
        brand_new = compute_valuation([100.0] * 10, [], "brand_new")
        assert brand_new["suggestion"] > base["suggestion"]

    def test_no_transactions_falls_back_to_onsale(self):
        r = compute_valuation([], [100.0, 120.0, 140.0, 160.0, 180.0], "90")
        assert r["sample_count"] == 0
        assert r["source"] == "onsale_reference"
        assert r["price_low"] < r["price_high"]
        assert "暂无成交" in r["explanation"]

    def test_no_data_at_all(self):
        r = compute_valuation([], [], "95")
        assert r["sample_count"] == 0
        assert r["price_low"] is None
        assert "暂无成交" in r["explanation"] and "在售" in r["explanation"]

    def test_invalid_prices_filtered(self):
        r = compute_valuation([0, -5, 100.0, 100.0, 100.0, 100.0, 100.0], [], "95")
        assert r["sample_count"] == 5

    def test_rounding_above_100(self):
        r = compute_valuation([3333.0] * 10, [], "95")
        assert r["median"] == 3333.0


class TestSplitL2Samples:
    def test_by_l2(self):
        rows = [{"price": 100.0, "cat_l2": "phone"}, {"price": 200.0, "cat_l2": "laptop"},
                {"price": 300.0, "cat_l2": "phone"}]
        l2, rest = split_l2_samples(rows, "phone")
        assert l2 == [100.0, 300.0] and rest == [200.0]

    def test_no_l2_returns_all_in_rest(self):
        rows = [{"price": 100.0, "cat_l2": "phone"}]
        l2, rest = split_l2_samples(rows, None)
        assert l2 == [] and rest == [100.0]


class TestBuildCategoryLines:
    def test_children_indented_under_parent(self):
        text = build_category_lines(CATEGORY_ROWS)
        lines = text.split("\n")
        assert lines[0] == "digital:数码"
        assert "  phone:手机" in lines
        # 二级类目必须出现在其父级之后
        assert lines.index("digital:数码") < lines.index("  phone:手机")
        assert lines.index("life:生活用品") < lines.index("  bike:自行车")


class TestParseRecognitionResponse:
    def test_codes_valid(self):
        r = parse_recognition_response(
            '{"catL1Code": "digital", "catL2Code": "phone", "title": "iPhone 14", '
            '"description": "95新", "condition": "95"}',
            CATEGORY_ROWS,
        )
        assert r["catL1Code"] == "digital" and r["catL2Code"] == "phone"
        assert r["condition"] == "95"

    def test_invalid_code_falls_back_to_name(self):
        r = parse_recognition_response(
            '{"catL1Code": "unknown", "catL2Code": null, "catL1Name": "数码", "title": "x"}',
            CATEGORY_ROWS,
        )
        assert r["catL1Code"] == "digital"

    def test_l2_not_under_l1_rejected(self):
        r = parse_recognition_response(
            '{"catL1Code": "digital", "catL2Code": "bike", "title": "x"}',
            CATEGORY_ROWS,
        )
        assert r["catL2Code"] is None

    def test_condition_invalid_rejected(self):
        r = parse_recognition_response('{"title": "x", "condition": "全新"}', CATEGORY_ROWS)
        assert r["condition"] is None

    def test_markdown_fenced_json(self):
        r = parse_recognition_response(
            '```json\n{"catL1Code": "digital", "title": "x"}\n```', CATEGORY_ROWS
        )
        assert r["catL1Code"] == "digital"


class TestClassifyCategory:
    @pytest.mark.asyncio
    async def test_success(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(return_value=MagicMock(
            content='{"catL1Code": "digital", "catL2Code": "laptop"}'))
        r = await classify_category(llm, "ThinkPad X1 95新", None, CATEGORY_ROWS)
        assert r["catL1Code"] == "digital" and r["catL2Code"] == "laptop"

    @pytest.mark.asyncio
    async def test_llm_error_propagates(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(side_effect=RuntimeError("boom"))
        with pytest.raises(RuntimeError):
            await classify_category(llm, "x", None, CATEGORY_ROWS)


class TestValuationRequestValidation:
    def test_title_required(self):
        with pytest.raises(Exception):
            ProductValuationRequest(title="  ")

    def test_camel_alias(self):
        req = ProductValuationRequest.model_validate({
            "title": "iPhone", "catL1": "digital", "catL2": "phone", "condition": "95",
        })
        assert req.cat_l1 == "digital"

    def test_data_camel_output(self):
        d = ProductValuationData(sample_count=3, price_low=100.0, price_high=200.0)
        out = d.model_dump(by_alias=True)
        assert out["sampleCount"] == 3 and out["priceLow"] == 100.0


class TestRecognitionRequestValidation:
    def test_images_required(self):
        with pytest.raises(ValidationError):
            ProductRecognitionRequest(images=[])

    def test_images_capped(self):
        with pytest.raises(ValidationError):
            ProductRecognitionRequest(images=[f"/{i}.jpg" for i in range(6)])

    def test_camel_alias(self):
        req = ProductRecognitionRequest.model_validate({"images": ["/a.jpg"], "userId": "1"})
        assert req.user_id == "1"
