"""AI 音乐作品业务编排(文生歌 / 翻唱 / 状态轮询)。

落库在 BFF 层, ai-service 只负责 vendor 调用 + 标准化。
"""
from __future__ import annotations

import logging
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.services.music_vendor.base import (
    MusicVendorError,
)
from app.services.music_vendor.poller import MusicTaskPoller
from app.services.music_vendor.senseaudio_universal import SenseaudioUniversalVendor

logger = logging.getLogger(__name__)


async def _resolve_vendor(db: AsyncSession, model_type: str = "MUSIC") -> SenseaudioUniversalVendor:
    """解析 vendor: 优先用 model_id (若传), 否则取 default_flag=1 的默认模型."""
    from app.services.vendor_router import _build_vendor, _resolve_model
    model = await _resolve_model(db, model_type)
    if model is None:
        raise MusicVendorError(f"未配置 {model_type} 模型, 请先在模型管理中添加")
    return _build_vendor(model)


async def submit_song(
    db: AsyncSession,
    *,
    prompt: Optional[str] = None,
    style: Optional[str] = None,
    lyrics: Optional[str] = None,
    voice_id: Optional[str] = None,
    gender: str = "any",
    mode: str = "sing",
    bpm: Optional[int] = None,
    key_scale: Optional[str] = None,
    time_signature: Optional[str] = None,
    audio_format: str = "mp3",
    expect_duration: Optional[int] = None,
    language: str = "zh",
    model_id: Optional[int] = None,  # 用户在前端选的模型
) -> str:
    """提交歌曲生成任务, 返回 vendor task_id(BFF 落库后再返回 work_id)。

    model_id 优先级:
      1. 用户显式传入 → 按 model_id 查 ai_model, 用其 model_name
      2. 未传 → vendor_router 取默认模型( default_flag=1 )
    """
    vendor = await _resolve_vendor(db, "MUSIC")
    if model_id is not None:
        from app.services.vendor_router import _resolve_model_by_id
        chosen = await _resolve_model_by_id(db, model_id)
        if chosen is None:
            raise MusicVendorError(f"模型 id={model_id} 不存在或已禁用")
        if chosen.model_type != "MUSIC":
            raise MusicVendorError(f"模型 id={model_id} 不是音乐类 (type={chosen.model_type})")
        # 用 .override_model 不改 vendor 实例的 model_name
        vendor = vendor.override_model(chosen.model_name)
        logger.info("submit_song using model_id=%s model_name=%s", model_id, chosen.model_name)
    return await vendor.submit_song(
        prompt=prompt,
        style=style,
        lyrics=lyrics,
        voice_id=voice_id,
        gender=gender,
        mode=mode,
        bpm=bpm,
        key_scale=key_scale,
        time_signature=time_signature,
        audio_format=audio_format,
        expect_duration=expect_duration,
        language=language,
    )


async def poll_song_status(db: AsyncSession, task_id: str) -> dict:
    """轮询单次(供 BFF 主动查询, 不在 ai-service 内做长轮询)。"""
    vendor = await _resolve_vendor(db, "MUSIC")
    return await vendor.poll_song(task_id)


async def poll_until_done(db: AsyncSession, task_id: str) -> dict:
    """ai-service 内部长轮询直到终态(供 BFF 同步调用模式)。"""
    vendor = await _resolve_vendor(db, "MUSIC")
    poller = MusicTaskPoller(vendor)
    return await poller.poll(task_id)
