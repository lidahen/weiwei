"""特质聚合器：把攒够证据的事实归纳成 `memory_type='trait'` 的特质行（spec §7）。

纯判定复用 `extract` 的守卫函数（scope/confidence/PII 口径与事实层一致，禁止两套规则）；
DB 读写全部走 `store`，唯一键语义（不含 status → 必须 UPSERT）由 store 封装。

⚠️ 模块依赖是单向的：aggregate **顶层** import extract；extract 对 aggregate 的引用
（`run_extract` 末尾的聚合触发）必须留在函数体内做惰性 import，否则 extract↔aggregate
在模块加载期互相引用会炸。

对用户静默、对日志不静默：解析失败 / 冲突短路 / 上游异常都有可 grep 的 warning；
但**解析失败绝不触发特质退场**——一次被截断的 JSON 不得静默清空整个特质层（Task 4 评审）。
"""
import json
import logging
import re
from collections.abc import Mapping, Sequence
from datetime import datetime, timedelta
from typing import Any, Optional

from app.config import settings
from app.services.memory import extract, logic, store
from app.services.memory.constants import MEM_KEYS, PII_PATTERNS, TRAIT_KEYS, SCOPE_GLOBAL
from app.services.memory.logic import clamp_text, detect_pii, is_trait_key

logger = logging.getLogger(__name__)

# 特质行用固定哨兵做 session_id：它不是真实会话，6h 窗口内的重复聚合走 B 分支
# 只刷新值、计数不涨；但默认 24h 冷却 > 6h 时间窗，冷却到期后的重算会按跨时段
# +1（hit/session 各自递增）。当前没有消费方读取 trait 的计数（render_block 不
# 渲染、升格查询只查 memory_type='fact'），不要把它误当"用户行为证据频次"。
_AGGREGATE_SESSION = "aggregate"
_ADVICE_CHARS = 40
_LEVELS = ("high", "medium", "low")
_MAX_LOG_CONFLICTS = 3  # 冲突内容留痕排查用，但日志只放前 3 条防爆量


def _safe_log_text(value: Any, limit: int) -> str:
    """日志用文本：先对**完整原文**做 PII 检测（先截断会把手机号切成漏检的半截），
    命中只回类型与长度；未命中才截断回显（与未知 key 的日志口径一致，spec §10.1）。
    """
    raw = str(value or "")
    for kind, pattern in PII_PATTERNS.items():
        if re.search(pattern, raw):
            return f"pii={kind}(len={len(raw)})"
    return clamp_text(raw, limit)

# 逐字照抄 spec §7.2 的提示词块；「可用的特质」一节由 TRAIT_KEYS 动态拼入（§7.2 块外的
# "特质写入"段给了同一份词表）—— 动态化让提示词与 constants 词表不可能各写各的漂移。
# ⚠️ 输出示例含字面大括号：只能 .replace("{facts}", ...) 填占位，禁 .format()（同 extract）。
AGGREGATE_PROMPT = """\
你是用户画像聚合器。下面是同一用户长期累积的偏好事实（含命中次数、
跨会话数、置信度、来源）。据此判断哪些已形成稳定特质。

## 可用的特质（只能从下表选，不得自造）
""" + " / ".join(TRAIT_KEYS) + """

## 规则
1. 只有 hit_count >= 3 且 session_count >= 2 的事实才可作为特质证据；
   不足就输出空。
2. 「用户本人的偏好」与「一次性场景」要区分，后者不计入。
3. 每条特质必须列出证据 key，不得凭空归纳。
4. 证据集中在单一类目时，scope 写 category:<类目>；跨类目才写 global。
5. 两类信号冲突时（如自称价格敏感但行为一直选高价），在 conflicts 里写明，
   不要硬归为一类，也不要为此产出特质。
6. advice 必须是给导购 Agent 的一句可执行用法（≤40 字）。

## 输入
事实清单：
{facts}

## 输出（严格 JSON）
{"traits":[{"trait":"价格敏感度","level":"high|medium|low",
  "scope":"global","evidence_keys":["预算上限","交易习惯"],
  "advice":"优先展示性价比款，主动说明价格优势","confidence":0.75}],
 "conflicts":["..."]}"""


def _parse_json(raw: Any) -> Optional[Any]:
    """剥 ```json 围栏再解析；失败返回 None 并留痕（只打异常类型/形态，不打原文）。"""
    text = str(raw or "").strip()
    if not text:
        logger.warning("memory aggregate parse failed: empty_output")
        return None
    if text.startswith("```"):
        lines = text.splitlines()
        if len(lines) < 2:
            logger.warning("memory aggregate parse failed: unterminated_fence")
            return None
        if lines[-1].strip().startswith("```"):
            lines = lines[1:-1]
        else:
            lines = lines[1:]
        text = "\n".join(lines).strip()
    try:
        return json.loads(text)
    except (TypeError, ValueError) as e:
        logger.warning("memory aggregate parse failed: %s", type(e).__name__)
        return None


def build_facts_text(facts: Sequence[Mapping]) -> str:
    """把够格的事实清单渲染成提示词的 {facts} 段（spec §7.2 规则 1 的判据必须可见）。"""
    lines = []
    for row in facts or []:
        lines.append(
            f"- {row.get('mem_key') or '未知'}：{row.get('mem_value') or '未知'}"
            f"（命中 {row.get('hit_count') or 0} 次 / 跨 {row.get('session_count') or 0} 会话"
            f" / 置信度 {row.get('confidence') or 0} / 来源 {row.get('source') or '未知'}"
            f" / 类目 {row.get('scope') or SCOPE_GLOBAL}）"
        )
    return "\n".join(lines)


def parse_aggregate(raw: Any) -> Optional[tuple[list[dict], list[str]]]:
    """把聚合器输出解析成 `(traits, conflicts)`；**结构失败返回 None，不猜**。

    None 与 `([], [])` 的区分是本模块的命门：前者（JSON 截断/整段跑偏）调用方必须
    整体跳过——既不写也不删；后者（模型明确判定没有特质）才允许触发旧特质退场。
    混同两者 = 一次网络抖动静默清空该用户全部特质（Task 4 评审发现的缺口）。

    traits 元素字段：`mem_key / mem_value / confidence / scope / evidence_text`。
    守卫口径与 extract.parse_memories 一致（词表、level 枚举、advice 截 40、
    PII 整条丢弃、scope 非 category: 前缀回落 global、置信度夹 [0.1,0.95]）。
    """
    data = _parse_json(raw)
    if not isinstance(data, Mapping):
        # 裸数组/字符串/null 都不算"聚合成功"——结构对不上就是失败，别猜意图
        if data is not None:
            logger.warning("memory aggregate parse failed: not_object(%s)", type(data).__name__)
        return None
    traits_raw = data.get("traits")
    if not isinstance(traits_raw, list):
        logger.warning("memory aggregate parse failed: traits_not_list")
        return None
    conflicts_raw = data.get("conflicts")
    if conflicts_raw is None:
        conflicts: list[str] = []
    elif isinstance(conflicts_raw, list):
        conflicts = [str(c) for c in conflicts_raw if str(c).strip()]
    else:
        # 模型把单条冲突写成字符串（"自称价格敏感但行为一直选高价"）很常见。
        # 这里绝不能当成"无冲突"落成 []：那会绕过 §7.3 的 conflicts 短路，
        # 直接写新特质并退掉旧特质——与 traits 非 list 同口径，按结构失败处理。
        logger.warning("memory aggregate parse failed: conflicts_not_list(%s)",
                       type(conflicts_raw).__name__)
        return None

    out: list[dict] = []
    for item in traits_raw:
        if not isinstance(item, Mapping):
            logger.warning("memory dropped: entry_not_object(%s)", type(item).__name__)
            continue
        name = str(item.get("trait") or "").strip()
        if not is_trait_key(name):
            # 自造特质名同样是用户可控文本的衍生物：PII 命中就不进日志（I-1 口径）
            kind = detect_pii(name)
            if kind is not None:
                logger.warning("memory dropped: pii=%s trait=%s", kind,
                               extract._scrubbed_key_hint(name))
            else:
                logger.warning("memory dropped: unknown trait=%s", clamp_text(name, 32))
            continue
        level = str(item.get("level") or "").strip().lower()
        if level not in _LEVELS:
            # level 是模型回填的自由文本，12 字正好装得下 11 位手机号——
            # 与其他用户可控文本同口径，日志只回命中类型/截断原文
            logger.warning("memory dropped: bad level trait=%s level=%s", name,
                           _safe_log_text(level, 12))
            continue
        # spec §7.2 规则 3：不得凭空归纳——过滤后没有一条合法证据 key 就整条丢弃。
        # 模型常把多个 key 写成 "预算上限、交易习惯" 一个字符串，逐字符迭代会让
        # 证据全部作废（进而误触发退场）；这里按中英文分隔符拆开，单 key 字符串
        # 也能正确落成一项。
        raw_keys = item.get("evidence_keys")
        if isinstance(raw_keys, str):
            raw_keys = re.split(r"[，,、]", raw_keys)
        elif isinstance(raw_keys, (list, tuple)):
            raw_keys = list(raw_keys)
        else:
            raw_keys = []
        evidence_keys = [
            k.strip() for k in raw_keys
            if isinstance(k, str) and k.strip() in MEM_KEYS
        ]
        if not evidence_keys:
            logger.warning("memory dropped: no valid evidence keys trait=%s", name)
            continue
        advice = clamp_text(item.get("advice"), _ADVICE_CHARS)
        mem_value = f"{level}：{advice}"
        # advice 是从用户原话归纳的中文短句——PII 硬拦照跑（命中整条丢弃，日志不打原文）
        if detect_pii(mem_value) is not None:
            logger.warning("memory dropped: pii in advice trait=%s", name)
            continue
        out.append({
            "mem_key": name,
            "mem_value": mem_value,
            "confidence": extract._clamp_confidence(item.get("confidence")),
            "scope": extract._clamp_scope(item.get("scope"), name),
            "evidence_text": clamp_text(",".join(evidence_keys), 100),
        })
    if traits_raw and not out and not conflicts:
        # "产出过条目但全被守卫丢弃"（词表漂移 / 证据 key 全非法 / PII）不等于
        # "模型判定没有特质"：按解析失败处理，绝不触发退场——否则一次格式跑偏
        # 就会把该用户全部旧特质静默清空（Task 4 评审建立的防线不能在守卫层被绕过）。
        logger.warning("memory aggregate parse failed: all_traits_dropped")
        return None
    return out, conflicts


async def _get_aggregate_llm():
    """聚合 LLM：口径与 extract._get_extract_llm 完全一致（同一配置项、同一回落链）。

    `agent_memory_extract_model` 为空 → 传 None 让 build_chat_llm 回落 ai_model 表的
    LLM 默认 —— 不硬编码 settings.ai_model（spec §6.2 第 7 条，写路径与读路径同源）。
    """
    from app.services.model_resolver import build_chat_llm, refresh_llm_config

    await refresh_llm_config()
    return build_chat_llm(
        model_name=settings.agent_memory_extract_model or None, temperature=0
    )


async def maybe_aggregate(*, db, tenant_id: int, user_id: int) -> None:
    """聚合触发判定（spec §7.1）：够格事实存在 **且** 距上次 trait 落库超过冷却才跑。

    `last_trait_seen` 不过滤 status（Task 8.5 已定口径）：特质被重算清空后仍受冷却
    阻尼，不会被"清空→立即再聚合→又判空"的循环牵着每轮烧 LLM。
    """
    facts = await store.load_promotable_facts(
        db,
        tenant_id=tenant_id,
        user_id=user_id,
        min_hits=int(settings.agent_memory_trait_min_hits),
        min_sessions=int(settings.agent_memory_trait_min_sessions),
    )
    if not facts:
        return
    last = logic._naive(await store.last_trait_seen(db, tenant_id=tenant_id, user_id=user_id))
    cooldown = timedelta(hours=int(settings.agent_memory_trait_cooldown_hours))
    # last 为 None（无 trait 行 / 脏值读不出）= 从未聚合，立即允许；
    # 恰好满 24h 不算"冷却内"（< 判据），与 spec"距今 > 24h 才允许"同一口径
    if last is not None and datetime.now() - last < cooldown:
        return
    # 结束上面的只读事务，把连接还给连接池：run_aggregate 里有一次最长 120s 的
    # LLM 调用（超时 + 重试）。带着未结束的 SELECT 事务等网络，会让并发提取任务
    # 占满连接池（pool_size=10 + max_overflow=20），前台对话（配额/RAG/商品查询）
    # 抢不到连接直接不可用。事实写入在 store._upsert 内已逐条 commit，这里只关
    # 事务、不改变数据（run_extract 的注释同样要求"不跨 LLM 持连接"）。
    await db.commit()
    await run_aggregate(db=db, tenant_id=tenant_id, user_id=user_id, facts=facts)


async def run_aggregate(*, db, tenant_id: int, user_id: int,
                        facts: Sequence[Mapping]) -> None:
    """跑一次聚合并按 spec §7.3 落库。**顺序不能反：先 conflicts，再写，最后退场。**

    - conflicts 非空 → 不写不删只留痕（矛盾证据下重算只会把好特质一起搅黄）；
    - 解析失败（None）→ 同样不写不删——退场语义是"本次判定没有"，不是"本次没判出来"；
    - 写一律 `store.upsert_trait`（唯一键不含 status，"先软删再 INSERT" 必撞
      Duplicate entry 并被降级吞掉 → 特质层静默失效，Task 4 评审实锤）；
    - 全部写成功后才 `deactivate_traits_except`，keep 用 (mem_key, scope) 对：
      同一 key 从 category:数码 迁到 global 时旧 scope 行必须退场，否则两条同 key
      特质同时注入、可能给出矛盾建议。
    - 整体兜异常：聚合失败只 warning，**不得连累已落库的事实**（调用方在
      run_extract 末尾，事实早已 commit）。
    """
    try:
        llm = await _get_aggregate_llm()
        from langchain_core.messages import HumanMessage

        # ⚠️ 只能 replace：提示词含输出示例的字面大括号（同 extract，.format 会抛 KeyError
        #    并被这里的 except 吞成一条 warning——特质层静默停摆）
        prompt = AGGREGATE_PROMPT.replace("{facts}", build_facts_text(facts))
        # 直连 ainvoke：ainvoke_or_stream 会经 ContextVar 继承本轮 TokenSink，
        # 聚合 JSON 漏进用户 SSE 流、usage 被污染（Task 10 评审对照实验实锤）
        resp = await llm.ainvoke([HumanMessage(content=prompt)])
        content = getattr(resp, "content", "")
        if not isinstance(content, str):
            content = str(content or "")

        parsed = parse_aggregate(content)
        if parsed is None:
            # 与"判定为空"严格区分：这里既不写也不退场（见函数 docstring）
            logger.warning("memory aggregate parse failed: keep existing traits")
            return
        traits, conflicts = parsed
        if conflicts:
            # conflicts 是模型对用户陈述的归纳，可能含手机号/门牌；
            # 日志只回脱敏后的内容（spec §10.1：日志本身不得成为泄漏点）
            logger.warning("memory aggregate conflict, skip: %s",
                           [_safe_log_text(c, 80) for c in conflicts[:_MAX_LOG_CONFLICTS]])
            return
        for t in traits:
            ok = await store.upsert_trait(
                db,
                tenant_id=tenant_id,
                user_id=user_id,
                mem_key=t["mem_key"],
                mem_value=t["mem_value"],
                confidence=t["confidence"],
                scope=t["scope"],
                session_id=_AGGREGATE_SESSION,
                evidence_text=t["evidence_text"],
                source="aggregate",
            )
            if not ok:
                # 任一条写失败（_upsert 内部已回滚并 warning）就放弃本次退场：
                # 宁可不应用本轮结论，也不"半应用"——旧特质保留，冷却后可重算。
                logger.warning("memory aggregate upsert failed, skip deactivation")
                return
        await store.deactivate_traits_except(
            db, tenant_id=tenant_id, user_id=user_id,
            keep=[(t["mem_key"], t["scope"]) for t in traits],
        )
    except Exception as e:
        # 只打异常类型：上游原文可能整段是用户对话衍生物（spec §10.1）
        logger.warning("memory aggregate failed: %s", type(e).__name__)
