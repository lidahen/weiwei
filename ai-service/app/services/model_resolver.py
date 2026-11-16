"""模型解析唯一入口。

**所有**按 model_type / model_id 取模型的地方都必须走这里，不要再各写一份查询。
这段逻辑历史上存在 4 份拷贝（vendor_router._resolve_model / model_resolver /
assist._resolve_llm_model / image._resolve_model）外加 video 路由里的一处内联，
差异本身就是隐患：

- 有的带 `limit(1)`，有的用 `scalar_one_or_none()` —— 后者一旦该类型出现第 2 条
  `default_flag=1` 会直接抛 `MultipleResultsFound`（线上即 500）；
- by-id 分支大多只校验 `del_flag`，不校验 `model_type`/`status` —— 用户把 VIDEO 的
  model_id 传给图片接口会被静默接受，再用错误的协议去调外部 API。

本模块的约定：
- 按类型取默认：**永远 `order_by(model_id).limit(1)`**，选型确定；
  若探测到多条默认，打 warning（数据卫生告警）而不是抛异常。
- 按 id 取：校验存在性 / 启用状态 / 类型匹配，不合法抛 `ModelConfigError`（路由层转 400）。
- **不缓存 ORM 对象**（会话关闭后属性会过期，取用即 DetachedInstanceError）；
  只缓存 chat LLM 需要的字符串三元组。
"""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Any, Optional, Tuple

from langchain_openai import ChatOpenAI
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.ai_model import AiModel

logger = logging.getLogger(__name__)

# chat LLM 配置缓存（api_key, base_url, model_name）
_LLM_CFG_TTL_S = 60
_llm_cfg_cache: Optional[Tuple[str, str, str]] = None
_llm_cfg_cached_at: float = 0.0
_llm_cfg_lock = asyncio.Lock()


class ModelConfigError(RuntimeError):
    """模型配置层面的调用方错误（id 不存在 / 已停用 / 类型不匹配）。

    与"未配置任何可用模型"区分：前者是调用参数错误，路由层应转 400 并回传原因；
    后者返回 None，由调用方给"未配置模型，请联系管理员"的提示。
    """


async def resolve_model(
    db: AsyncSession,
    model_type: str,
    model_id: Optional[int] = None,
) -> Optional[AiModel]:
    """解析要使用的模型记录。

    Args:
        db: 数据库会话。
        model_type: 期望的能力类型（LLM / VISION / IMAGE / VIDEO / MUSIC / TTS ...）。
        model_id: 调用方显式指定的模型 id（如前端下拉选择的模型）。为 None 时取该类型默认。

    Returns:
        命中的 AiModel；该类型下没有任何可用模型时返回 None。

    Raises:
        ModelConfigError: 显式传了 model_id 但不存在 / 已停用 / 类型不匹配。
    """
    if model_id is not None:
        return await _resolve_by_id(db, model_id, model_type)
    return await _resolve_default(db, model_type)


async def resolve_by_id(db: AsyncSession, model_id: int) -> Optional[AiModel]:
    """按 id 取模型记录，只校验存在性与启用状态，**不校验能力类型**。

    供"类型由调用方另行判定"的场景使用（如 vendor 路由）。
    不存在或已停用同样抛 `ModelConfigError`，避免拿到不可用模型继续调外部 API。
    """
    result = await db.execute(
        select(AiModel).where(AiModel.model_id == model_id, AiModel.del_flag == 0)
    )
    model = result.scalar_one_or_none()
    if model is None:
        raise ModelConfigError(f"模型 id={model_id} 不存在")
    if model.status != 1:
        raise ModelConfigError(f"模型 id={model_id}（{model.model_name}）已停用")
    return model


async def _resolve_by_id(db: AsyncSession, model_id: int, expected_type: str) -> AiModel:
    """按 id 取，并额外校验能力类型（防止跨能力误用）。"""
    model = await resolve_by_id(db, model_id)
    if model.model_type != expected_type:
        raise ModelConfigError(
            f"模型 id={model_id}（{model.model_name}）类型不匹配："
            f"该接口需要 {expected_type}，实际为 {model.model_type}"
        )
    return model


async def resolve_creation_candidates(db: AsyncSession, model_type: str) -> list:
    """创作域模型候选链（按优先级排序，供逐镜生成的失败自动降级使用）。

    顺序：① creation_{image,video}_model 指名的高质量档（配置里按 model_name 匹配）
    → ② default_flag=1 默认 → ③ 其余启用中的同类型模型（model_id 升序）。
    去重后返回；一个可用模型都没有返回空列表。显式指定 model_id 时不走本函数。
    """
    preferred = (getattr(settings, f"creation_{model_type.lower()}_model", "") or "").strip()

    async def _query(extra_where=None, order=None, limit=None):
        stmt = select(AiModel).where(
            AiModel.model_type == model_type,
            AiModel.status == 1,
            AiModel.del_flag == 0,
        )
        if extra_where is not None:
            stmt = stmt.where(extra_where)
        if order is not None:
            stmt = stmt.order_by(order)
        if limit is not None:
            stmt = stmt.limit(limit)
        return (await db.execute(stmt)).scalars().all()

    candidates: list = []
    seen: set = set()

    def _push(rows) -> None:
        for m in rows:
            if m.model_id not in seen:
                seen.add(m.model_id)
                candidates.append(m)

    if preferred:
        _push(await _query(extra_where=AiModel.model_name == preferred, order=AiModel.model_id.asc(), limit=1))
    _push(await _query(extra_where=AiModel.default_flag == 1, order=AiModel.model_id.asc(), limit=1))
    # 兜底：其余全部启用中的同类型模型（去重后排在链尾，供失败降级）
    _push(await _query(order=func.coalesce(AiModel.default_flag, 0).desc()))
    return candidates


async def _resolve_default(db: AsyncSession, model_type: str) -> Optional[AiModel]:
    """取该类型默认模型：default_flag=1 优先，否则 status=1 任一。

    恒带 `order_by(model_id).limit(1)`，避免多条默认时抛 MultipleResultsFound。
    """
    # 取 2 条仅为探测"默认不唯一"，只使用第 1 条
    result = await db.execute(
        select(AiModel)
        .where(
            AiModel.model_type == model_type,
            AiModel.default_flag == 1,
            AiModel.status == 1,
            AiModel.del_flag == 0,
        )
        .order_by(AiModel.model_id.asc())
        .limit(2)
    )
    rows = result.scalars().all()
    if rows:
        if len(rows) > 1:
            logger.warning(
                "model_type=%s 存在多条 default_flag=1（已取 model_id 最小者 %s=%s，其余: %s）"
                "，建议在模型管理里只保留一条默认",
                model_type,
                rows[0].model_id,
                rows[0].model_name,
                ", ".join(f"{m.model_id}={m.model_name}" for m in rows[1:]),
            )
        return rows[0]

    result = await db.execute(
        select(AiModel)
        .where(
            AiModel.model_type == model_type,
            AiModel.status == 1,
            AiModel.del_flag == 0,
        )
        .order_by(func.coalesce(AiModel.default_flag, 0).desc(), AiModel.model_id.asc())
        .limit(1)
    )
    return result.scalar_one_or_none()


def _as_float(value: Any, default: Optional[float]) -> Optional[float]:
    """ai_model.temperature/top_p 在库中是 decimal，模型类声明为 String，统一容错转换。"""
    if value is None or value == "":
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def build_chat_llm(
    model: Optional[AiModel] = None,
    *,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    model_name: Optional[str] = None,
    temperature: Optional[float] = None,
    timeout: Optional[int] = None,
    max_retries: Optional[int] = None,
) -> ChatOpenAI:
    """统一构造 ChatOpenAI，供对话 / 视觉 / 审核 / 文案 / 提示词增强共用。

    三种用法：
    1. `build_chat_llm(model_record)` —— 指定 ai_model 记录（路由层查表后用）
    2. `build_chat_llm(temperature=0.1)` —— 用当前默认 LLM 配置（同步，读预热缓存）
    3. `build_chat_llm(model_name="xxx")` —— 显式指定模型名

    默认超时/重试取自 settings，避免各处散落。
    """
    if model is not None:
        api_key = model.api_key or api_key
        base_url = model.base_url or base_url
        model_name = model.model_name or model_name
        temperature = _as_float(model.temperature, temperature)

    if not api_key or not base_url or not model_name:
        cfg_key, cfg_base, cfg_model = current_llm_config()
        api_key = api_key or cfg_key
        base_url = base_url or cfg_base
        model_name = model_name or cfg_model

    # OpenAI SDK 在 base_url 后直接拼 /chat/completions，要求 base_url 自带 /v1 一类版本前缀；
    # 模型管理里只填裸域名（如 https://api.senseaudio.cn）会 404，这里按惯例补 /v1（已有路径的 URL 不动）
    base_url = (base_url or "").rstrip("/")
    if base_url and "/" not in base_url.split("://", 1)[-1]:
        base_url = f"{base_url}/v1"

    return ChatOpenAI(
        base_url=base_url or "",
        api_key=api_key or "sk-placeholder",
        model=model_name or "",
        temperature=temperature if temperature is not None else settings.ai_temperature,
        timeout=timeout if timeout is not None else settings.ai_llm_timeout,
        max_retries=max_retries if max_retries is not None else settings.ai_llm_max_retries,
    )


async def refresh_llm_config(force: bool = False) -> Tuple[str, str, str]:
    """查表取 LLM 默认模型，刷新 chat LLM 配置缓存；查不到或异常则回退 settings。

    供"拿不到 db 会话"的场景（LangGraph 节点 / RAG / 提示词增强）通过
    `current_llm_config()` 同步取用。启动时会预热。
    """
    global _llm_cfg_cache, _llm_cfg_cached_at

    now = time.monotonic()
    if not force and _llm_cfg_cache and (now - _llm_cfg_cached_at) < _LLM_CFG_TTL_S:
        return _llm_cfg_cache

    cfg: Optional[Tuple[str, str, str]] = None
    try:
        from app.database import async_session

        async with async_session() as db:
            model = await _resolve_default(db, "LLM")
        if model and model.api_key and model.base_url and model.model_name:
            cfg = (model.api_key, model.base_url, model.model_name)
            logger.info("chat LLM 配置来自 ai_model 表: %s", model.model_name)
    except Exception as exc:  # noqa: BLE001 —— 取表失败必须回退，不能拖垮对话
        logger.warning("读取 ai_model 默认 LLM 失败，回退 settings: %s", exc)

    if cfg is None:
        cfg = (settings.ai_api_key, settings.ai_base_url, settings.ai_model)
        logger.info("chat LLM 配置回退 settings: %s", settings.ai_model)

    async with _llm_cfg_lock:
        _llm_cfg_cache = cfg
        _llm_cfg_cached_at = time.monotonic()
    return cfg


def current_llm_config() -> Tuple[str, str, str]:
    """同步读取已预热的 chat LLM 配置；未预热时回退 settings。

    `agent/llm._get_llm()` 与 `chat._get_llm()` 是同步函数，用这个取值。
    """
    if _llm_cfg_cache:
        return _llm_cfg_cache
    return (settings.ai_api_key, settings.ai_base_url, settings.ai_model)


def invalidate_cache() -> None:
    """清缓存（管理后台改默认模型后调用；下次取用会重新查表）。"""
    global _llm_cfg_cache, _llm_cfg_cached_at
    _llm_cfg_cache = None
    _llm_cfg_cached_at = 0.0


# ── 兼容旧调用点：原 model_resolver.resolve_default_model / assist._resolve_llm_model ──
async def resolve_default_model(db: AsyncSession, model_type: str) -> Optional[AiModel]:
    """保留旧名，内部转调 resolve_model（语义完全等价，恒带 limit(1)）。"""
    return await resolve_model(db, model_type)


async def resolve_llm_model(db: AsyncSession, model_id: Optional[int] = None) -> Optional[AiModel]:
    """取 LLM 类型模型（替代各处写死 model_type="LLM" 的私有副本）。"""
    return await resolve_model(db, "LLM", model_id)
