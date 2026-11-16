"""AI 社区日报文案服务：prompt 构建 + LLM 响应解析。

prompt 构建与 JSON 解析为纯逻辑（便于单测，同 product_copywriting 的思路），
LLM 调用通过 langchain ChatOpenAI；调用失败抛异常，由路由层转为错误响应、
Java 代理层捕获后走模板兜底（source=fallback）。

输入只有聚合指标 JSON（不含任何用户手机号/地址等 PII），
提示词明确禁止虚构未提供的数字/标题，数据为零的小节输出「今天很安静」类一句话。
"""

import json
import logging
import re
from typing import Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

logger = logging.getLogger(__name__)

MAX_TITLE_LEN = 60
MAX_CONTENT_LEN = 3000

REPORT_SYSTEM_PROMPT = (
    "你是一个社区平台的运营小编，负责撰写每天早上发给全体居民的「社区日报」。"
    "语气轻松、有温度、不夸大，像邻里之间聊家常。\n"
    "要求输出一个 JSON 对象，包含两个字段：\n"
    '{"title": "日报标题", "content": "日报正文（Markdown）"}\n'
    "规则：\n"
    "1. title：不超过 30 字，有日期感和社区味，不要标题党；\n"
    "2. content：Markdown 正文，固定小节结构与顺序——"
    "`## 一句话总览`、`## 二手好物`（从浏览 Top 中挑 2-3 件，带商品标题，浏览量为参考不必逐条罗列数字）、"
    "`## 邻里互助`（互助完成单与好评）、`## 社区动态`（热帖）；\n"
    "3. 只能使用输入 JSON 中出现的数字与标题，严禁虚构未提供的数字、商品或帖子；"
    "数据为零或缺失的小节，输出「今天很安静」一类的一句话带过即可；\n"
    "4. 正文总长控制在 500 字以内，不要出现用户手机号、住址等隐私信息（输入里也没有）；\n"
    "5. 只输出 JSON 对象，不要使用 markdown 代码块或其他解释性文字。"
)


def build_report_user_prompt(stats: dict, tenant_name: str = "", date: str = "") -> str:
    """把聚合指标拼成用户提示词（纯逻辑，可单测）。

    stats 各 key 由 Java 侧聚合 SQL 产出（camelCase）：
    - newProducts 新上架数 / topProducts [{title, views}] 浏览Top3
    - doneOrders 成交单数 / doneAmount 成交金额
    - newFeeds 新发帖数 / hotFeeds [{summary, likes}] 点赞Top2
    - runnerDoneToday 完成单数 / runnerAvgRating 新增好评均分 / runnerNewReviews 新增好评数
    - runnerDoneTotal 累计完成总数 / renewCount 套餐变动数（可选）
    """
    lines = []
    if tenant_name:
        lines.append(f"社区名称：{tenant_name}")
    if date:
        lines.append(f"日报日期：{date}")
    lines.append("昨日聚合指标 JSON：")
    lines.append(json.dumps(stats or {}, ensure_ascii=False))
    return "\n".join(lines)


def _extract_json(text: str) -> Optional[dict]:
    """从 LLM 文本中提取 JSON 对象（与 product_copywriting 同思路的容错解析）。"""
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


def parse_report_response(text: str, date: str = "") -> tuple[str, str]:
    """解析 LLM 日报输出，返回 (title, content)。

    解析不出 content 时返回空串（由路由层判空报错 → Java 走模板兜底），
    title 缺失时用日期拼一个保底标题。
    """
    data = _extract_json(text)
    content = ""
    title = ""
    if data:
        title = str(data.get("title") or "").strip()
        content = str(data.get("content") or "").strip()
    if not content:
        # 降级：整段文本若无小节结构则视为解析失败（避免把寒暄当正文存档）
        cleaned = (text or "").strip()
        if "##" in cleaned:
            content = cleaned[:MAX_CONTENT_LEN]
    if not title:
        title = f"社区日报 · {date}" if date else "社区日报"
    return title[:MAX_TITLE_LEN], content[:MAX_CONTENT_LEN]


async def generate_daily_report(
    llm: ChatOpenAI, stats: dict, tenant_name: str = "", date: str = ""
) -> tuple[str, str]:
    """调用 LLM 生成日报，返回 (title, content)。LLM 调用失败时抛出异常。"""
    user_prompt = build_report_user_prompt(stats, tenant_name=tenant_name, date=date)
    messages = [SystemMessage(content=REPORT_SYSTEM_PROMPT), HumanMessage(content=user_prompt)]
    resp = await llm.ainvoke(messages)
    text = getattr(resp, "content", "")
    if isinstance(text, list):
        text = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in text
        )
    title, content = parse_report_response(text, date=date)
    if not content:
        logger.warning("daily report LLM returned empty content")
    return title, content
