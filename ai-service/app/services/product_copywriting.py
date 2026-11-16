"""商品文案生成服务：prompt 构建 + LLM 响应解析。

prompt 构建与 JSON 解析为纯逻辑（便于单测），LLM 调用通过 langchain ChatOpenAI。
LLM 调用失败时抛出异常，由路由层转换为友好错误；JSON 解析失败时降级为纯文本单标题。
"""

import json
import logging
import re
from typing import List, Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

logger = logging.getLogger(__name__)

MAX_TITLES = 5
MAX_DESCRIPTION_LEN = 500
MAX_TITLE_LEN = 60

COPY_SYSTEM_PROMPT = (
    "你是一个二手交易平台的商品文案专家。请根据用户提供的商品信息，生成商品发布用的标题和描述。\n"
    "要求输出一个 JSON 对象，包含两个字段：\n"
    '{"titles": ["标题1", "标题2", "标题3"], "description": "商品描述"}\n'
    "规则：\n"
    "1. titles：3-5 个候选标题，每个不超过60字，突出品类、成色、核心卖点；\n"
    "2. description：一段 100-300 字的商品描述，语气真诚自然，突出使用情况、成色与卖点，"
    "不要夸大宣传，不要编造未提供的信息；\n"
    "3. 只输出 JSON 对象，不要使用 markdown 代码块或其他解释性文字。"
)


def build_copy_user_prompt(
    product_name: str,
    category: Optional[str] = None,
    condition: Optional[str] = None,
    price: Optional[float] = None,
    keywords: Optional[str] = None,
) -> str:
    """把商品字段拼成一段用户提示词（纯逻辑，可单测）。"""
    parts = [f"商品名：{product_name}"]
    if category:
        parts.append(f"分类：{category}")
    if condition:
        parts.append(f"成色：{condition}")
    if price is not None:
        parts.append(f"售价：{price}元")
    if keywords:
        parts.append(f"补充卖点：{keywords}")
    return "\n".join(parts)


def build_image_prompt(
    product_name: str,
    category: Optional[str] = None,
    condition: Optional[str] = None,
    keywords: Optional[str] = None,
) -> str:
    """把商品字段拼成图像生成提示词（纯逻辑，可单测）。"""
    desc_parts = [product_name]
    if condition:
        desc_parts.append(condition)
    if category:
        desc_parts.append(category)
    if keywords:
        desc_parts.append(keywords)
    subject = "，".join(p for p in desc_parts if p)
    return (
        f"二手交易平台商品实拍图：{subject}。"
        "干净明亮的背景，柔和自然光，商品居中构图，细节清晰，"
        "真实感商品摄影风格，高质量，无文字水印。"
    )


def _extract_json(text: str) -> Optional[dict]:
    """从 LLM 文本中提取 JSON 对象（与 prompt_enhancer 同思路的容错解析）。"""
    if not text:
        return None
    try:
        return json.loads(text)
    except (json.JSONDecodeError, TypeError):
        pass
    fenced = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL)
    if fenced:
        try:
            return json.loads(fenced.group(1))
        except json.JSONDecodeError:
            pass
    braces = re.search(r"\{.*\}", text, re.DOTALL)
    if braces:
        try:
            return json.loads(braces.group(0))
        except json.JSONDecodeError:
            return None
    return None


def parse_copy_response(text: str) -> tuple[List[str], str]:
    """解析 LLM 文案输出，返回 (titles, description)。

    JSON 解析失败时降级：把整段文本清洗为单条标题，绝不抛异常。
    """
    data = _extract_json(text)
    if data:
        raw_titles = data.get("titles") or []
        if isinstance(raw_titles, str):
            raw_titles = [raw_titles]
        titles = [
            str(t).strip()[:MAX_TITLE_LEN]
            for t in raw_titles
            if str(t).strip()
        ][:MAX_TITLES]
        description = str(data.get("description") or "").strip()[:MAX_DESCRIPTION_LEN]
        if titles or description:
            return titles, description

    # 降级：无法解析为 JSON 时，用清洗后的纯文本作为单条标题
    fallback = (text or "").strip()
    fallback = re.sub(r"[{}\[\]\"']", "", fallback).strip()
    return ([fallback[:MAX_TITLE_LEN]] if fallback else []), ""


async def generate_product_copy(
    llm: ChatOpenAI,
    product_name: str,
    category: Optional[str] = None,
    condition: Optional[str] = None,
    price: Optional[float] = None,
    keywords: Optional[str] = None,
) -> tuple[List[str], str]:
    """调用 LLM 生成商品文案，返回 (titles, description)。LLM 调用失败时抛出异常。"""
    user_prompt = build_copy_user_prompt(
        product_name, category=category, condition=condition,
        price=price, keywords=keywords,
    )
    messages = [SystemMessage(content=COPY_SYSTEM_PROMPT), HumanMessage(content=user_prompt)]
    resp = await llm.ainvoke(messages)
    text = getattr(resp, "content", "")
    if isinstance(text, list):
        text = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in text
        )
    titles, description = parse_copy_response(text)
    if not titles and not description:
        logger.warning("product copy LLM returned empty result")
    return titles, description
