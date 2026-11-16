"""AI 提示词智能增强服务。

当用户开启增强模式时，调用 LLM 自动扩展提示词，并同时生成反向提示词和风格标签，
供图片/视频生成使用。增强模式不再依赖字数判断——只要开启，所有提示词都走增强流程。

异常安全：LLM 调用失败时降级为原始 prompt，绝不阻断生成流程。
"""
import json
import logging
import re
from typing import Optional

from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel

from app.config import settings
from app.services.skill_loader import get_skill_body

logger = logging.getLogger(__name__)


class EnrichmentResult(BaseModel):
    """提示词增强结果。"""

    prompt: str = ""
    negative_prompt: Optional[str] = None
    style: Optional[str] = None
    is_enriched: bool = False


SYSTEM_PROMPT = (
    "你是一个专业的 AI 绘画/视频提示词增强专家。用户会给你一个简短的描述，"
    "你需要输出一个 JSON 对象，包含三个字段：\n"
    '{"prompt": "增强后的详细提示词", "negative_prompt": "要避免的负面描述", "style": "风格标签"}\n'
    "要求：\n"
    "1. prompt：基于用户描述扩展为详尽提示词（100-300字），补充风格、光影、构图、材质、色彩、氛围等细节\n"
    "2. negative_prompt：列出常见的质量问题，用英文逗号分隔，如 blurry, low quality, deformed, watermark, text, signature, bad anatomy\n"
    "3. style：一个简洁的风格标签，如 photorealistic / anime / 3d-render / watercolor / oil-painting / cyberpunk / chinese-ink / cinematic\n"
    "4. 必须保留用户原始意图，不得偏离主题或添加无关内容\n"
    "5. 若用户描述已包含细节，不要重复，而是适当丰富\n"
    "6. 仅输出 JSON 对象，不要使用 markdown 代码块、引号包裹或其他解释性文字\n"
    "7. 如果用户描述本身就是高质量详细提示词，直接原样输出为 prompt，但仍需提供 negative_prompt 和 style"
)


def _build_system_prompt(task_type: Optional[str] = None) -> str:
    """构建增强 System Prompt：固定 JSON 输出契约 + 内置 Skill 领域知识。

    Skill 内容来自 ai-service/skills/prompt-enhance/SKILL.md，
    修改 Markdown 文件即可调整增强能力（mtime 变化自动热更新），无需改动代码。
    Skill 加载失败时降级为仅使用基础契约，行为与旧版一致。
    """
    system_prompt = SYSTEM_PROMPT
    skill_body = get_skill_body(task_type)
    if skill_body:
        system_prompt = (
            f"{system_prompt}\n\n"
            "# 提示词增强 Skill（请严格运用以下专业知识）\n\n"
            f"{skill_body}"
        )
        logger.debug(
            "Prompt enhance system prompt built with skill (task_type=%s, skill_chars=%d)",
            task_type,
            len(skill_body),
        )
    return system_prompt


def _extract_json(text: str) -> Optional[dict]:
    """从 LLM 返回的文本中提取 JSON 对象。

    尝试顺序：
    1. 直接 json.loads
    2. 提取 ```json ... ``` 或 ``` ... ``` 中的内容
    3. 提取最外层花括号
    """
    if not text:
        return None

    # 尝试直接解析
    try:
        return json.loads(text)
    except (json.JSONDecodeError, TypeError):
        pass

    # 尝试提取代码块中的 JSON
    match = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(1))
        except json.JSONDecodeError:
            pass

    # 尝试提取最外层花括号
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        try:
            return json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            pass

    return None


async def _get_enrich_llm() -> ChatOpenAI:
    """构建用于提示词增强的 LLM 实例。

    取值优先级：ai_prompt_enhance_model（显式指定）→ ai_model 表默认 LLM → settings 兜底。
    统一走 model_resolver，不再各自拼 base_url/api_key。
    """
    from app.services.model_resolver import build_chat_llm, refresh_llm_config

    await refresh_llm_config()
    return build_chat_llm(
        model_name=settings.ai_prompt_enhance_model or None,
        temperature=settings.ai_prompt_enhance_temperature,
    )


async def enrich_prompt(
    raw_prompt: str,
    *,
    enabled: Optional[bool] = None,
    task_type: str = "image",
) -> EnrichmentResult:
    """根据原始提示词进行智能增强，返回增强后的 prompt、negative_prompt、style。

    增强模式不再依赖字数判断——只要开启，所有提示词都走增强流程。
    System Prompt 由固定 JSON 输出契约 + 内置 Skill 领域知识拼接而成，
    task_type 决定注入哪个 Skill（如 image / video）。

    Args:
        raw_prompt: 用户输入的原始提示词
        enabled: 单请求级开关。为 None 时回退到全局配置
                 ai_prompt_enhance_enabled；为 False 时强制跳过增强。
        task_type: 任务类型，用于选择内置 Skill（image / video）。

    Returns:
        EnrichmentResult(prompt, negative_prompt, style, is_enriched)
        - is_enriched=True:  LLM 增强成功，返回结构化结果
        - is_enriched=False: 返回原始 prompt（未增强或增强失败降级）
    """
    # 空提示词直接返回
    if not raw_prompt or not raw_prompt.strip():
        return EnrichmentResult(prompt=raw_prompt or "")

    # 单请求级开关：False 时强制跳过；None 时回退全局配置
    request_enabled = (
        enabled if enabled is not None else settings.ai_prompt_enhance_enabled
    )
    if not request_enabled:
        logger.debug("Prompt enrichment disabled, returning raw prompt")
        return EnrichmentResult(prompt=raw_prompt)

    # API 未配置，降级
    if not settings.ai_api_key:
        logger.warning(
            "AI API key not configured, skipping prompt enrichment for: %s", raw_prompt
        )
        return EnrichmentResult(prompt=raw_prompt)

    try:
        llm = await _get_enrich_llm()
        messages = [
            SystemMessage(content=_build_system_prompt(task_type)),
            HumanMessage(content=raw_prompt),
        ]
        response = await llm.ainvoke(messages)
        content = (response.content or "").strip()

        if not content:
            logger.warning("LLM returned empty content for prompt: %s", raw_prompt)
            return EnrichmentResult(prompt=raw_prompt)

        # 解析 JSON 结构
        parsed = _extract_json(content)
        if parsed:
            enriched_prompt = (parsed.get("prompt") or "").strip()
            negative_prompt_raw = parsed.get("negative_prompt") or ""
            style_raw = parsed.get("style") or ""

            negative_prompt = negative_prompt_raw.strip() if negative_prompt_raw.strip() else None
            style = style_raw.strip() if style_raw.strip() else None

            if enriched_prompt:
                logger.info(
                    "Prompt enriched: '%s' -> prompt='%s' neg='%s' style='%s'",
                    raw_prompt[:50],
                    enriched_prompt[:80],
                    (negative_prompt or "")[:40],
                    style or "-",
                )
                return EnrichmentResult(
                    prompt=enriched_prompt,
                    negative_prompt=negative_prompt,
                    style=style,
                    is_enriched=True,
                )
            # prompt 字段为空，降级
            logger.warning("LLM returned empty prompt field for: %s", raw_prompt)
            return EnrichmentResult(prompt=raw_prompt)

        # JSON 解析失败，降级：把 LLM 返回的文本当作 prompt 使用
        logger.warning(
            "LLM returned non-JSON content for prompt '%s': %s",
            raw_prompt,
            content[:100],
        )
        return EnrichmentResult(prompt=content, is_enriched=True)

    except Exception as exc:
        logger.warning(
            "Prompt enrichment failed, falling back to raw prompt: %s (error: %s)",
            raw_prompt,
            exc,
        )
        return EnrichmentResult(prompt=raw_prompt)