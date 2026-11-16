"""AI 社区日报的单元测试（不真正调用外网 LLM，全部 mock）。"""

from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from app.schemas.assist import DailyReportRequest
from app.services.daily_report import (
    build_report_user_prompt,
    generate_daily_report,
    parse_report_response,
)


SAMPLE_STATS = {
    "newProducts": 3,
    "topProducts": [{"title": "山地车", "views": 12}, {"title": "机械键盘", "views": 8}],
    "doneOrders": 2,
    "doneAmount": 3999.0,
    "newFeeds": 5,
    "hotFeeds": [{"summary": "周末约球", "likes": 8}],
    "runnerDoneToday": 1,
    "runnerAvgRating": 5.0,
    "runnerNewReviews": 1,
    "runnerDoneTotal": 12,
    "renewCount": 0,
}


class TestBuildReportUserPrompt:
    def test_contains_tenant_date_and_stats(self):
        prompt = build_report_user_prompt(SAMPLE_STATS, tenant_name="默认社区", date="2026-09-06")
        assert "社区名称：默认社区" in prompt
        assert "日报日期：2026-09-06" in prompt
        assert "山地车" in prompt

    def test_empty_stats_and_names(self):
        prompt = build_report_user_prompt({})
        assert "{}" in prompt
        assert "社区名称" not in prompt

    def test_no_pii_fields_injected(self):
        prompt = build_report_user_prompt(SAMPLE_STATS)
        # 提示词只包含聚合指标，不含手机号/地址字段名
        assert "phone" not in prompt.lower()
        assert "address" not in prompt.lower()


class TestParseReportResponse:
    def test_plain_json(self):
        title, content = parse_report_response(
            '{"title": "周三日报", "content": "## 一句话总览\\n今天不错"}',
            date="2026-09-06",
        )
        assert title == "周三日报"
        assert "## 一句话总览" in content

    def test_fenced_json(self):
        text = '```json\n{"title": "T", "content": "## 二手好物\\n内容"}\n```'
        title, content = parse_report_response(text)
        assert title == "T"
        assert "二手好物" in content

    def test_missing_title_falls_back_to_date(self):
        _, content = parse_report_response('{"content": "## 一句话总览\\n安静的一天"}',
                                           date="2026-09-06")
        assert content != ""
        title, _ = parse_report_response('{"content": "## 一句话总览\\n安静"}', date="2026-09-06")
        assert title == "社区日报 · 2026-09-06"

    def test_plain_text_without_structure_is_rejected(self):
        # 无 JSON 且无小节结构 → content 为空（Java 侧走模板兜底）
        title, content = parse_report_response("这不是一段结构化输出")
        assert content == ""
        assert title  # 保底标题始终有

    def test_title_length_clamped(self):
        long_title = "长" * 100
        title, _ = parse_report_response(
            '{"title": "%s", "content": "## 一句话总览\\nok"}' % long_title
        )
        assert len(title) == 60


class TestDailyReportRequestValidation:
    def test_defaults(self):
        req = DailyReportRequest()
        assert req.stats == {}
        assert req.tenant_name is None

    def test_camel_alias_accepted(self):
        req = DailyReportRequest.model_validate({"tenantName": "thu社区", "date": "2026-09-06"})
        assert req.tenant_name == "thu社区"

    def test_length_limit(self):
        with pytest.raises(ValidationError):
            DailyReportRequest.model_validate({"tenantName": "长" * 65})


class TestGenerateDailyReport:
    @pytest.mark.asyncio
    async def test_success(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(
            return_value=MagicMock(
                content='{"title": "日报", "content": "## 一句话总览\\n晴"}'
            )
        )
        title, content = await generate_daily_report(llm, SAMPLE_STATS, tenant_name="默认社区",
                                                     date="2026-09-06")
        assert title == "日报"
        assert "晴" in content
        # 提示词带上了聚合指标与社区名
        args = llm.ainvoke.await_args
        assert "默认社区" in args.args[0][1].content
        assert "山地车" in args.args[0][1].content

    @pytest.mark.asyncio
    async def test_llm_error_propagates(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(side_effect=RuntimeError("boom"))
        with pytest.raises(RuntimeError):
            await generate_daily_report(llm, SAMPLE_STATS)
