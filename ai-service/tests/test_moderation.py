"""AI 内容初审的单元测试（不真正调用外网 LLM，全部 mock / 纯逻辑）。"""

from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from app.schemas.moderation import ModerationRequest
from app.services.moderation import (
    build_moderation_user_prompt,
    combine_verdicts,
    parse_moderation_response,
    run_text_moderation,
    text_rule_violations,
)


class TestTextRuleViolations:
    def test_clean_text_no_violation(self):
        assert text_rule_violations("iPhone 14 Pro 95新", "国行无拆修，电池效率88%") == []

    def test_banned_keyword_is_hard_hit(self):
        vs = text_rule_violations("出弹簧刀一把", "九成新")
        assert vs and vs[0]["type"] == "banned_item" and vs[0]["hard"] is True

    def test_off_platform_keyword_is_soft_hit(self):
        vs = text_rule_violations("低价转让", "详情加微信聊")
        assert vs and vs[0]["type"] == "off_platform" and vs[0]["hard"] is False

    def test_phone_number_detected(self):
        vs = text_rule_violations("转让婴儿车", "联系电话13812345678")
        assert any(v["type"] == "off_platform" for v in vs)

    def test_off_platform_only_first_keyword(self):
        vs = text_rule_violations("t", "加微信聊 加qq详谈 私下转账")
        off_platform = [v for v in vs if v["type"] == "off_platform"]
        # 关键词组命中一次 + 手机号/QQ 正则各可能一次，但关键词组不重复刷屏
        assert len([v for v in off_platform if "加" in v["detail"]]) == 1


class TestParseModerationResponse:
    def test_valid_json(self):
        v = parse_moderation_response(
            '{"risk_level": "medium", "violations": [{"type": "off_platform", "detail": "疑似引流"}], "summary": "存疑"}'
        )
        assert v["risk_level"] == "medium"
        assert v["violations"][0]["type"] == "off_platform"
        assert v["summary"] == "存疑"

    def test_fenced_json(self):
        v = parse_moderation_response('```json\n{"risk_level": "low", "violations": [], "summary": "正常"}\n```')
        assert v and v["risk_level"] == "low"

    def test_invalid_risk_rejected(self):
        assert parse_moderation_response('{"risk_level": "extreme", "violations": []}') is None

    def test_violation_without_detail_dropped(self):
        v = parse_moderation_response(
            '{"risk_level": "high", "violations": [{"type": "banned_item"}, {"detail": "有问题"}]}'
        )
        assert len(v["violations"]) == 1
        assert v["violations"][0]["type"] == "suspect"

    def test_non_json_returns_none(self):
        assert parse_moderation_response("无法解析的输出") is None


class TestCombineVerdicts:
    def test_clean_all_low(self):
        v = combine_verdicts([], {"risk_level": "low", "violations": [], "summary": "正常"}, None)
        assert v["risk_level"] == "low" and v["action"] == "auto_pass"

    def test_rule_hard_beats_llm_low(self):
        rules = [{"type": "banned_item", "detail": "违禁词", "hard": True}]
        v = combine_verdicts(rules, {"risk_level": "low", "violations": [], "summary": "正常"}, None)
        assert v["risk_level"] == "high" and v["action"] == "auto_reject"

    def test_rule_soft_floor_medium(self):
        rules = [{"type": "off_platform", "detail": "引流", "hard": False}]
        v = combine_verdicts(rules, {"risk_level": "low", "violations": [], "summary": "正常"}, None)
        assert v["risk_level"] == "medium" and v["action"] == "manual"

    def test_vision_high_wins(self):
        v = combine_verdicts(
            [],
            {"risk_level": "low", "violations": [], "summary": "文本正常"},
            {"risk_level": "high", "violations": [{"type": "qr_code", "detail": "图含二维码"}]},
        )
        assert v["risk_level"] == "high"
        assert any(i["type"] == "qr_code" for i in v["violations"])

    def test_llm_failure_with_clean_rules_still_low(self):
        v = combine_verdicts([], None, None)
        assert v["risk_level"] == "low" and v["summary"] == "未见明显违规"

    def test_violations_capped(self):
        rules = [{"type": "banned_item", "detail": f"违禁{i}", "hard": True} for i in range(20)]
        v = combine_verdicts(rules, None, None)
        assert len(v["violations"]) <= 10


class TestBuildModerationUserPrompt:
    def test_product_fields(self):
        p = build_moderation_user_prompt("iPhone", "95新", "数码/手机", 4999, "95", "product")
        assert "类型：商品" in p and "标题：iPhone" in p and "价格：4999元" in p

    def test_post_type(self):
        p = build_moderation_user_prompt("", "拼车信息", None, None, None, "post")
        assert "类型：社区帖子" in p


class TestRunTextModeration:
    @pytest.mark.asyncio
    async def test_success(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(return_value=MagicMock(
            content='{"risk_level": "medium", "violations": [], "summary": "存疑"}'))
        v = await run_text_moderation(llm, "t", "d", None, None, None, "product")
        assert v and v["risk_level"] == "medium"

    @pytest.mark.asyncio
    async def test_llm_error_propagates(self):
        llm = MagicMock()
        llm.ainvoke = AsyncMock(side_effect=RuntimeError("boom"))
        with pytest.raises(RuntimeError):
            await run_text_moderation(llm, "t", "d", None, None, None, "product")


class TestModerationRequestValidation:
    def test_biz_type_restricted(self):
        with pytest.raises(ValidationError):
            ModerationRequest(bizType="comment", title="t")

    def test_verdict_output_camel_case(self):
        """Java 任务侧读 riskLevel/modelName 驼峰字段——序列化契约回归。"""
        from app.schemas.moderation import ModerationVerdict

        v = ModerationVerdict(risk_level="low", action="auto_pass", summary="ok", model_name="m1")
        out = v.model_dump(by_alias=True)
        assert "riskLevel" in out and "modelName" in out
        assert out["riskLevel"] == "low" and out["action"] == "auto_pass"

    def test_camel_alias_and_trims(self):
        req = ModerationRequest.model_validate({
            "bizType": "product", "title": "  iPhone  ", "images": ["  ", "/a.jpg"],
        })
        assert req.biz_type == "product"
        assert req.title == "iPhone"
        assert req.images == ["/a.jpg"]

    def test_unknown_condition_ignored(self):
        req = ModerationRequest.model_validate({"bizType": "post", "title": "t", "condition": "全新"})
        assert req.condition is None

    def test_images_capped_at_5(self):
        req = ModerationRequest.model_validate({
            "bizType": "product", "title": "t", "images": [f"/{i}.jpg" for i in range(8)],
        })
        assert len(req.images) == 5
