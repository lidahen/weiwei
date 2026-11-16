"""首页小组件（天气 + 激励语）单元测试：不访问外网，缓存与 LLM 全部 mock。"""

from datetime import datetime
from unittest.mock import AsyncMock

import pytest

from app.services import home_widget
from app.services.home_widget import (
    QUOTE_COUNT,
    build_quote_user_prompt,
    clean_city,
    describe_weather_code,
    fallback_quotes,
    get_quotes,
    get_weather,
    now_cn,
    parse_quotes_response,
)
from app.services.quota_service import QuotaExceededException


class TestDescribeWeatherCode:
    def test_sunny(self):
        text, icon = describe_weather_code(0)
        assert text == "晴"
        assert icon == "☀️"

    def test_rain(self):
        text, _ = describe_weather_code(63)
        assert text == "中雨"

    def test_thunder(self):
        text, icon = describe_weather_code(95)
        assert text == "雷阵雨"
        assert icon == "⛈️"

    def test_none_falls_back(self):
        text, icon = describe_weather_code(None)
        assert text == "多云"
        assert icon == "⛅"

    def test_unknown_code_falls_back(self):
        text, _ = describe_weather_code(42)
        assert text == "多云"


class TestCleanCity:
    def test_strip_shi_suffix(self):
        assert clean_city("深圳市") == "深圳"

    def test_strip_whitespace(self):
        assert clean_city("  北京 ") == "北京"

    def test_empty_falls_back_to_default(self):
        assert clean_city("") == "深圳"
        assert clean_city(None) == "深圳"

    def test_single_char_not_stripped(self):
        assert clean_city("市") == "市"

    def test_long_name_truncated(self):
        assert len(clean_city("a" * 100)) <= 30


class TestFallbackQuotes:
    def test_returns_five(self):
        assert len(fallback_quotes()) == QUOTE_COUNT

    def test_same_day_stable(self):
        day = datetime(2026, 9, 10)
        assert fallback_quotes(day) == fallback_quotes(day)

    def test_differs_across_days(self):
        day1 = datetime(2026, 9, 10)
        day2 = datetime(2026, 9, 11)
        assert fallback_quotes(day1) != fallback_quotes(day2)

    def test_quotes_from_pool(self):
        pool = set(home_widget.FALLBACK_QUOTES)
        assert all(q in pool for q in fallback_quotes())


class TestParseQuotesResponse:
    def test_plain_json(self):
        quotes = parse_quotes_response(
            '{"quotes": ["旧物新生", "闲置不闲", "好物流转", "邻里同温", "生活减负"]}'
        )
        assert quotes == ["旧物新生", "闲置不闲", "好物流转", "邻里同温", "生活减负"]

    def test_fenced_json(self):
        text = '```json\n{"quotes": ["第一条", "第二条"]}\n```'
        quotes = parse_quotes_response(text)
        assert quotes[:2] == ["第一条", "第二条"]
        assert len(quotes) == QUOTE_COUNT  # 不足 5 条自动补齐

    def test_line_fallback(self):
        text = "1. 旧物有故事\n2. 闲置有归途\n3. 心动常新"
        quotes = parse_quotes_response(text)
        assert quotes[0] == "1. 旧物有故事"[:30] or "旧物有故事" in quotes[0]
        assert len(quotes) == QUOTE_COUNT

    def test_empty_response_returns_empty(self):
        # 空响应由 get_quotes 决定降级模板池（单一职责），解析层仅返回空列表
        assert parse_quotes_response("") == []

    @pytest.mark.asyncio
    async def test_blank_llm_output_falls_back(self, monkeypatch):
        # 服务层：LLM 输出为空 → 降级模板池，不缓存
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=None))
        monkeypatch.setattr(home_widget, "_cache_set", AsyncMock())
        llm = AsyncMock()
        llm.ainvoke.return_value = AsyncMock(content="   ", model_name="m")
        result = await get_quotes(tenant_id=1, llm=llm)
        assert result["source"] == "template"
        assert len(result["quotes"]) == QUOTE_COUNT

    def test_dedup_and_truncate(self):
        text = '{"quotes": ["重复", "重复", "第三条", "第四条", "第五条", "第六条"]}'
        quotes = parse_quotes_response(text)
        assert len(quotes) == QUOTE_COUNT
        assert len(set(quotes)) == QUOTE_COUNT


class TestBuildQuoteUserPrompt:
    def test_contains_date_city_weather(self):
        day = datetime(2026, 9, 10)
        prompt = build_quote_user_prompt("深圳市", {"text": "多云", "temp": 30.1}, day=day)
        assert "9月10日" in prompt
        assert "星期四" in prompt
        assert "深圳" in prompt
        assert "多云" in prompt
        assert "30.1" in prompt

    def test_without_weather(self):
        day = datetime(2026, 9, 10)
        prompt = build_quote_user_prompt("广州", None, day=day)
        assert "广州" in prompt
        assert "天气" not in prompt


class TestGetQuotes:
    @pytest.mark.asyncio
    async def test_guest_uses_template(self, monkeypatch):
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=None))
        result = await get_quotes(tenant_id=0, llm=None)
        assert result["source"] == "template"
        assert len(result["quotes"]) == QUOTE_COUNT

    @pytest.mark.asyncio
    async def test_cache_hit_skips_llm(self, monkeypatch):
        cached = ["缓存条目一", "缓存条目二", "缓存条目三", "缓存条目四", "缓存条目五"]
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=cached))
        llm = AsyncMock()
        result = await get_quotes(tenant_id=1, llm=llm)
        assert result["source"] == "cache"
        assert result["quotes"] == cached
        llm.ainvoke.assert_not_called()

    @pytest.mark.asyncio
    async def test_llm_success_saves_cache(self, monkeypatch):
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=None))
        saved = {}
        async def fake_set(key, value, ttl):
            saved[key] = value

        monkeypatch.setattr(home_widget, "_cache_set", fake_set)

        llm = AsyncMock()
        llm.ainvoke.return_value = AsyncMock(
            content='{"quotes": ["一", "二", "三", "四", "五"]}', model_name="test-model"
        )
        result = await get_quotes(
            tenant_id=1, llm=llm,
            log_usage_fn=AsyncMock(),
        )
        assert result["source"] == "ai"
        assert result["quotes"] == ["一", "二", "三", "四", "五"]
        assert len(saved) == 1
        assert saved[list(saved.keys())[0]] == result["quotes"]

    @pytest.mark.asyncio
    async def test_llm_failure_falls_back(self, monkeypatch):
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=None))
        llm = AsyncMock()
        llm.ainvoke.side_effect = RuntimeError("llm down")
        result = await get_quotes(tenant_id=1, llm=llm)
        assert result["source"] == "template"
        assert len(result["quotes"]) == QUOTE_COUNT

    @pytest.mark.asyncio
    async def test_quota_exceeded_falls_back(self, monkeypatch):
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=None))

        async def quota_fail():
            raise QuotaExceededException("今日AI调用次数已用完")

        result = await get_quotes(tenant_id=1, llm=AsyncMock(), quota_check=quota_fail)
        assert result["source"] == "template"
        assert len(result["quotes"]) == QUOTE_COUNT


class TestGetWeather:
    @pytest.mark.asyncio
    async def test_cache_hit_no_external_call(self, monkeypatch):
        cached = {"city": "深圳", "text": "多云", "icon": "⛅", "temp": 30.1}
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=cached))
        result = await get_weather("深圳市")
        assert result == cached

    @pytest.mark.asyncio
    async def test_geocode_failure_returns_none(self, monkeypatch):
        monkeypatch.setattr(home_widget, "_cache_get", AsyncMock(return_value=None))

        class FakeClient:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return False

            async def get(self, url, params=None):
                raise RuntimeError("network down")

        class FakeAsyncClient:
            def __init__(self, *args, **kwargs):
                self._client = FakeClient()

            async def __aenter__(self):
                return self._client

            async def __aexit__(self, *args):
                return False

        monkeypatch.setattr(home_widget.httpx, "AsyncClient", FakeAsyncClient)
        result = await get_weather("深圳")
        assert result is None


class TestNowCn:
    def test_fixed_utc8(self):
        current = now_cn()
        assert current.utcoffset().total_seconds() == 8 * 3600
