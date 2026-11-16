"""Vendor router —— 业务路由统一入口。

按 model_type 查 ai_model 默认模型 → 按 model_provider 路由到 vendor 实例。
支持缓存避免每次查 DB。
"""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Optional, Protocol

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.ai_model import AiModel
from app.services.music_vendor.base import MusicVendorError

logger = logging.getLogger(__name__)


class _VendorLike(Protocol):
    """vendor 最小接口契约. 实际 vendor 类实现可能更多方法."""
    name: str


_CACHE_TTL_S = 60
_cache: dict[str, tuple[float, Optional[_VendorLike]]] = {}
_cache_lock = asyncio.Lock()


async def _resolve_model(db: AsyncSession, model_type: str) -> Optional[AiModel]:
    """按类型取默认模型记录。

    已收敛到 model_resolver.resolve_model（唯一入口），此处仅保留薄封装，
    避免 vendor 相关调用方改签名。
    """
    from app.services.model_resolver import resolve_model

    return await resolve_model(db, model_type)


async def _resolve_model_by_id(db: AsyncSession, model_id: int) -> Optional[AiModel]:
    """按 model_id 查单条记录(用于"用户在前端选了某个模型"的场景)。

    已收敛到 model_resolver.resolve_by_id；此处保持"返回 Optional"的旧语义，
    由调用方（get_vendor_for_model_id）决定如何报错，避免改动异常类型。
    """
    from app.services.model_resolver import ModelConfigError, resolve_by_id

    try:
        return await resolve_by_id(db, model_id)
    except ModelConfigError as exc:
        logger.debug("vendor_router 按 id 取模型失败: %s", exc)
        return None


async def get_vendor_for_model_id(db: AsyncSession, model_id: int, expected_type: str):
    """按 model_id 取 vendor(用于"用户选了某个模型, 按它调 vendor")。

    返回 vendor 实例(已按 model.model_name 设置好)。
    """
    chosen = await _resolve_model_by_id(db, model_id)
    if chosen is None:
        raise MusicVendorError(f"模型 id={model_id} 不存在")
    if chosen.status != 1:
        raise MusicVendorError(f"模型 id={model_id} 已停用")
    if chosen.model_type != expected_type:
        raise MusicVendorError(
            f"模型 id={model_id} 类型不匹配: 期望 {expected_type}, 实际 {chosen.model_type}"
        )
    return _build_vendor(chosen)


def _build_vendor(model: AiModel) -> _VendorLike:
    """根据 model_provider 创建 vendor 实例."""
    provider = (model.model_provider or "").lower()
    if provider == "senseaudio":
        from app.services.music_vendor.senseaudio_universal import SenseaudioUniversalVendor
        return SenseaudioUniversalVendor(
            api_key=model.api_key or "",
            base_url=model.base_url or "https://api.senseaudio.cn",
            model_name=model.model_name or "",
        )
    if provider in ("siliconflow",):
        from app.services.music_vendor.siliconflow_clone import SiliconflowCloneVendor
        return SiliconflowCloneVendor(
            api_key=model.api_key or "",
            clone_model=model.model_name,
        )
    if provider in ("openai", "deepseek", "zhipu", "ollama", "agnes"):
        # 这些 LLM/IMAGE/VIDEO 仍走 ai-service 现有调用方, vendor 抽象留待后续
        # 此处不构造 vendor, 业务方应直接用 model 记录的 api_key+base_url 调用
        raise MusicVendorError(
            f"vendor_router 暂不抽象 provider={provider}, 业务方应直接读 model 记录调用"
        )
    raise MusicVendorError(f"未知 provider: {provider}")


async def get_vendor(db: AsyncSession, model_type: str) -> Optional[_VendorLike]:
    """按 model_type 取默认 vendor, 60s 缓存."""
    cache_key = model_type
    now = time.monotonic()
    async with _cache_lock:
        cached = _cache.get(cache_key)
        if cached and (now - cached[0]) < _CACHE_TTL_S:
            return cached[1]

    model = await _resolve_model(db, model_type)
    if not model:
        async with _cache_lock:
            _cache[cache_key] = (now, None)
        return None
    vendor = _build_vendor(model)
    async with _cache_lock:
        _cache[cache_key] = (now, vendor)
    logger.info("vendor_router: type=%s provider=%s model=%s", model_type, model.model_provider, model.model_name)
    return vendor


async def get_model(db: AsyncSession, model_type: str) -> Optional[AiModel]:
    """仅取 AiModel 记录, 不构造 vendor. 用于已有 vendor 抽象的业务(如 agnes_image)."""
    return await _resolve_model(db, model_type)


def invalidate_cache(model_type: Optional[str] = None) -> None:
    """手动清缓存(管理后台改 default_flag 后调用)."""
    if model_type is None:
        _cache.clear()
    else:
        _cache.pop(model_type, None)
