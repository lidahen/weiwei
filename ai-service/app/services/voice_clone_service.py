"""用户私有音色库业务编排。

ai-service 不直连 DB, 仅做 vendor 调用 + 业务校验, 落库在 BFF 层。
"""
from __future__ import annotations

import logging
from typing import Optional

from app.config import settings
from app.services.music_vendor.base import (
    MusicVendorError,
)
from app.services.music_vendor.siliconflow_clone import SiliconflowCloneVendor

logger = logging.getLogger(__name__)


def _resolve_vendor() -> SiliconflowCloneVendor:
    """克隆音色仅由硅基流动实现。"""
    return SiliconflowCloneVendor()


def _validate_audio_duration(duration_ms: Optional[int]) -> None:
    """校验音频时长在配置范围内。"""
    if duration_ms is None:
        return  # 不强制要求; vendor 自身也会校验
    duration_s = duration_ms / 1000.0
    if duration_s < settings.siliconflow_voice_min_duration_s:
        raise MusicVendorError(
            f"样本时长过短({duration_s:.1f}s), 最少 {settings.siliconflow_voice_min_duration_s}s"
        )
    if duration_s > settings.siliconflow_voice_max_duration_s:
        raise MusicVendorError(
            f"样本时长过长({duration_s:.1f}s), 最多 {settings.siliconflow_voice_max_duration_s}s"
        )


async def upload_and_train(
    *,
    audio_bytes: bytes,
    text: str,
    name: str,
    duration_ms: Optional[int] = None,
    model: Optional[str] = None,
    consent_signed: bool = False,
    consent_ip: Optional[str] = None,
) -> dict:
    """上传用户声音样本并训练克隆音色。

    返回 (供 BFF 落库):
      {
        "vendor": "siliconflow",
        "vendor_voice_id": str(uri),
        "vendor_model": str,
        "name": str,
        "status": 2,   # 直接视为可用(硅基流动同步返回可用 voice)
        "consent_signed": True,
        "consent_ip": str|None,
      }
    """
    if not consent_signed:
        # 强制合规: 必须签署授权协议才能上传样本
        raise MusicVendorError("请先签署声音克隆授权协议")
    _validate_audio_duration(duration_ms)

    vendor = _resolve_vendor()
    result = await vendor.upload_voice_sample(
        audio_bytes=audio_bytes,
        text=text,
        name=name,
        model=model,
    )
    return {
        "vendor": vendor.name,
        "vendor_voice_id": result["vendor_voice_id"],
        "vendor_model": result["vendor_model"],
        "name": name,
        "status": 2,  # 硅基流动同步返回, 视为可用
        "consent_signed": True,
        "consent_ip": consent_ip,
    }


async def synthesize_with_voice(
    *,
    text: str,
    voice_id: str,
    model: Optional[str] = None,
    response_format: str = "mp3",
    speed: float = 1.0,
) -> bytes:
    """用用户克隆音色合成语音。"""
    if not text or not text.strip():
        raise MusicVendorError("text 必填")
    if not voice_id or not voice_id.strip():
        raise MusicVendorError("voice_id 必填")
    vendor = _resolve_vendor()
    return await vendor.synthesize_tts(
        text=text,
        voice_id=voice_id,
        model=model,
        response_format=response_format,
        speed=speed,
    )
