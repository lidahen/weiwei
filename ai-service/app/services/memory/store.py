"""长期记忆的 DB 层：租户解析、读取、UPSERT、失效、淘汰、升格查询。

判定口径全部在 `logic`（纯函数层），这里只做 I/O 编排：
- 唯一键 `(tenant_id, user_id, memory_type, mem_key, scope)` 不含 `status`，所以写入一律
  按唯一键 UPSERT 到同一行，禁止「先 status=0 再 INSERT」（spec §7.3 的真实教训：
  被软删的行仍占着键位，再 INSERT 必然 Duplicate entry，而异常会被"只告警"吞掉 ——
  特质层会永久静默失效，且没有任何报错）；
- 对用户静默、对日志不静默：每个失败分支都留一条可 grep 的 `logger.warning`，
  只打异常类型，不打 mem_value / evidence 原文（PII，spec §10.3）。
"""
import json
import logging
from collections.abc import Mapping, Sequence
from datetime import datetime
from typing import Optional

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.services.memory import logic
from app.services.memory.constants import SCOPE_GLOBAL

logger = logging.getLogger(__name__)

# 读取列：`logic.render_block` 除渲染字段外还要 hit/session 计数（"证据 N 次"）
_ROW_FIELDS: tuple[str, ...] = (
    "memory_type", "mem_key", "mem_value", "confidence", "scope", "source",
    "hit_count", "session_count", "last_seen",
)
_SELECT_COLUMNS = ", ".join(_ROW_FIELDS)

# spec §8.3：置信度降序取前 N（effective 衰减与阈值过滤在 logic.render_block 里做）
_LOAD_MEMORIES_SQL = text(f"""
    SELECT {_SELECT_COLUMNS}
    FROM ai_user_memory
    WHERE tenant_id = :tenant_id AND user_id = :user_id AND status = 1
    ORDER BY confidence DESC, last_seen DESC
    LIMIT :limit
""")

# spec §7.2 规则 1：只有 hit_count >= 3 且 session_count >= 2 的事实可作特质证据
_LOAD_PROMOTABLE_SQL = text(f"""
    SELECT {_SELECT_COLUMNS}
    FROM ai_user_memory
    WHERE tenant_id = :tenant_id AND user_id = :user_id
      AND memory_type = 'fact' AND status = 1
      AND hit_count >= :min_hits AND session_count >= :min_sessions
    ORDER BY hit_count DESC, last_seen DESC
""")

# 读既有行：**故意不带 status 条件**（见模块 docstring），也故意不 SELECT status。
# 必须含 last_seen：`logic.plan_upsert` 的"独立命中"要靠它算时间窗（评审 P0-1），
# 漏读会让每次同会话写入都被当成"很久以前"，计数虚增、特质层过早升格。
_LOAD_ROW_SQL = text("""
    SELECT id, mem_value, confidence, hit_count, session_count,
           last_session_id, evidence, first_seen, last_seen
    FROM ai_user_memory
    WHERE tenant_id = :tenant_id AND user_id = :user_id
      AND memory_type = :memory_type AND mem_key = :mem_key AND scope = :scope
    LIMIT 1
""")

_INSERT_SQL = text("""
    INSERT INTO ai_user_memory
        (tenant_id, user_id, memory_type, mem_key, mem_value, confidence,
         hit_count, session_count, last_session_id, evidence, scope, source,
         first_seen, last_seen, status)
    VALUES
        (:tenant_id, :user_id, :memory_type, :mem_key, :mem_value, :confidence,
         :hit_count, :session_count, :last_session_id, :evidence, :scope, :source,
         :first_seen, :last_seen, :status)
""")

# WHERE 里除主键外再带唯一键五列：主键命中已足够，但多一层校验能挡住"id 指向别的桶"
# 这类脏写（软删/并发下 id 被复用），代价只是一次索引列比较
_UPDATE_SQL = text("""
    UPDATE ai_user_memory
    SET mem_value = :mem_value, confidence = :confidence,
        hit_count = :hit_count, session_count = :session_count,
        last_session_id = :last_session_id, evidence = :evidence,
        source = :source, first_seen = :first_seen,
        last_seen = :last_seen, status = :status
    WHERE id = :id
      AND tenant_id = :tenant_id AND user_id = :user_id
      AND memory_type = :memory_type AND mem_key = :mem_key AND scope = :scope
""")

_COUNT_ACTIVE_SQL = text("""
    SELECT COUNT(*) AS total FROM ai_user_memory
    WHERE tenant_id = :tenant_id AND user_id = :user_id AND status = 1
""")

# 淘汰候选行：把该用户的有效行全取回来，在 Python 里按**衰减后置信度**排序（评审 P1-3）。
# 口径必须与读取侧 `logic.render_block` 一致 —— 都用 `logic.effective_confidence`：
# 读取时认为"高置信度长期偏好该保留"，淘汰时却按 last_seen 杀，两边打架会优先杀掉
# 一年前提过、至今有效的核心约束（"不要小米"那类）。
# 写路径已限每用户 ≤ max_rows 行（量级 50），全取安全；LIMIT 只是防御性兜底。
_EVICT_CANDIDATES_SQL = text("""
    SELECT id, confidence, last_seen
    FROM ai_user_memory
    WHERE tenant_id = :tenant_id AND user_id = :user_id AND status = 1
    ORDER BY id ASC
    LIMIT :cap
""")

_LAST_TRAIT_SEEN_SQL = text("""
    SELECT MAX(last_seen) AS last_seen FROM ai_user_memory
    WHERE tenant_id = :tenant_id AND user_id = :user_id
      AND memory_type = 'trait'
""")


# ── 小工具（脏值只告警，绝不让异常穿透到对话主链路）──────────────────────────────


def _tenant_ok(tenant_id) -> bool:
    """0 / 非数表示无法归属：调用方据此不读不写（spec §9.1.1 第 3 条）。"""
    try:
        return int(tenant_id) > 0
    except (TypeError, ValueError):
        return False


def _as_int(value) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError) as e:
        logger.warning("memory: expected int: %s", type(e).__name__)
        return 0


def _as_float(value) -> float:
    """confidence 是 DECIMAL(3,2)，读回来是 Decimal；下游全部按 float 做衰减算术。"""
    try:
        return float(value)
    except (TypeError, ValueError) as e:
        logger.warning("memory: confidence not numeric: %s", type(e).__name__)
        return 0.0


def _as_datetime(value) -> Optional[datetime]:
    """DATETIME 列可能以 datetime / ISO 字符串返回，统一成 naive datetime。

    不统一的话，聚合侧 `datetime.now() - value` 会抛 TypeError，而那个异常只会变成
    一条后台 warning —— 特质聚合的冷却判断会静默失效。
    """
    if isinstance(value, datetime):
        return value.replace(tzinfo=None) if value.tzinfo is not None else value
    if isinstance(value, str) and value.strip():
        try:
            return datetime.fromisoformat(value)
        except ValueError as e:
            logger.warning("memory: last_seen parse failed: %s", type(e).__name__)
    elif value is not None:
        logger.warning("memory: unexpected last_seen type: %s", type(value).__name__)
    return None


def _row_to_dict(row) -> dict:
    """把 RowMapping / ORM 实例统一成 dict。

    `logic.render_block` 用 `row.get(...)` 读行（ORM 实例没有 `.get`，会直接抛
    AttributeError）；confidence 也必须在这里转 float。
    """
    if isinstance(row, Mapping):
        data = dict(row)
    else:
        data = {col: getattr(row, col, None) for col in _ROW_FIELDS}
    if "confidence" in data:
        data["confidence"] = _as_float(data.get("confidence"))
    for col in ("hit_count", "session_count"):
        if col in data:
            data[col] = _as_int(data.get(col))
    return data


def _rowcount(result) -> int:
    """UPDATE 的受影响行数；驱动不支持（None / 负数）时按 0 处理。"""
    value = getattr(result, "rowcount", None)
    try:
        return max(0, int(value))
    except (TypeError, ValueError):
        logger.warning("memory: rowcount not numeric: %s", type(value).__name__)
        return 0


async def _rollback(db) -> None:
    """写失败后回滚：否则同一 session 上的后续写入全落在已中止的事务里，一起静默失败。"""
    rollback = getattr(db, "rollback", None)
    if rollback is None:
        return
    try:
        await rollback()
    except Exception as e:
        logger.warning("memory: rollback failed: %s", type(e).__name__)


# ── 租户解析（spec §9.1.1）────────────────────────────────────────────────────


async def resolve_tenant_id(
    db: AsyncSession, user_id: int, hint: str = "", *, authoritative: bool = False
) -> int:
    """解析记忆归属租户；`0` 表示无法归属（调用方据此不读不写）。

    - 读路径（`authoritative=False`，默认）：`hint` 是正整数就直接用（正常多租户请求
      走这条，零额外查询）。读错的代价只是"本次少注入一层记忆"，量级可接受；
    - **写路径必须传 `authoritative=True`**（评审 P2-1）：跳过 `hint` 直接查
      `sys_user.tenant_id`（权威归属）。`X-Tenant-Id` 是客户端可控头，传错头会把
      A 租户的画像写进 B 租户 —— 安全事故级，不是性能问题；
    - 权威查询拿不到时**绝不退回 hint**：宁可不写（返回 0），也不写错桶；
    - 仍拿不到 → 0，绝不臆造租户 —— 同一用户的记忆若在 0 和 N 两个桶之间分裂，
      功能看着正常但永远读不到自己写的数据。
    """
    try:
        hint_id = int(str(hint).strip())
    except (TypeError, ValueError):
        hint_id = 0
    if not authoritative and hint_id > 0:
        return hint_id

    try:
        result = await db.execute(
            text("SELECT tenant_id FROM sys_user WHERE user_id = :uid AND del_flag = '0'"),
            {"uid": user_id},
        )
        row = result.mappings().first()
        tenant_id = _as_int(row.get("tenant_id")) if row else 0
    except Exception as e:
        logger.warning("memory: resolve_tenant query failed: %s", type(e).__name__)
        return 0
    if tenant_id <= 0:
        if authoritative and hint_id > 0:
            # 写路径：DB 拿不到而只有头可用 —— 明确拒绝，并留下可 grep 的痕迹
            logger.warning("memory tenant: authoritative lookup failed, hint=%s", hint)
        else:
            logger.warning("memory: resolve_tenant unresolvable: no tenant_id for user")
        return 0
    if authoritative and hint_id > 0 and hint_id != tenant_id:
        # 头与权威不一致：以 sys_user 为准，同时留痕（客户端传错租户头的信号）
        logger.warning("memory: authoritative tenant differs from hint, hint=%s db=%s", hint, tenant_id)
    return tenant_id


# ── 读取 ─────────────────────────────────────────────────────────────────────


async def load_memories(
    db: AsyncSession, *, tenant_id: int, user_id: int, limit: int = 50
) -> list[dict]:
    """取用户的有效记忆行（spec §8.3）；异常降级成空列表 + warning（spec §12）。

    返回 **plain dict** 而非 ORM 行：`logic.render_block` 用 `row.get(...)` 读，
    且 confidence 必须在这里从 Decimal 转成 float。
    """
    if not _tenant_ok(tenant_id):
        logger.warning("memory: load_memories skipped: unattributable tenant")
        return []
    limit = max(0, _as_int(limit))
    if limit == 0:
        return []
    try:
        result = await db.execute(
            _LOAD_MEMORIES_SQL,
            {"tenant_id": int(tenant_id), "user_id": user_id, "limit": limit},
        )
        rows = result.mappings().all()
    except Exception as e:
        logger.warning("memory: load_memories failed: %s", type(e).__name__)
        return []
    return [_row_to_dict(row) for row in rows]


async def load_promotable_facts(
    db: AsyncSession, *, tenant_id: int, user_id: int, min_hits: int, min_sessions: int
) -> list[dict]:
    """够格升格为特质的事实行（命中数与跨会话数双阈值，spec §7.1/§7.2）。"""
    if not _tenant_ok(tenant_id):
        logger.warning("memory: load_promotable_facts skipped: unattributable tenant")
        return []
    try:
        result = await db.execute(
            _LOAD_PROMOTABLE_SQL,
            {
                "tenant_id": int(tenant_id),
                "user_id": user_id,
                "min_hits": _as_int(min_hits),
                "min_sessions": _as_int(min_sessions),
            },
        )
        rows = result.mappings().all()
    except Exception as e:
        logger.warning("memory: load_promotable_facts failed: %s", type(e).__name__)
        return []
    return [_row_to_dict(row) for row in rows]


async def last_trait_seen(
    db: AsyncSession, *, tenant_id: int, user_id: int
) -> Optional[datetime]:
    """该用户**所有** trait 行的最大 last_seen；无 trait 行 → None（立即允许聚合）。

    故意不过滤 `status`（spec §7.1 原文是"所有 trait 行"）：若只看有效行，特质被一次
    重算清空后这里会返回 None，调用方把它当成"从未聚合"，聚合器就会**每轮**重跑，
    24h 冷却形同虚设 —— 预算从 ≤1000 次/天变成按轮次，也失去了对清空路径的阻尼。
    """
    if not _tenant_ok(tenant_id):
        logger.warning("memory: last_trait_seen skipped: unattributable tenant")
        return None
    try:
        result = await db.execute(
            _LAST_TRAIT_SEEN_SQL,
            {"tenant_id": int(tenant_id), "user_id": user_id},
        )
        row = result.mappings().first()
    except Exception as e:
        logger.warning("memory: last_trait_seen failed: %s", type(e).__name__)
        return None
    if not row:
        return None
    return _as_datetime(row.get("last_seen"))


# ── 写入 ─────────────────────────────────────────────────────────────────────


async def _load_row(
    db: AsyncSession, tenant_id: int, user_id: int, memory_type: str, mem_key: str, scope: str
) -> Optional[dict]:
    """读唯一键对应的既有行。

    **不带 status 条件**：软删行仍占着 `uk_user_mem` 键位，漏读会让后续 INSERT 撞
    Duplicate entry 并被吞成 warning —— 这是本模块最不能省的一行条件。
    """
    result = await db.execute(
        _LOAD_ROW_SQL,
        {
            "tenant_id": int(tenant_id),
            "user_id": user_id,
            "memory_type": memory_type,
            "mem_key": mem_key,
            "scope": scope,
        },
    )
    row = result.mappings().first()
    return _row_to_dict(row) if row else None


async def _upsert(
    db: AsyncSession,
    *,
    tenant_id: int,
    user_id: int,
    memory_type: str,
    mem_key: str,
    mem_value: str,
    confidence: float,
    scope: str,
    source: str,
    session_id: str,
    evidence_text: str,
) -> bool:
    """fact / trait 共用的 UPSERT：读既有行 → plan_upsert → INSERT 或 UPDATE。"""
    label = "trait" if memory_type == "trait" else "fact"
    if not _tenant_ok(tenant_id):
        # 0 号桶会把同一用户的记忆劈成两半（spec §9.1.1）：宁可不写
        logger.warning("memory: upsert_%s skipped: unattributable tenant", label)
        return False

    scope = str(scope or "").strip() or SCOPE_GLOBAL
    mem_key = str(mem_key or "").strip()
    try:
        existing = await _load_row(db, tenant_id, user_id, memory_type, mem_key, scope)
    except Exception as e:
        # 连接级错误会让 session 进入待回滚状态：不回滚的话，同一 run_extract 里
        # 后续 upsert 全部 PendingRollbackError —— 本轮记忆整套丢失
        logger.warning("memory: upsert_%s load failed: %s", label, type(e).__name__)
        await _rollback(db)
        return False

    cols = logic.plan_upsert(
        existing,
        mem_value=mem_value,
        confidence=confidence,
        session_id=session_id,
        evidence_text=evidence_text,
        now=datetime.now(),
        # 显式接线：不传的话配置项就是个装饰品（评审 P0-1 的时间窗阈值）
        gap_hours=settings.agent_memory_session_gap_hours,
    )
    if not cols:
        return False  # plan_upsert 已记录跳过原因（空 session_id / 空 value 等）

    params = {
        "tenant_id": int(tenant_id),
        "user_id": user_id,
        "memory_type": memory_type,
        "mem_key": mem_key,
        "mem_value": cols["mem_value"],
        "confidence": cols["confidence"],
        "hit_count": cols["hit_count"],
        "session_count": cols["session_count"],
        "last_session_id": cols["last_session_id"],
        # plan_upsert 给的是 list[dict]，JSON 列要自己序列化成文本（保留中文原文）
        "evidence": json.dumps(cols["evidence"], ensure_ascii=False),
        "scope": scope,
        # source 以最后一次写入为准（spec §6.4.2）：行为证据与对话证据共用同一行
        "source": str(source or "dialog"),
        "first_seen": cols["first_seen"],
        "last_seen": cols["last_seen"],
        "status": cols["status"],
    }
    try:
        if existing is None:
            await db.execute(_INSERT_SQL, params)
        else:
            params["id"] = existing.get("id")
            result = await db.execute(_UPDATE_SQL, params)
            if _rowcount(result) == 0:
                # WHERE 没命中却提交，等于"报告成功但什么都没写"——与本模块
                # "对用户静默、对日志不静默"相反，必须显式失败
                logger.warning(
                    "memory upsert: UPDATE matched 0 rows key=%s user=%s", mem_key, user_id)
                await _rollback(db)
                return False
        await db.commit()
    except Exception as e:
        logger.warning("memory: upsert_%s failed: %s", label, type(e).__name__)
        await _rollback(db)
        return False
    return True


async def upsert_fact(
    db: AsyncSession,
    *,
    tenant_id: int,
    user_id: int,
    mem_key: str,
    mem_value: str,
    confidence: float,
    scope: str,
    source: str,
    session_id: str,
    evidence_text: str,
) -> bool:
    """写入一条事实（对话提取 source="dialog" / 工具行为 source="behavior"）。"""
    return await _upsert(
        db,
        tenant_id=tenant_id,
        user_id=user_id,
        memory_type="fact",
        mem_key=mem_key,
        mem_value=mem_value,
        confidence=confidence,
        scope=scope,
        source=source,
        session_id=session_id,
        evidence_text=evidence_text,
    )


async def upsert_trait(
    db: AsyncSession,
    *,
    tenant_id: int,
    user_id: int,
    mem_key: str,
    mem_value: str,
    confidence: float,
    scope: str,
    session_id: str,
    evidence_text: str,
    source: str = "aggregate",
) -> bool:
    """写入一条聚合特质；与事实共用 UPSERT 路径，只差 memory_type 与 source 缺省值。"""
    return await _upsert(
        db,
        tenant_id=tenant_id,
        user_id=user_id,
        memory_type="trait",
        mem_key=mem_key,
        mem_value=mem_value,
        confidence=confidence,
        scope=scope,
        source=source,
        session_id=session_id,
        evidence_text=evidence_text,
    )


async def deactivate_traits_except(
    db: AsyncSession, *, tenant_id: int, user_id: int, keep: Sequence[tuple[str, str]]
) -> int:
    """把不在 `keep` 里的旧 trait 行置 status=0（特质是重算而非追加，spec §7.3）。

    `keep` 是 `(mem_key, scope)` 对而不是裸 key：`scope` 在唯一键里，
    `品质要求@category:数码` 与 `品质要求@global` 是两行独立数据。只按 key 保留，
    会让同 key 改 scope 后的旧行残留，注入块里同时出现两条互相矛盾的特质。

    必须在 upsert_trait 之后调用：先写本次结果，再让旧结果退役。
    """
    if not _tenant_ok(tenant_id):
        logger.warning("memory: deactivate_traits skipped: unattributable tenant")
        return 0
    pairs = [
        (str(key or "").strip(), str(scope or "").strip() or SCOPE_GLOBAL)
        for key, scope in (keep or [])
        if str(key or "").strip()
    ]
    params: dict = {"tenant_id": int(tenant_id), "user_id": user_id}
    if not pairs:
        # 空 keep = "聚合器判定没有任何特质存活"。按 spec 语义应全部退役，但这是唯一
        # 会清空特质层的路径，必须留下可 grep 的告警。调用方用 parse_aggregate 返回
        # None 区分"解析失败"（那种情况根本不会走到这里），空列表只代表结论为空。
        logger.warning(
            "memory deactivate_traits: empty keep set, deactivating all traits tenant=%s user=%s",
            tenant_id, user_id,
        )
        keep_sql = ""
    else:
        # 手工展开占位符：text() 的 expanding 绑定在假 session 里不好断言。
        # 保留条件 = 任一 (mem_key, scope) 对相等；整段 NOT(...) 即"不在保留集里"。
        conditions = []
        for i, (key, scope) in enumerate(pairs):
            params[f"k{i}"] = key
            params[f"s{i}"] = scope
            conditions.append(f"(mem_key = :k{i} AND scope = :s{i})")
        keep_sql = " AND NOT (" + " OR ".join(conditions) + ")"
    sql = text(f"""
        UPDATE ai_user_memory
        SET status = 0
        WHERE tenant_id = :tenant_id AND user_id = :user_id
          AND memory_type = 'trait' AND status = 1{keep_sql}
    """)
    try:
        result = await db.execute(sql, params)
        await db.commit()
    except Exception as e:
        logger.warning("memory: deactivate_traits failed: %s", type(e).__name__)
        await _rollback(db)
        return 0
    return _rowcount(result)


async def evict_overflow(
    db: AsyncSession, *, tenant_id: int, user_id: int, max_rows: int
) -> int:
    """行数超上限时软删**衰减后置信度最低**的若干行（spec §4.2：每用户 ≤50 行）。

    排序口径与读取侧共用 `logic.effective_confidence`（评审 P1-3）：先取回该用户全部
    有效行（量级 50，安全），在 Python 里算衰减值升序取 overflow 个 id，再按主键软删。
    同分按 id 升序定序，保证结果可复现。
    """
    if not _tenant_ok(tenant_id):
        logger.warning("memory: evict_overflow skipped: unattributable tenant")
        return 0
    limit = _as_int(max_rows)
    if limit <= 0:
        logger.warning("memory: evict_overflow skipped: max_rows=%s", max_rows)
        return 0

    params = {"tenant_id": int(tenant_id), "user_id": user_id}
    try:
        result = await db.execute(_COUNT_ACTIVE_SQL, params)
        row = result.mappings().first()
        total = _as_int(row.get("total")) if row else 0
        overflow = total - limit
        if overflow <= 0:
            return 0

        result = await db.execute(
            _EVICT_CANDIDATES_SQL, {**params, "cap": limit * 10})
        rows = result.mappings().all()
        # 与 render_block 同一把尺子：衰减后置信度升序，同分按 id 升序。
        # `now` 在这里取**一次**并传给每一行：逐行各自 datetime.now() 会引入微秒级
        # 抖动，让同分行排序变成"谁被处理得晚谁先死"，既不可复现也不可用 id 定序。
        current = datetime.now()
        scored = sorted(
            ((logic.effective_confidence(
                r.get("confidence"), r.get("last_seen"),
                now=current, half_life_days=settings.agent_memory_half_life_days),
              _as_int(r.get("id")))
             for r in rows),
            key=lambda item: (item[0], item[1]),
        )
        picked = scored[:overflow]
        doomed = [mem_id for _eff, mem_id in picked if mem_id > 0]
        if len(doomed) < len(picked):
            # 主键是 AUTO_INCREMENT，非正数只可能是脏行：留痕，不静默少淘汰
            logger.warning("memory: evict_overflow dropped %d invalid ids",
                           len(picked) - len(doomed))
        if not doomed:
            # COUNT 说超限、候选却取不到（并发删除 / 脏 id）：不发空 IN() 的 SQL
            logger.warning("memory: evict_overflow no candidates: total=%s rows=%s", total, len(rows))
            return 0
        in_params = {**params}
        conditions = []
        for i, mem_id in enumerate(doomed):
            in_params[f"i{i}"] = mem_id
            conditions.append(f":i{i}")
        # WHERE 仍带 tenant_id/user_id：id 来自本函数刚查出的行，但多一层归属校验
        # 能挡住"id 指向别的桶"这类脏写（与 _UPDATE_SQL 同一取舍）
        sql = text(
            "UPDATE ai_user_memory SET status = 0 "
            f"WHERE tenant_id = :tenant_id AND user_id = :user_id AND status = 1 "
            f"AND id IN ({', '.join(conditions)})"
        )
        result = await db.execute(sql, in_params)
        await db.commit()
    except Exception as e:
        logger.warning("memory: evict_overflow failed: %s", type(e).__name__)
        await _rollback(db)
        return 0
    return _rowcount(result)
