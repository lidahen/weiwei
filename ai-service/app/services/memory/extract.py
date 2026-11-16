"""长期记忆的提取层：规则层（纯函数）+ 异步编排（run_extract / schedule_extract）。

前半部分是**纯函数规则层**——"给定文本与 trace，产出可落库的结构"，把安全边界钉在单测里；
后半部分是**编排层**：开会话、调一次提取 LLM、经 `store` UPSERT 落库、淘汰溢出行，
并以 asyncio 后台任务运行，**绝不阻塞本轮回复**（spec §6.1 / §6.5）。

四条贯穿全模块的口径（spec §6.2 / §6.4 / §10）：

- **对用户静默、对日志不静默**：每个丢弃分支都留一条可 grep 的 `logger.warning`，
  且只打类型/参数名/命中种类，**不打用户原文与 PII 原文**（日志本身不能成为泄漏点）。
- **白名单是唯一防线**：`mem_key` 必须在 `MEM_KEYS` 内，行为证据只认
  `_BEHAVIOR_PARAM_WHITELIST` 里的参数名——`params` 里混着 `keyword` 搜索词（可能含
  姓名/地址）与被注入的 `user_id`/`tenant_id`。
- **脏值只降级不抛异常**：提取跑在后台任务里，异常只会变成一条 warning，记忆会静默
  停摆；所以数值转不了就跳过该条、文本一律 `clamp_text`、置信度一律夹取。
"""
import asyncio
import json
import logging
import math
from collections.abc import Mapping, Sequence
from typing import Any, Optional

from langchain_core.messages import HumanMessage

from app.config import settings
from app.database import async_session
from app.services.memory import store
from app.services.memory.constants import (
    MAX_EVIDENCE_CHARS,
    MAX_VALUE_CHARS,
    SCOPE_GLOBAL,
)
from app.services.memory.logic import clamp_text, detect_pii, is_memory_key

logger = logging.getLogger(__name__)

# ── 提取提示词（spec §6.2 定稿，逐字照抄；改动必须与设计文档同步）────────────────
# ⚠️ 占位符只能 replace：提示词正文含 JSON 字面大括号（输出示例），
#    用 str.format() 会抛 KeyError('"memories"')。
EXTRACT_PROMPT = """你是二手交易平台的用户记忆提取器。阅读下面这段对话，只提取「长期有价值、
下次新会话仍然应该记住」的用户偏好与事实。

## 可用的 key（只能从下表选，不得自造）
预算上限 / 预算区间 / 类目偏好 / 类目排除 / 属性偏好 / 成色要求 /
硬性条件 / 常驻城市 / 稳定身份 / 交易习惯 / 明确拒绝 / 决策阶段

## 值得记
- 用户对价格、类目、品牌、成色、尺寸、功能、地点的长期约束
- 用户的稳定事实（常住城市、家庭成员、职业、正在准备的事）
- 用户的交易习惯（自提还是邮寄、是否议价、对瑕疵的容忍度）
- 用户明确说"不要/别给我看"的东西
- 用户正在比较的选择、已表达倾向

## 绝对不要提取
- 一次性询价、寒暄、情绪化表达（"这个多少钱"、"气死了"）
- 订单号、物流单号、优惠券码（这些会过期，工具能实时查）
- 手机号、身份证号、银行卡号、精确门牌地址
- 只对当前一轮有效的临时语境（"我明天要用"）

## 判断规则
1. 只记用户明确说过的，不推断。evidence 必须能摘出原话片段（≤100 字）。
2. 必须区分「用户本人的偏好」与「一次性场景」：帮朋友买、临时急用等
   不计入本人偏好。
3. 同一 key 在同一段对话出现多次，合并为一条，value 取最具体的一次。
4. 拿不准就不输出。宁缺勿滥。
5. confidence：用户明确陈述 0.8~0.9；从约束间接体现 0.6~0.7；语气含糊 ≤0.5。

## 输入格式
对话（最近若干条，含角色）：
{history}

## 输出（严格 JSON，无内容输出 {"memories":[]}）
{"memories":[{"key":"预算上限","value":"50元以内","evidence":"我预算就50以内",
  "confidence":0.9,"scope":"global"}]}"""

# 对话消息的角色映射：LangChain 消息用 `.type`（human/ai/system/tool），
# 前端上送的是 ChatMsg（.role）或 dict（{"role","content"}），三种都要认。
_LC_TYPE_ROLES: Mapping[str, str] = {"human": "user", "ai": "assistant"}
_ROLE_USER = "user"
_ROLE_ASSISTANT = "assistant"

# 渲染成文本时的角色前缀（提示词里的"含角色"格式）
_ROLE_LABELS: Mapping[str, str] = {_ROLE_USER: "用户", _ROLE_ASSISTANT: "助手"}

# 输入侧的截断长度：普通消息 200 字，本轮助手回复 300 字（spec §6.4.2）
_INPUT_MSG_CHARS = 200
_INPUT_REPLY_CHARS = 300

# ── 解析守卫：置信度与 scope ──
_CONFIDENCE_FLOOR = 0.1
_CONFIDENCE_CEILING = 0.95
_CONFIDENCE_DEFAULT = 0.6
# scope 列是 VARCHAR(64)，且必须压平（值里带换行能伪造注入块的 bullet 结构）
_SCOPE_CHARS = 64
_CATEGORY_PREFIX = "category:"

# ── 行为证据（spec §6.4）──
# 参数名白名单，必须与 `nodes._TRACE_PARAM_WHITELIST` 保持一致（有漂移测试钉住）：
# trace 侧按这份名单落 params，提取侧按同一份名单取值，两边不一致就会静默丢证据。
# 按**参数名**而非工具名过滤：estimate_price / recommend_products 也带 cat_l1 / condition。
_BEHAVIOR_PARAM_WHITELIST: tuple[str, ...] = (
    "order_by", "price_min", "price_max", "condition", "cat_l1", "city",
)
# order_by 只有这一个取值含行为信号（其余取值如 price_desc 不产出证据）
_ORDER_BY_FACTS: Mapping[str, tuple[str, str, float]] = {
    "price_asc": ("交易习惯", "倾向按价格从低到高筛选", 0.5),
}
# 文本类参数 → (mem_key, confidence)；cat_l1 的 scope 特殊（恒 category:<值>）
_TEXT_PARAM_FACTS: Mapping[str, tuple[str, float]] = {
    "condition": ("成色要求", 0.7),
    "cat_l1": ("类目偏好", 0.6),
    "city": ("常驻城市", 0.7),
}


def should_extract(
    *,
    enabled: bool,
    user_id: str,
    resume: Optional[str],
    interrupted: bool,
    error: str,
    last_user_text: str,
) -> bool:
    """本轮值不值得跑提取（spec §6.1 的跳过条件，纯判断、无副作用）。

    放在调度之前是为了"零开销跳过"：为一句"谢谢"起一次 LLM 调用既不划算，
    也会把寒暄喂进画像。任一条件不满足即 False。
    """
    if not enabled:
        return False
    raw_user = str(user_id or "").strip()
    # 与读路径同一口径（nodes.memory_recall_node）："" / 非数字 / "0" 都不可归属，
    # 放行等于让所有未登录访客共用一份画像
    if not raw_user.isdigit() or raw_user == "0":
        return False
    # 空白的 resume / error 表示"本轮没有这两件事"，不是确认轮 / 不是失败轮：
    # 若按 `is not None` 判，前端上送 resume="" 就会静默停掉整条写路径。
    if str(resume or "").strip() or interrupted:
        # 确认轮（L2/L3 令牌流程）与中断轮：说的是"要不要下单"，不是长期偏好
        return False
    if str(error or "").strip():
        return False
    return len(str(last_user_text or "").strip()) >= int(settings.agent_memory_extract_min_chars)


def _role_of(msg: Any) -> str:
    """把 LangChain 消息 / ChatMsg / dict 统一成 "user" / "assistant" / 其他。"""
    role = getattr(msg, "role", None)
    if not role:
        # LangChain 消息没有 role 属性，用 .type（human/ai/system/tool）
        role = _LC_TYPE_ROLES.get(str(getattr(msg, "type", "") or ""), "")
    if not role and isinstance(msg, Mapping):
        role = str(msg.get("role") or "")
    return str(role or "").strip().lower()


def _content_of(msg: Any) -> str:
    content = msg.get("content") if isinstance(msg, Mapping) else getattr(msg, "content", None)
    if content is None:
        return ""
    # content 可能是多模态块列表（[{"type":"text","text":...}]）：str() 兜底后交给
    # clamp_text 压平，规则层不需要解析结构
    return content if isinstance(content, str) else str(content)


def build_extract_input(messages: Sequence, final_reply: str, max_msgs: int = 6) -> str:
    """拼提取输入：最近若干条对话 + 本轮助手回复（spec §6.2 / §6.4.2）。

    - 只保留 user / assistant：SystemMessage（人设 / RAG 片段 / 记忆块）一律排除，
      否则记忆会被当作用户原话重新提取，形成自我强化；
    - 本轮助手回复不在前端上送的 messages 里（它只在 `result["final_reply"]`），
      必须显式补上，否则"就这个吧"这类依赖上文确认的话是孤立短句；助手内容仅用于
      消解指代——提示词已规定"只记**用户**明确说过的"；
    - 总量 ≤ max_msgs：历史取最后 `max_msgs - 1` 条，末尾那条是本轮助手回复。
    """
    limit = max(1, int(max_msgs))
    turns: list[str] = []
    for msg in messages or []:
        label = _ROLE_LABELS.get(_role_of(msg))
        if label is None:
            continue
        text = clamp_text(_content_of(msg), _INPUT_MSG_CHARS)
        if not text:
            continue
        turns.append(f"{label}：{text}")

    kept = turns[-(limit - 1):] if limit > 1 else []
    reply = clamp_text(final_reply, _INPUT_REPLY_CHARS)
    if reply:
        kept.append(f"{_ROLE_LABELS[_ROLE_ASSISTANT]}：{reply}")
    return "\n".join(kept)


def _parse_json(raw: str) -> Optional[Any]:
    """剥 ```json 围栏 + 解析；任何失败都返回 None 并留痕（调用方据此返回 []）。"""
    text = str(raw or "").strip()
    if not text:
        logger.warning("memory extract parse failed: empty_output")
        return None
    if text.startswith("```"):
        # 模型常把 JSON 包进 markdown 围栏；第一行是 ```json / ```，最后一行是 ```
        lines = text.splitlines()
        if len(lines) < 2:
            logger.warning("memory extract parse failed: unterminated_fence")
            return None
        if lines[-1].strip().startswith("```"):
            lines = lines[1:-1]
        else:
            lines = lines[1:]
        text = "\n".join(lines).strip()
    try:
        return json.loads(text)
    except (TypeError, ValueError) as e:
        # 只打异常类型：原文可能整段是用户对话
        logger.warning("memory extract parse failed: %s", type(e).__name__)
        return None


def _clamp_confidence(value: Any) -> float:
    """置信度夹到 [0.1, 0.95]；缺失/脏值走默认 0.6。

    上限 0.95 是给衰减留余量（DECIMAL(3,2) 存 1.00 时衰减一次仍接近满值，
    等于把模型的一次判断当成不可动摇的事实）；下限 0.1 保证不会因 0 值被
    `agent_memory_min_confidence` 永久挡在注入块之外而显得"提取成功却没记忆"。
    """
    try:
        conf = float(value)
    except (TypeError, ValueError):
        return _CONFIDENCE_DEFAULT
    if not math.isfinite(conf):
        return _CONFIDENCE_DEFAULT
    return min(_CONFIDENCE_CEILING, max(_CONFIDENCE_FLOOR, conf))


def _scrubbed_key_hint(key: str) -> str:
    """PII 命中时给日志用的安全替身：只说"什么样的 key"，绝不复述原文。

    未知 key 是模型自造、源头是用户对话的文本（"住朝阳3号楼2单元501室"）；
    条目被丢弃不代表安全 —— 丢弃日志若原样打印 key，日志本身就成了泄漏点
    （截断到 32 字符仍然装得下 11 位手机号）。
    """
    return f"unknown_key(len={len(key)})"


def _clamp_scope(value: Any, mem_key: str = "") -> str:
    """scope 只认 global 与 category:<一级类目>；其余（含空类目）一律回退 global。

    类目名同样是从对话里回填的用户可控文本（spec §10.1 的守卫不能只盯 value/evidence）：
    命中 PII 就整值作废、回落 global —— 条目本身（value/evidence 干净）照常保留，
    但绝不带着可疑类目入表。与行为侧 `cat_l1` 的前置判定同一口径。
    """
    scope = clamp_text(value, _SCOPE_CHARS)
    if scope.startswith(_CATEGORY_PREFIX) and len(scope) > len(_CATEGORY_PREFIX):
        if detect_pii(scope) is not None:
            # 条目本身（value/evidence 干净）保留，只是类目作废
            logger.warning("memory scope fallback: pii in scope key=%s", mem_key)
            return SCOPE_GLOBAL
        return scope
    return SCOPE_GLOBAL


def parse_memories(raw: str) -> list[dict]:
    """把提取器的 LLM 输出解析成可落库的事实列表；不可用的一律丢弃并留痕。

    字段映射（提示词的输出格式 → 本模块与 store 的内部契约）：
    `key`→`mem_key`、`value`→`mem_value`、`evidence`→`evidence_text`，
    外加 `confidence` / `scope`。直接按 `mem_key` 读 LLM 输出会让每条都被判成
    未知 key 而全量丢弃，且不会有任何异常——所以映射必须在这里做。

    返回 `[]` 的情形：输出不是 JSON、没有 `memories` 数组、或每条都被守卫拦下。
    """
    data = _parse_json(raw)
    if data is None:
        return []
    # 兼容模型省略外层包裹直接吐数组的情况
    items: Any = data if isinstance(data, list) else (data.get("memories") if isinstance(data, Mapping) else None)
    if not isinstance(items, list):
        logger.warning("memory extract parse failed: memories_not_list(%s)", type(data).__name__)
        return []

    out: list[dict] = []
    for item in items:
        if not isinstance(item, Mapping):
            logger.warning("memory dropped: entry_not_object(%s)", type(item).__name__)
            continue
        # 白名单校验用原文 key（strip 即可）：词表都是短中文标识，压平反而掩盖漂移
        key = str(item.get("key") or "").strip()
        if not is_memory_key(key):
            # 未知 key 本身就是用户可控文本，过 PII 才决定日志怎么写（I-1）：
            # 命中则只留类型与安全替身，未命中才带截断后的原文（便于排查模型漂移）
            kind = detect_pii(key)
            if kind is not None:
                logger.warning("memory dropped: pii=%s key=%s", kind, _scrubbed_key_hint(key))
            else:
                logger.warning("memory dropped: unknown key=%s", clamp_text(key, 32))
            continue
        value = clamp_text(item.get("value"), MAX_VALUE_CHARS)
        if not value:
            logger.warning("memory dropped: empty value key=%s", key)
            continue
        evidence = clamp_text(item.get("evidence"), MAX_EVIDENCE_CHARS)
        # 先截断再判 PII：判的是"最终要落库的那串文本"，与存储内容保持一致
        kind = detect_pii(value, evidence)
        if kind is not None:
            # 只打命中类型与 key，绝不打原文（spec §10.1）
            logger.warning("memory dropped: pii=%s key=%s", kind, key)
            continue
        out.append(
            {
                "mem_key": key,
                "mem_value": value,
                "confidence": _clamp_confidence(item.get("confidence")),
                "scope": _clamp_scope(item.get("scope"), key),
                "evidence_text": evidence,
            }
        )
    return out


def _amount(value: Any, param: str) -> Optional[str]:
    """把价格参数转成展示串：转不了或不是有限数就跳过该条并留痕。

    `params` 是模型原始参数，不校验不限长：`price_max` 可能是 `"50"`、`"面议"`、
    `None`。整数不带 `.0`（渲染成 "50元以内" 而不是 "50.0元以内"）。
    """
    try:
        number = float(value)
    except (TypeError, ValueError):
        logger.warning("memory behavior skipped: price not numeric param=%s", param)
        return None
    if not math.isfinite(number):
        logger.warning("memory behavior skipped: price not finite param=%s", param)
        return None
    return str(int(number)) if number.is_integer() else str(number)


def _clean_text(value: Any) -> str:
    """文本类参数（cat_l1 / condition / city）压平截断；空白值返回 ""。"""
    return clamp_text(value, MAX_VALUE_CHARS)


def _emit_fact(
    out: list[dict],
    *,
    mem_key: str,
    mem_value: str,
    confidence: float,
    scope: str,
    evidence: str,
) -> bool:
    """组装一条行为证据；空值或命中 PII 则丢弃（返回 False 供调用方决定后续）。"""
    value = clamp_text(mem_value, MAX_VALUE_CHARS)
    if not value:
        logger.warning("memory dropped: empty behavior value key=%s", mem_key)
        return False
    text = clamp_text(evidence, MAX_EVIDENCE_CHARS)
    kind = detect_pii(value, text)
    if kind is not None:
        logger.warning("memory dropped: pii=%s key=%s", kind, mem_key)
        return False
    out.append(
        {
            "mem_key": mem_key,
            "mem_value": value,
            "confidence": confidence,
            "scope": scope,
            "evidence_text": text,
            "source": "behavior",
        }
    )
    return True


def behavior_facts(tool_calls: Sequence[Mapping]) -> list[dict]:
    """把本轮 trace 的 `tool_calls[].params` 合成行为证据（spec §6.4）。

    为什么值得单独做：工具参数是**用户实际筛选过的东西**，比模型从对话里推断可靠，
    且不花一次 LLM 调用。取值口径（表见 §6.4）：

    | 参数 | mem_key | value | confidence |
    |---|---|---|---|
    | `order_by == "price_asc"` | 交易习惯 | 倾向按价格从低到高筛选 | 0.5 |
    | `price_max` | 预算上限 | `<max>元以内` | 0.7 |
    | `price_min` + `price_max` | 预算区间 | `<min>-<max>元` | 0.7 |
    | `condition` | 成色要求 | 原值 | 0.7 |
    | `cat_l1` | 类目偏好 | 原值 | 0.6 |
    | `city` | 常驻城市 | 原值 | 0.7 |

    几条硬口径：
    - `ok=False` 的调用跳过（没执行成功的筛选不是行为）；不含白名单参数的调用整条跳过；
    - **绝不读 `error` 字段**（评审 M3）：异常文本里常常回流用户的 `keyword` 搜索词；
    - 同一次调用带 `cat_l1` 时，预算类证据的 scope 写 `category:<cat_l1>`（否则 global）；
      `类目偏好` 恒为 `category:<cat_l1>`；`常驻城市` / `交易习惯` / `成色要求` 恒 global
      （它们与类目无关，绑类目会把同一个习惯拆成 N 行）；
    - `evidence_text` 只由参数本身拼成（如 `搜索参数 price_max=50`），**不含 keyword
      原文**（spec §10.3 数据最小化）。
    """
    out: list[dict] = []
    for record in tool_calls or []:
        if not isinstance(record, Mapping):
            logger.warning("memory behavior skipped: record_not_object(%s)", type(record).__name__)
            continue
        if not record.get("ok"):
            continue
        params = record.get("params") or {}
        if not isinstance(params, Mapping):
            logger.warning("memory behavior skipped: params_not_object(%s)", type(params).__name__)
            continue
        if not any(name in params for name in _BEHAVIOR_PARAM_WHITELIST):
            continue

        # 类目：既是一条证据，也决定同一调用里预算类证据的 scope。
        # PII 命中时连 scope 一起作废——不能把可疑文本写进 scope 列。
        cat = _clean_text(params.get("cat_l1"))
        if cat and detect_pii(cat, f"搜索参数 cat_l1={cat}") is not None:
            logger.warning("memory behavior dropped: pii in cat_l1")
            cat = ""
        price_scope = f"{_CATEGORY_PREFIX}{clamp_text(cat, _SCOPE_CHARS - len(_CATEGORY_PREFIX))}" if cat else SCOPE_GLOBAL

        order_by = _clean_text(params.get("order_by"))
        if order_by in _ORDER_BY_FACTS:
            mem_key, mem_value, confidence = _ORDER_BY_FACTS[order_by]
            _emit_fact(
                out,
                mem_key=mem_key,
                mem_value=mem_value,
                confidence=confidence,
                scope=SCOPE_GLOBAL,
                evidence=f"搜索参数 order_by={order_by}",
            )

        lo = _amount(params["price_min"], "price_min") if params.get("price_min") not in (None, "") else None
        hi = _amount(params["price_max"], "price_max") if params.get("price_max") not in (None, "") else None
        if hi is not None:
            _emit_fact(
                out,
                mem_key="预算上限",
                mem_value=f"{hi}元以内",
                confidence=0.7,
                scope=price_scope,
                evidence=f"搜索参数 price_max={hi}",
            )
        if lo is not None and hi is not None:
            _emit_fact(
                out,
                mem_key="预算区间",
                mem_value=f"{lo}-{hi}元",
                confidence=0.7,
                scope=price_scope,
                evidence=f"搜索参数 price_min={lo} price_max={hi}",
            )

        for name, (mem_key, confidence) in _TEXT_PARAM_FACTS.items():
            text = _clean_text(params.get(name))
            if not text:
                continue
            scope = f"{_CATEGORY_PREFIX}{clamp_text(text, _SCOPE_CHARS - len(_CATEGORY_PREFIX))}" if name == "cat_l1" else SCOPE_GLOBAL
            _emit_fact(
                out,
                mem_key=mem_key,
                mem_value=text,
                confidence=confidence,
                scope=scope,
                evidence=f"搜索参数 {name}={text}",
            )
    return out


# ── 异步编排（spec §6.1 / §6.5）─────────────────────────────────────────────────

# 在途提取任务的持有表：asyncio 只弱引用任务，不留引用的话"起了就没人管"的提取任务
# 可能在跑完前被 GC 回收（表现为偶发的静默不提取，几乎无法复现）。
# 对标 app/services/home_widget.py 的 _spawn_weather_refresh 写法。
_extract_tasks: set[asyncio.Task] = set()


def _last_user_text(messages: Sequence) -> str:
    """取最近一条用户消息文本（跳过条件的长度门槛用它，spec §6.1）。"""
    for msg in reversed(list(messages or [])):
        if _role_of(msg) == _ROLE_USER:
            return str(_content_of(msg) or "")
    return ""


async def _get_extract_llm():
    """构造提取用的 LLM。

    - 模型名取 `agent_memory_extract_model`，为空则传 None 让 `build_chat_llm` 回落
      `ai_model` 表的 LLM 默认 —— **绝不硬编码 `settings.ai_model`**，那会盖掉运营在
      管理后台配置的模型（spec §6.2 第 7 条）；
    - temperature=0：提取是结构化抽取，不需要采样多样性；
    - 先 `refresh_llm_config()`：进程启动预热可能失败（DB 未就绪），这里补一次；
    - 惰性 import：`model_resolver` 依赖 DB 层，模块级引入会把整条链拖进 memory 包。
    """
    from app.services.model_resolver import build_chat_llm, refresh_llm_config

    await refresh_llm_config()
    return build_chat_llm(
        model_name=settings.agent_memory_extract_model or None, temperature=0
    )


async def _dialogue_facts(history: str) -> list[dict]:
    """跑一次提取 LLM 并解析成事实列表；**任何失败只 warning 并返回 []**。

    单独 try/except 是关键：LLM 挂了不能连累本轮的行为证据（工具参数是免费且可靠的），
    所以调用方把这段当成"可失败的可选项"，失败即视作本轮没有对话事实。
    """
    try:
        llm = await _get_extract_llm()
        # ⚠️ 只能 replace：提示词正文含 `{"memories":[]}` 字面大括号，
        #    str.format() 会抛 KeyError('"memories"')，被后台任务的 except 吞成一条
        #    warning —— 提取会静默全失效（同 store 的软删行教训）。
        prompt = EXTRACT_PROMPT.replace("{history}", history)
        # 必须直连 ainvoke：走 agent.llm.ainvoke_or_stream 会经 ContextVar 取 TokenSink，
        # 而 create_task 复制当前 context —— 提取 JSON 原文会被 emit 进用户的 SSE 流，
        # 且 usage 被覆盖、计费错（runner 在 bind_sink 区间里调度本任务）。
        resp = await llm.ainvoke([HumanMessage(content=prompt)])
    except Exception as e:
        # 只打异常类型：响应原文可能整段是用户对话（spec §10.1）
        logger.warning("memory extract llm failed: %s", type(e).__name__)
        return []
    content = getattr(resp, "content", "")
    if not isinstance(content, str):
        # 多模态块列表等异常结构：交给 parse_memories 判失败，不在这里猜
        content = str(content or "")
    return parse_memories(content)


async def run_extract(
    *,
    messages: Sequence,
    final_reply: str,
    user_id: str,
    tenant_hint: str,
    session_id: str,
    trace: Mapping,
) -> None:
    """把一轮对话变成记忆：调提取 LLM → 合成行为证据 → UPSERT 落库 → 淘汰溢出。

    只在后台任务里被调用（`schedule_extract`），异常由 `_extract_guarded` 兜住。
    前置守卫按"越贵的越靠后"排序，且**一次 DB 会话都不跨 LLM 调用持有**
    （提取每轮一次、日均可上万次，占着连接池等网络会让前台对话抢不到连接）：

    1. **session_id 必须是非空稳定串**，否则直接返回并 warning：空串会让 `plan_upsert`
       写 `last_session_id=""`，此后每轮都被判成"同会话"（§6.3-B），计数永久冻结在 1、
       特质层永不激活；而 runner 的 `transient-<uuid>` 每轮一变，会让单次对话产出 N 个
       假会话、在同一次对话里就凑满升格门槛。两个方向都是**静默毁掉特质层**，
       所以宁可这轮不提取。
    2. 租户解析（写路径 `authoritative=True`，评审 P2-1）：解析不出来就不读不写，
       也不白花一次 LLM 调用。
    3. 提取输入为空则不调 LLM（提不到东西，还费一次调用），但行为证据照常落库。
    """
    session = str(session_id or "").strip()
    if not session:
        logger.warning("memory extract skipped: no stable session_id")
        return
    try:
        uid = int(str(user_id or "").strip())
    except (TypeError, ValueError):
        logger.warning("memory extract skipped: user_id not numeric")
        return
    if uid <= 0:
        # 与读路径同一口径：未归属用户不写（spec §9.1.1）
        logger.warning("memory extract skipped: unattributable user_id")
        return

    history = build_extract_input(
        messages, final_reply, max_msgs=int(settings.agent_memory_extract_max_msgs)
    )
    if not history:
        # 仍要继续：行为证据不花 LLM 调用，没对话历史时也照样有价值
        logger.warning("memory extract dialogue pass skipped: empty history")

    # 只认 tool_calls：trace 可能是 {}，且**绝不读 trace 的 error 字段**（评审 M3 ——
    # 异常文本里常回流用户的 keyword 搜索词，进画像或日志都是泄漏）
    tool_calls = (trace.get("tool_calls") or []) if isinstance(trace, Mapping) else []

    async with async_session() as db:
        tenant_id = await store.resolve_tenant_id(
            db, uid, str(tenant_hint or ""), authoritative=True
        )
    if tenant_id <= 0:
        # store 内部已就"解析不出来"留痕，这里只补一条"提取因此作废"的落点
        logger.warning("memory extract skipped: tenant unresolved user=%s", uid)
        return

    facts: list[dict] = []
    if history:
        facts += await _dialogue_facts(history)
    facts += behavior_facts(tool_calls)
    if not facts:
        return

    async with async_session() as db:
        for fact in facts:
            await store.upsert_fact(
                db,
                tenant_id=tenant_id,
                user_id=uid,
                mem_key=fact["mem_key"],
                mem_value=fact["mem_value"],
                confidence=fact["confidence"],
                scope=fact["scope"],
                # 对话事实缺省 source=dialog；行为证据由 behavior_facts 自带 "behavior"
                source=str(fact.get("source") or "dialog"),
                session_id=session,
                evidence_text=fact["evidence_text"],
            )
        await store.evict_overflow(
            db, tenant_id=tenant_id, user_id=uid,
            max_rows=settings.agent_memory_max_rows,
        )
        # 特质聚合触发（spec §7.1"每次事实 UPSERT 后检查"即指此处）：必须在 evict 之后——
        # 淘汰可能把够格事实软删，先聚后汰会让聚合基于即将作废的证据下结论。
        # 惰性 import 打破 extract↔aggregate 的模块级循环（aggregate 顶层依赖 extract 的守卫）。
        # 失败不影响本轮已落库的事实：maybe_aggregate 在自身内部兜住全部异常。
        from app.services.memory import aggregate

        await aggregate.maybe_aggregate(db=db, tenant_id=tenant_id, user_id=uid)



def schedule_extract(
    *,
    messages: Sequence,
    final_reply: str,
    user_id: str,
    tenant_hint: str,
    session_id: str,
    trace: Mapping,
    resume: Optional[str],
    interrupted: bool,
    error: str,
) -> None:
    """同步入口：判断该不该提取，该则起后台任务，**不 await、不阻塞回复**（spec §6.5）。

    跳过条件命中时零开销：不建任务、不开会话、不调模型。
    """
    if not should_extract(
        enabled=bool(settings.agent_memory_enabled),
        user_id=str(user_id or ""),
        resume=resume,
        interrupted=bool(interrupted),
        error=str(error or ""),
        last_user_text=_last_user_text(messages),
    ):
        return
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError as e:
        # 非异步上下文调用（如脚本 / 同步测试）：没有 loop 就没有后台任务可言
        logger.warning("memory extract not scheduled: %s", type(e).__name__)
        return
    task = loop.create_task(
        _extract_guarded(
            messages=messages, final_reply=final_reply, user_id=user_id,
            tenant_hint=tenant_hint, session_id=session_id, trace=trace,
        )
    )
    _extract_tasks.add(task)
    task.add_done_callback(_extract_tasks.discard)


async def _extract_guarded(**kwargs) -> None:
    """后台任务的安全网：任何异常只降级成一条 warning，绝不冒泡到对话主链路。"""
    try:
        await run_extract(**kwargs)
    except Exception as e:
        logger.warning("memory extract failed: %s", type(e).__name__)

