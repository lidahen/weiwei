"""AI 内容初审服务：规则过滤 + LLM 结构化审查 + 视觉审查（可选）。

分层（便于单测）：
- 纯逻辑：规则关键词命中、LLM JSON 解析、多源结论合并（本文件 + vision.py 的消息构建）
- LLM 调用：ChatOpenAI（langchain），失败抛异常由路由层转为 code=500，Java 侧跳过留人工

判定口径（与 docs/AI初审与发布助手设计.md 对齐）：
- high：违禁品（毒品/枪支/管制刀具/色情等，规则直接定级）、明显欺诈、图片二维码/联系方式
- medium：站外引流、假冒话术、夸大宣传、图文存疑
- low：正常二手/社区内容
"""

import logging
import re
from typing import Dict, List, Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.services.product_copywriting import _extract_json

logger = logging.getLogger(__name__)

RISK_ORDER = {"low": 0, "medium": 1, "high": 2}
VALID_ACTIONS = {"low": "auto_pass", "medium": "manual", "high": "auto_reject"}

# 硬违禁：命中即 high（不依赖模型，LLM 挂了也兜得住）
_BANNED_HARD = [
    "毒品", "冰毒", "大麻", "摇头丸", "迷奸", "迷药",
    "海洛因", "可卡因", "鸦片",
    "枪支", "手枪", "步枪", "弹药", "子弹", "雷管", "炸药", "气枪",
    "管制刀具", "弹簧刀", "匕首",
    "假证", "办证", "办毕业证", "代办毕业证", "证件办理", "刻章办证",
    "发票代开", "代开发票",
    "招嫖", "嫖娼", "援交", "裸聊", "约炮", "卖淫",
    "人体器官", "代孕",
    "洗钱", "跑分", "给料",
    # 高危欺诈话术：刷单/银行卡采集/虚假药品功效（评测集 M58/M59/M81，规则兜底）
    "刷单", "刷信誉", "填写银行卡", "特效药", "包治百病",
]

# 站外引流/交易迁移：命中至少 medium
_OFF_PLATFORM = [
    "加微信", "加vx", "加v信", "加qq", "加q q", "微信号", "vx号", "v信号",
    "私聊转账", "私下转账", "直接转账", "微信支付给我", "支付宝转我",
    "线下交易", "来我家交易", "点我主页链接",
]

# 假冒/夸大话术：命中至少 medium
_COUNTERFEIT = ["高仿", "复刻", "1:1正品", "一比一", "精仿", "外贸原单尾货", "专柜同款低价"]

_PHONE_RE = re.compile(r"1[3-9]\d{9}")
_QQ_RE = re.compile(r"[Qq][Qq][:：]?\s*\d{5,11}")

# 硬违禁词白名单：命中白名单的片段先剔除再匹配，防子串误杀（如"鸦片鱼"是比目鱼商品名）
_BANNED_HARD_WHITELIST = ["鸦片鱼", "鸦片鱼身"]

MODERATION_SYSTEM_PROMPT = (
    "你是社区二手交易平台的内容审核专家。请审查给定的商品/帖子信息，判断是否违规。\n"
    "输出一个 JSON 对象：\n"
    '{"risk_level": "low|medium|high", "violations": [{"type": "...", "detail": "..."}], "summary": "..."}\n'
    "判定标准：\n"
    "1. high：违禁品（毒品、枪支刀具、药品、活体动物、色情、证件发票、赃物来源不明）；"
    "明显欺诈（空手套白狼、要求先转账后发货的话术）；假冒品牌冒充正品。\n"
    "2. medium：疑似站外引流或引导私下交易；夸大宣传与实际不符；价格明显异常偏离（如远低于市场价的全新iPhone）；"
    "信息模糊难以判断真伪。\n"
    "3. low：正常个人闲置转让或正常社区内容。\n"
    "violations 每项 type 从 banned_item/off_platform/fake_brand/price_abnormal/exaggerate/suspect 中选，"
    "detail 用一句话中文说明依据（引用原文关键词）。summary 为不超过 60 字的中文结论。\n"
    "只输出 JSON 对象，不要 markdown 代码块或其他解释。"
)

VISION_SYSTEM_PROMPT = (
    "你是二手交易平台的图片审核员。逐张检查给定的商品实拍图，输出 JSON：\n"
    '{"image_issues": [{"type": "...", "detail": "...", "image_index": 0}], "match": true}\n'
    "检查项：图片中是否出现二维码、微信号/QQ/手机号等联系方式、其他平台水印或店铺水印、"
    "屏幕翻拍图（非实拍）、与「商品标题」描述明显不符的物品。\n"
    "type 从 qr_code/contact_info/watermark/resale_screen/mismatch 中选；"
    "match 表示图片内容与商品标题总体相符（无明显不符时为 true）。\n"
    "没有问题时 image_issues 为空数组。只输出 JSON 对象。"
)


def text_rule_violations(title: str, description: str) -> List[Dict]:
    """确定性规则审查（纯逻辑，可单测）。返回违规列表，可能为空。"""
    text = f"{title or ''}\n{description or ''}"
    for w in _BANNED_HARD_WHITELIST:
        text = text.replace(w, "")
    violations: List[Dict] = []
    for kw in _BANNED_HARD:
        if kw in text:
            violations.append({"type": "banned_item", "detail": f"文本出现违禁词「{kw}」", "hard": True})
    for kw in _OFF_PLATFORM:
        if kw in text:
            violations.append({"type": "off_platform", "detail": f"疑似站外引流「{kw}」", "hard": False})
            break  # 引流词命中一次即可，避免刷屏
    for kw in _COUNTERFEIT:
        if kw in text:
            violations.append({"type": "fake_brand", "detail": f"疑似假冒话术「{kw}」", "hard": False})
            break
    if _PHONE_RE.search(text):
        violations.append({"type": "off_platform", "detail": "文本包含手机号", "hard": False})
    if _QQ_RE.search(text):
        violations.append({"type": "off_platform", "detail": "文本包含 QQ 号", "hard": False})
    return violations


def parse_moderation_response(text: str) -> Optional[Dict]:
    """解析 LLM 审查输出（纯逻辑，可单测）。字段非法时逐项剔除，无法用返回 None。"""
    data = _extract_json(text)
    if not data:
        return None
    risk = str(data.get("risk_level") or "").strip().lower()
    if risk not in RISK_ORDER:
        return None
    raw_violations = data.get("violations") or []
    violations = []
    if isinstance(raw_violations, list):
        for item in raw_violations:
            if not isinstance(item, dict):
                continue
            detail = str(item.get("detail") or "").strip()[:200]
            if not detail:
                continue
            vtype = str(item.get("type") or "").strip()[:40] or "suspect"
            violations.append({"type": vtype, "detail": detail})
    summary = str(data.get("summary") or "").strip()[:120]
    return {"risk_level": risk, "violations": violations, "summary": summary}


def merge_risk(*risk_levels: Optional[str]) -> str:
    """多源风险取最高档（None 视为不参与）。纯逻辑。"""
    best = "low"
    for r in risk_levels:
        if r and r in RISK_ORDER and RISK_ORDER[r] > RISK_ORDER[best]:
            best = r
    return best


def combine_verdicts(rule_violations: List[Dict], llm_verdict: Optional[Dict],
                     vision_verdict: Optional[Dict]) -> Dict:
    """合并 规则 + LLM 文本 + 视觉 三路结论（纯逻辑，可单测）。

    - 规则命中硬违禁（hard=True）→ 直接 high；
    - 规则软命中（引流/假冒）→ 至少 medium；
    - LLM/视觉结论参与取最高档；视觉的 match=False 且无具体 issue → medium。
    """
    violations: List[Dict] = []
    hard_hit = False
    soft_hit = False
    for v in rule_violations or []:
        violations.append({"type": v["type"], "detail": v["detail"]})
        if v.get("hard"):
            hard_hit = True
        else:
            soft_hit = True

    risk_candidates: List[Optional[str]] = []
    if llm_verdict:
        violations.extend(llm_verdict.get("violations") or [])
        risk_candidates.append(llm_verdict.get("risk_level"))
    if vision_verdict:
        vision_issues = vision_verdict.get("violations") or []
        violations.extend(vision_issues)
        risk_candidates.append(vision_verdict.get("risk_level"))

    if hard_hit:
        floor = "high"
    elif soft_hit:
        floor = "medium"
    else:
        floor = None

    risk = merge_risk(floor, *risk_candidates)
    # 汇总说明：优先 LLM summary，兜底规则命中第一条
    summary = (llm_verdict or {}).get("summary") or ""
    if not summary and violations:
        summary = violations[0]["detail"]
    return {
        "risk_level": risk,
        "action": VALID_ACTIONS[risk],
        "violations": violations[:10],
        "summary": summary or "未见明显违规",
    }


def build_moderation_user_prompt(title: str, description: str, category_name: Optional[str],
                                 price: Optional[float], condition: Optional[str],
                                 biz_type: str) -> str:
    """把待审内容拼成用户提示词（纯逻辑，可单测）。"""
    kind = "商品" if biz_type == "product" else "社区帖子"
    parts = [f"类型：{kind}"]
    if title:
        parts.append(f"标题：{title}")
    if description:
        parts.append(f"内容：{description}")
    if category_name:
        parts.append(f"类目：{category_name}")
    if price is not None:
        parts.append(f"价格：{price}元")
    if condition:
        parts.append(f"成色：{condition}")
    return "\n".join(parts)


def _moderation_system_prompt() -> str:
    """初审系统提示词，支持 Skill 热加载（skills/moderation-agent/SKILL.md）。

    编辑 Markdown 即可调整判定口径，无需改代码；取不到 / 解析失败时回退内置
    MODERATION_SYSTEM_PROMPT，绝不影响主流程。
    """
    try:
        from app.services.skill_loader import get_skill_by_name

        skill_body = get_skill_by_name("moderation-agent")
        if skill_body:
            return skill_body
    except Exception as e:
        logger.warning("moderation-agent skill load failed, fallback to built-in prompt: %s", e)
    return MODERATION_SYSTEM_PROMPT


async def run_text_moderation(llm: ChatOpenAI, title: str, description: str,
                              category_name: Optional[str], price: Optional[float],
                              condition: Optional[str], biz_type: str) -> Optional[Dict]:
    """LLM 文本审查。返回解析后的结论；LLM 失败或输出不可解析返回 None（调用方降级规则结论）。"""
    user_prompt = build_moderation_user_prompt(
        title, description, category_name, price, condition, biz_type
    )
    messages = [SystemMessage(content=_moderation_system_prompt()), HumanMessage(content=user_prompt)]
    resp = await llm.ainvoke(messages)
    text = getattr(resp, "content", "")
    if isinstance(text, list):
        text = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in text
        )
    return parse_moderation_response(text)
