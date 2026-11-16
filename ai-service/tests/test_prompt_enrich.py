"""提示词增强服务单元测试。"""
import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from app.services import prompt_enhancer
from app.services.prompt_enhancer import EnrichmentResult


@pytest.fixture
def mock_response():
    """模拟 LLM 返回增强后的 JSON。"""
    resp = MagicMock()
    resp.content = (
        '{"prompt": "一只毛茸茸的橘猫慵懒地坐在窗台上，柔和的自然光透过纱帘洒下，超写实摄影风格", '
        '"negative_prompt": "blurry, low quality, deformed, watermark, text, signature", '
        '"style": "photorealistic"}'
    )
    return resp


@pytest.fixture
def mock_llm_class(mock_response):
    """模拟 ChatOpenAI 类。"""
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_response)
    mock_cls = MagicMock(return_value=mock_instance)
    return mock_cls, mock_instance


@pytest.mark.asyncio
async def test_short_prompt_triggers_enrich(monkeypatch, mock_llm_class):
    """开启增强时所有提示词都走增强，返回结构化结果。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
    mock_cls, mock_instance = mock_llm_class
    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("一只猫")
        assert isinstance(result, EnrichmentResult)
        assert result.is_enriched is True
        assert result.prompt != "一只猫"
        assert result.negative_prompt is not None
        assert result.style is not None
        mock_instance.ainvoke.assert_called_once()


@pytest.mark.asyncio
async def test_long_prompt_also_enrich(monkeypatch, mock_llm_class):
    """长提示词在开启增强时也走增强流程（不再按字数判断）。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
    mock_cls, mock_instance = mock_llm_class
    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("超写实水墨山水画，远山如黛，晨雾缭绕")
        assert result.is_enriched is True
        mock_instance.ainvoke.assert_called_once()


@pytest.mark.asyncio
async def test_llm_exception_fallback(monkeypatch):
    """LLM 调用异常时降级为原始 prompt。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
    mock_cls = MagicMock(side_effect=Exception("network error"))
    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("天空")
        assert result.prompt == "天空"
        assert result.is_enriched is False


@pytest.mark.asyncio
async def test_enrich_disabled(monkeypatch):
    """增强开关关闭时直接返回原始 prompt。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", False)

    result = await prompt_enhancer.enrich_prompt("一只猫")
    assert result.prompt == "一只猫"
    assert result.is_enriched is False


@pytest.mark.asyncio
async def test_no_api_key_fallback(monkeypatch):
    """API key 未配置时降级为原始 prompt。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "")

    result = await prompt_enhancer.enrich_prompt("猫")
    assert result.prompt == "猫"
    assert result.is_enriched is False


@pytest.mark.asyncio
async def test_empty_prompt(monkeypatch):
    """空 prompt 不触发增强。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    result = await prompt_enhancer.enrich_prompt("")
    assert result.prompt == ""
    assert result.is_enriched is False

    result = await prompt_enhancer.enrich_prompt("   ")
    assert result.prompt == "   "
    assert result.is_enriched is False


@pytest.mark.asyncio
async def test_llm_empty_content_fallback(monkeypatch):
    """LLM 返回空内容时降级为原始 prompt。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    mock_resp = MagicMock()
    mock_resp.content = ""
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_resp)
    mock_cls = MagicMock(return_value=mock_instance)

    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("猫")
        assert result.prompt == "猫"
        assert result.is_enriched is False
        mock_instance.ainvoke.assert_called_once()


@pytest.mark.asyncio
async def test_enrich_model_override(monkeypatch, mock_llm_class):
    """使用 ai_prompt_enhance_model 专用模型。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_model", "enhance-gpt")
    mock_cls, _mock_instance = mock_llm_class

    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        await prompt_enhancer.enrich_prompt("猫")
        mock_cls.assert_called_once()
        call_kwargs = mock_cls.call_args.kwargs
        # 构造已收敛到 model_resolver.build_chat_llm，参数名为 model_name
        assert call_kwargs["model_name"] == "enhance-gpt"
        assert call_kwargs["temperature"] == 0.7


@pytest.mark.asyncio
async def test_per_request_enabled_false_overrides_global(monkeypatch):
    """全局开启但单请求 enabled=False 时跳过增强。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    result = await prompt_enhancer.enrich_prompt("一只猫", enabled=False)
    assert result.prompt == "一只猫"
    assert result.is_enriched is False


@pytest.mark.asyncio
async def test_per_request_enabled_none_falls_back_global(monkeypatch, mock_llm_class):
    """enabled=None 时回退全局配置，正常触发增强。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
    mock_cls, mock_instance = mock_llm_class

    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("一只猫", enabled=None)
        assert result.is_enriched is True
        mock_instance.ainvoke.assert_called_once()


@pytest.mark.asyncio
async def test_json_with_markdown_code_block(monkeypatch):
    """LLM 返回 ```json 代码块时也能正确解析。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    mock_resp = MagicMock()
    mock_resp.content = '```json\n{"prompt": "enhanced", "negative_prompt": "blurry", "style": "anime"}\n```'
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_resp)
    mock_cls = MagicMock(return_value=mock_instance)

    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("test")
        assert result.is_enriched is True
        assert result.prompt == "enhanced"
        assert result.negative_prompt == "blurry"
        assert result.style == "anime"


@pytest.mark.asyncio
async def test_llm_non_json_content_fallback(monkeypatch):
    """LLM 返回纯文本（非 JSON）时将文本作为 prompt。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    mock_resp = MagicMock()
    mock_resp.content = "一只毛茸茸的猫坐在窗台上，柔和自然光，超写实摄影"
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_resp)
    mock_cls = MagicMock(return_value=mock_instance)

    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("一只猫")
        assert result.is_enriched is True
        assert result.prompt == "一只毛茸茸的猫坐在窗台上，柔和自然光，超写实摄影"
        assert result.negative_prompt is None
        assert result.style is None


@pytest.mark.asyncio
async def test_empty_negative_and_style_fields(monkeypatch):
    """LLM 返回空的 negative_prompt/style 时不填充。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    mock_resp = MagicMock()
    mock_resp.content = '{"prompt": "enhanced", "negative_prompt": "", "style": ""}'
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_resp)
    mock_cls = MagicMock(return_value=mock_instance)

    with patch("app.services.model_resolver.build_chat_llm", mock_cls):
        result = await prompt_enhancer.enrich_prompt("cat")
        assert result.is_enriched is True
        assert result.negative_prompt is None
        assert result.style is None