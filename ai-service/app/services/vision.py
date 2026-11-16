"""视觉审查服务：多模态模型读图（OpenAI 兼容 chat 接口的 image_url 内容块）。

- VISION 模型从 ai_model 表按 model_type='VISION' 解析（管理员在模型管理页配置）
- 模型未配置 / 调用失败 → 上层静默降级为纯文本审查，绝不阻塞主流程
- 图片 URL：业务库存的是 OSS 相对路径，这里统一拼 static_base_url 成可公网访问的绝对地址
"""

import logging
from typing import Dict, List, Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.config import settings
from app.services.moderation import VISION_SYSTEM_PROMPT
from app.services.product_copywriting import _extract_json

logger = logging.getLogger(__name__)

# 单次审查最多送检的图片数（控制多模态 token 成本）
MAX_IMAGES = 3


def to_absolute_urls(images: List[str]) -> List[str]:
    """相对路径拼 static 前缀；已是 http(s) 绝对地址的原样透传（纯逻辑，可单测）。"""
    base = (settings.static_base_url or "").rstrip("/")
    urls = []
    for img in images or []:
        u = str(img).strip()
        if not u:
            continue
        if u.startswith(("http://", "https://", "data:")):
            urls.append(u)
        else:
            urls.append(f"{base}/{u.lstrip('/')}" if base else u)
    return urls[:MAX_IMAGES]


def parse_vision_response(text: str) -> Optional[Dict]:
    """解析视觉模型输出 → 与文本审查同构的结论（纯逻辑，可单测）。

    qr_code/contact_info 类问题直接 high，其余 medium；match=False（图文不符）medium。
    输出不可解析返回 None。
    """
    data = _extract_json(text)
    if not data:
        return None
    issues: List[Dict] = []
    risk = "low"
    raw = data.get("image_issues") or []
    if isinstance(raw, list):
        for item in raw:
            if not isinstance(item, dict):
                continue
            detail = str(item.get("detail") or "").strip()[:200]
            itype = str(item.get("type") or "").strip()[:40]
            if not detail and not itype:
                continue
            idx = item.get("image_index")
            suffix = f"（第{int(idx) + 1}张）" if isinstance(idx, int) and idx >= 0 else ""
            if itype in ("qr_code", "contact_info"):
                risk = "high"
            elif risk != "high":
                risk = "medium"
            issues.append({
                "type": itype or "suspect",
                "detail": (detail or itype) + suffix,
            })
    if not issues and data.get("match") is False:
        # 模型没给出具体 issue 但明确说不相符
        risk = "medium"
        issues.append({"type": "mismatch", "detail": "图片内容与商品描述不相符"})
    return {"risk_level": risk, "violations": issues}


async def analyze_images(llm: ChatOpenAI, image_urls: List[str], title: str) -> Optional[Dict]:
    """多模态审查商品图，返回 {risk_level, violations}；失败/不可解析返回 None。"""
    urls = to_absolute_urls(image_urls)
    if not urls:
        return None
    content: list = [{"type": "text", "text": build_vision_user_prompt(title)}]
    for u in urls:
        content.append({"type": "image_url", "image_url": {"url": u}})
    messages = [
        SystemMessage(content=VISION_SYSTEM_PROMPT),
        HumanMessage(content=content),
    ]
    try:
        resp = await llm.ainvoke(messages)
    except Exception as e:  # noqa: BLE001 —— 图片拉取失败/模型不支持多模态等都降级
        logger.warning("vision moderation failed, degrade to text-only: %s", e)
        return None
    text = getattr(resp, "content", "")
    if isinstance(text, list):
        text = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in text
        )
    return parse_vision_response(text)


def build_vision_user_prompt(title: str) -> str:
    return f"商品标题：{title or '（无）'}\n请逐张检查以上商品实拍图。"
