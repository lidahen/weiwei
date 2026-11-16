"""AI 商品文案 / 配图辅助的单元测试（不真正调用外网 LLM，全部 mock）。"""

from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from app.schemas.assist import ProductCopyRequest
from app.services.product_copywriting import (
    build_copy_user_prompt,
    build_image_prompt,
    generate_product_copy,
    parse_copy_response,
)


class TestBuildCopyUserPrompt:
    def test_all_fields(self):
        prompt = build_copy_user_prompt(
            "iPhone 14 Pro", category="手机", condition="95新",
            price=3999.0, keywords="国行无拆修",
        )
        assert "商品名：iPhone 14 Pro" in prompt
        assert "分类：手机" in prompt
        assert "成色：95新" in prompt
        assert "售价：3999.0元" in prompt
        assert "补充卖点：国行无拆修" in prompt

    def test_optional_fields_omitted(self):
        prompt = build_copy_user_prompt("自行车")
        assert "商品名：自行车" in prompt
        assert "分类" not in prompt
        assert "售价" not in prompt


class TestBuildImagePrompt:
    def test_contains_subject_and_style(self):
        prompt = build_image_prompt("iPhone 14 Pro", category="手机", condition="95新")
        assert "iPhone 14 Pro" in prompt
        assert "手机" in prompt
        assert "95新" in prompt
        assert "商品实拍图" in prompt

    def test_keywords_appended(self):
        prompt = build_image_prompt("山地车", keywords="碳纤维车架")
        assert "碳纤维车架" in prompt


class TestParseCopyResponse:
    def test_plain_json(self):
        titles, desc = parse_copy_response(
            '{"titles": ["标题A", "标题B"], "description": "描述内容"}'
        )
        assert titles == ["标题A", "标题B"]
        assert desc == "描述内容"

    def test_fenced_json(self):
        text = '```json\n{"titles": ["标题A"], "description": "描述"}\n```'
        titles, desc = parse_copy_response(text)
        assert titles == ["标题A"]
        assert desc == "描述"

    def test_titles_string_normalized(self):
        titles, _ = parse_copy_response('{"titles": "单标题", "description": ""}')
        assert titles == ["单标题"]

    def test_fallback_plain_text(self):
        titles, desc = parse_copy_response("这不是JSON输出的一段话")
        assert titles == ["这不是JSON输出的一段话"]
        assert desc == ""

    def test_title_length_clamped(self):
        long_title = "长" * 100
        titles, _ = parse_copy_response(
            '{"titles": ["%s"], "description": ""}' % long_title
        )
        assert len(titles[0]) == 60


class TestProductCopyRequestValidation:
    def test_product_name_required(self):
        with pytest.raises(ValidationError):
            ProductCopyRequest(product_name="  ")

    def test_product_name_max_length(self):
        with pytest.raises(ValidationError):
            ProductCopyRequest(product_name="长" * 61)

    def test_camel_alias_accepted(self):
        req = ProductCopyRequest.model_validate({"productName": "iPhone"})
        assert req.product_name == "iPhone"


class TestGenerateProductCopy:
    @pytest.mark.asyncio
    async def test_success(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(
            return_value=MagicMock(
                content='{"titles": ["T1", "T2"], "description": "D"}'
            )
        )
        titles, desc = await generate_product_copy(llm, "商品X")
        assert titles == ["T1", "T2"]
        assert desc == "D"

    @pytest.mark.asyncio
    async def test_llm_error_propagates(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(side_effect=RuntimeError("boom"))
        with pytest.raises(RuntimeError):
            await generate_product_copy(llm, "商品X")
