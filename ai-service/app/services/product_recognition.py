"""AI 识图填单服务：多模态模型从商品实拍图识别 类目/标题/描述/成色。

- 类目必须从 product_category_dict 中选择（提示词注入全量类目树，要求回 code），
  模型输出校验：code 不在字典内时回退按名称匹配，仍失败则该字段置空（前端不回填）
- 描述只允许依据图片可见信息，禁止编造购买时间/配件等不可见事实
- 纯逻辑（类目行构建/响应解析）与 LLM 调用分离，便于单测
"""

import logging
from typing import Dict, List, Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.services.vision import to_absolute_urls

logger = logging.getLogger(__name__)

CONDITION_CODES = ("brand_new", "99", "95", "90", "80", "70_lower")
CONDITION_LABELS = {"brand_new": "全新", "99": "99新", "95": "95新", "90": "9成新",
                    "80": "8成新", "70_lower": "7成及以下"}

RECOGNITION_SYSTEM_PROMPT = (
    "你是二手交易平台的发布助手。根据用户上传的商品实拍图，识别商品并生成发布信息。\n"
    "输出一个 JSON 对象：\n"
    '{"catL1Code": "一级类目code", "catL2Code": "二级类目code或null", '
    '"title": "商品标题", "description": "商品描述", "condition": "成色code"}\n'
    "规则：\n"
    "1. catL1Code/catL2Code 只能从下方给出的类目字典中选，不得编造；拿不准二级类目时置 null；\n"
    "2. title：不超过 60 字，突出品牌/型号/品名；图片可见的型号才写，看不清就写品名；\n"
    "3. description：100-300 字，只描述图片中可见的外观、成色细节与用途，"
    "不得编造未出现在图片中的配件、包装、购买渠道；\n"
    "4. condition 只能是 brand_new/99/95/90/80/70_lower 之一，按图片外观磨损判断。\n"
    "只输出 JSON 对象，不要 markdown 代码块或其他解释。\n\n"
    "类目字典（code:name，缩进表示二级类目）：\n{categories}"
)


def build_category_lines(rows: List[Dict]) -> str:
    """把 product_category_dict 行构建为提示词里的类目树文本（纯逻辑，可单测）。

    rows: [{code,name,parent_code,level}]，一级在前，二级缩进挂在父级下。
    """
    lines: List[str] = []
    children: Dict[str, List[str]] = {}
    for r in rows:
        if r.get("level") == 1:
            lines.append(f"{r['code']}:{r['name']}")
        elif r.get("level") == 2:
            children.setdefault(r.get("parent_code") or "", []).append(f"  {r['code']}:{r['name']}")
    out: List[str] = []
    for line in lines:
        out.append(line)
        out.extend(children.get(line.split(":")[0], []))
    return "\n".join(out)


def _match_category(rows: List[Dict], level: int, value: Optional[str]) -> Optional[Dict]:
    """按 code 或名称在字典中匹配类目（纯逻辑）。"""
    if not value:
        return None
    v = str(value).strip()
    for r in rows:
        if r.get("level") == level and (r.get("code") == v or r.get("name") == v):
            return r
    for r in rows:
        if r.get("level") == level and v and (v in r.get("name", "") or r.get("name", "") in v):
            return r
    return None


def parse_recognition_response(text: str, category_rows: List[Dict]) -> Dict:
    """解析视觉模型输出并校验类目/成色（纯逻辑，可单测）。非法字段一律置 None。"""
    from app.services.product_copywriting import _extract_json

    data = _extract_json(text) or {}
    l1 = _match_category(category_rows, 1, data.get("catL1Code"))
    if not l1:
        l1 = _match_category(category_rows, 1, data.get("catL1Name"))
    l2 = _match_category(category_rows, 2, data.get("catL2Code"))
    if not l2:
        l2 = _match_category(category_rows, 2, data.get("catL2Name"))
    # 二级必须属于所选一级，防止模型跨类目乱配
    if l2 and l1 and (l2.get("parent_code") != l1.get("code")):
        l2 = None

    title = str(data.get("title") or "").strip()[:60] or None
    description = str(data.get("description") or "").strip()[:500] or None
    condition = str(data.get("condition") or "").strip()
    if condition not in CONDITION_CODES:
        condition = None
    return {
        "catL1Code": l1["code"] if l1 else None,
        "catL1Name": l1["name"] if l1 else None,
        "catL2Code": l2["code"] if l2 else None,
        "catL2Name": l2["name"] if l2 else None,
        "title": title,
        "description": description,
        "condition": condition,
    }


async def run_recognition(llm: ChatOpenAI, images: List[str], category_rows: List[Dict],
                          hint: Optional[str] = None) -> Dict:
    """调用多模态模型识别商品信息。LLM 失败抛异常（路由层转 500）。"""
    urls = to_absolute_urls(images)
    content: list = [{"type": "text", "text": _user_text(hint)}]
    for u in urls:
        content.append({"type": "image_url", "image_url": {"url": u}})
    messages = [
        SystemMessage(content=RECOGNITION_SYSTEM_PROMPT.replace(
            "{categories}", build_category_lines(category_rows))),
        HumanMessage(content=content),
    ]
    resp = await llm.ainvoke(messages)
    text = getattr(resp, "content", "")
    if isinstance(text, list):
        text = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in text
        )
    return parse_recognition_response(text, category_rows)


def _user_text(hint: Optional[str]) -> str:
    text = "请根据以上商品实拍图生成发布信息。"
    if hint:
        text = f"用户补充：{hint}\n{text}"
    return text


CLASSIFY_SYSTEM_PROMPT = (
    "你是二手交易平台的类目分类器。根据商品标题（和描述），从类目字典中选出最匹配的类目。\n"
    "输出 JSON：{\"catL1Code\": \"...\", \"catL2Code\": \"...或null\"}\n"
    "code 只能从字典中选择；拿不准二级类目时 catL2Code 置 null。只输出 JSON 对象。\n\n"
    "类目字典（code:name，缩进表示二级类目）：\n{categories}"
)


async def classify_category(llm: ChatOpenAI, title: str, description: Optional[str],
                            category_rows: List[Dict]) -> Dict:
    """按标题/描述把商品分类到字典类目（估价等场景的兜底）。失败抛异常由调用方处理。"""
    text_part = f"标题：{title}"
    if description:
        text_part += f"\n描述：{description[:200]}"
    messages = [
        SystemMessage(content=CLASSIFY_SYSTEM_PROMPT.replace(
            "{categories}", build_category_lines(category_rows))),
        HumanMessage(content=text_part),
    ]
    resp = await llm.ainvoke(messages)
    text = getattr(resp, "content", "")
    if isinstance(text, list):
        text = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in text
        )
    return parse_recognition_response(text, category_rows)
