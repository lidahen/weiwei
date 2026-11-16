"""商汤 senseaudio vendor —— 已重构为薄包装, 委托 SenseaudioUniversalVendor。

DEPRECATED: 后续调用方应改为:
    from app.services.vendor_router import get_vendor
    v = await get_vendor(db, "MUSIC")
    await v.submit_song(...)

本类保留仅为向后兼容 ai-music-voice-profile 变更的旧单元测试和 import 路径。
"""
from __future__ import annotations

import warnings
from typing import Optional

from app.services.music_vendor.senseaudio_universal import SenseaudioUniversalVendor

warnings.warn(
    "SenseaudioVendor 已 deprecated, 请使用 vendor_router.get_vendor(db, model_type)",
    DeprecationWarning,
    stacklevel=2,
)


class SenseaudioVendor(SenseaudioUniversalVendor):
    """DEPRECATED wrapper —— 默认 model_name='senseaudio-music-2.0-260626', 仅用于向后兼容."""

    def __init__(
        self,
        *,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        music_model: Optional[str] = None,
        timeout: Optional[int] = None,
    ):
        from app.config import settings

        super().__init__(
            api_key=api_key if api_key is not None else (settings.senseaudio_api_key or ""),
            base_url=base_url or settings.senseaudio_base_url,
            model_name=music_model or settings.senseaudio_music_model,
            timeout=timeout or settings.senseaudio_timeout,
        )

    # 旧测试还调 upload_voice_sample / synthesize_tts 等, 委托到父类
    async def upload_voice_sample(self, **_kwargs):
        raise NotImplementedError(
            "商汤不开放自助训练, 请使用 SiliconflowCloneVendor"
        )

    async def synthesize_tts(self, **_kwargs):
        # 旧版 senseaudio.vendor 没实现 TTS, 仅抛错
        raise NotImplementedError(
            "请使用 vendor_router.get_vendor(db, 'TTS') 或 SiliconflowCloneVendor"
        )
