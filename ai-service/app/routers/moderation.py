"""AI 内容初审路由：/ai/moderation/content。

调用方是 Java 侧 Quartz 初审任务（twelvet-product-server productAiModeration），
携带 X-Tenant-Id / X-User-Id 做配额检查与明细落账。规则命中不依赖模型；
LLM/视觉失败时返回尽力而为的结论或 code=500，Java 侧跳过该条留待人工。

判定与合并逻辑见 services/moderation.py（纯逻辑可单测）。
"""

import logging

from fastapi import APIRouter, Depends, Request
from langchain_openai import ChatOpenAI
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.moderation import ModerationRequest, ModerationVerdict
from app.schemas.common import ApiResponse
from app.services.model_resolver import resolve_default_model
from app.services.moderation import combine_verdicts, run_text_moderation, text_rule_violations
from app.services.quota_service import (
    QuotaExceededException,
    check_and_count,
    log_usage,
    tenant_id_from_request,
    user_id_from_request,
)
from app.services.vision import analyze_images

router = APIRouter(prefix="/ai/moderation", tags=["AI内容初审"])

logger = logging.getLogger(__name__)


def _build_moderation_llm(model) -> ChatOpenAI:
    """审核用 LLM：构造统一走 model_resolver（超时/重试取 settings）。

    温度压低到 0.1：审核要稳定可复现，不要"创造力"。
    """
    from app.services.model_resolver import build_chat_llm

    return build_chat_llm(model, temperature=0.1)


@router.post("/content")
async def moderate_content(request: Request, body: ModerationRequest, db: AsyncSession = Depends(get_db)):
    """审查一条商品/帖子内容，返回风险等级、违规明细与建议动作。"""
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    title = body.title or ""
    description = body.description or ""
    has_text = bool(title or description)
    if not has_text and not body.images:
        return ApiResponse(code=400, msg="标题、内容与图片不能同时为空")

    llm_model = await resolve_default_model(db, "LLM")
    if not llm_model:
        return ApiResponse(code=500, msg="未配置可用的AI模型，请联系管理员")

    llm = _build_moderation_llm(llm_model)

    # 1. 确定性规则（不依赖模型）
    rule_violations = text_rule_violations(title, description) if has_text else []

    # 2. LLM 文本审查（失败降级为仅规则结论；纯图帖跳过）
    llm_verdict = None
    if has_text:
        try:
            llm_verdict = await run_text_moderation(
                llm, title, description, body.category_name, body.price, body.condition, body.biz_type
            )
        except Exception as e:  # noqa: BLE001 —— 上游任何异常都不阻塞规则结论
            logger.warning("moderation LLM failed, fallback to rules only: %s", e)

    # 3. 视觉审查（可选：未配 VISION 模型或调用失败时跳过）
    vision_verdict = None
    if body.images:
        vision_model = await resolve_default_model(db, "VISION")
        if vision_model:
            try:
                vision_verdict = await analyze_images(
                    _build_moderation_llm(vision_model), body.images, title
                )
            except Exception as e:  # noqa: BLE001
                logger.warning("moderation vision failed, skip images: %s", e)

    verdict = combine_verdicts(rule_violations, llm_verdict, vision_verdict)

    # 事后计量：成功调用落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="moderation",
                    model_id=llm_model.model_id, model_name=llm_model.model_name, task_type="text")
    data = ModerationVerdict(
        risk_level=verdict["risk_level"],
        action=verdict["action"],
        violations=verdict["violations"],
        summary=verdict["summary"],
        model_name=llm_model.model_name,
    )
    return ApiResponse(data=data.model_dump(by_alias=True))
