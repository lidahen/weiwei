"""硅基流动 克隆音色供应商适配器。

覆盖: 用户上传样本训练克隆音色 / 用克隆音色合成 TTS。
不覆盖: 文生歌 / 平台音色列表 / AI 写词(走商汤 senseaudio)。

复用现有 tts_base_url / embedding_api_key(优先) / tts_api_key 作为鉴权源,
保证 zero-config 接入。
"""
from __future__ import annotations

import logging
import re
from typing import Optional

import httpx

from app.config import settings
from app.services.music_vendor.base import MusicVendorError, VendorNotConfiguredError

logger = logging.getLogger(__name__)


class SiliconflowCloneVendor:
    name = "siliconflow"

    def __init__(
        self,
        *,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        clone_model: Optional[str] = None,
        timeout: Optional[int] = None,
    ):
        # tts_base_url 默认含 /v1, 去掉以避免与路径再拼一次
        raw = (base_url or settings.tts_base_url or settings.embedding_base_url).rstrip("/")
        if raw.endswith("/v1"):
            raw = raw[:-3]
        self.base_url = raw
        # 复用现有 key,与 tts_service._resolve_api_key 一致
        self.api_key = (
            api_key
            or settings.siliconflow_voice_api_key
            or settings.tts_api_key
            or settings.embedding_api_key
            or ""
        ).strip()
        self.clone_model = clone_model or settings.siliconflow_voice_clone_model
        # 合成与训练是两个端点：/uploads/audio/voice 训练用 clone_model（IndexTTS-2），
        # /audio/speech 合成用 tts_model（CosyVoice2）——IndexTTS-2 在合成端点是 403 Model disabled
        self.tts_model = settings.tts_model
        self.timeout = timeout or settings.tts_timeout

    def _require_key(self) -> str:
        if not self.api_key:
            raise VendorNotConfiguredError(
                "硅基流动未配置: 请在 .env 中设置 SILICONFLOW_VOICE_API_KEY "
                "(或复用 TTS_API_KEY / EMBEDDING_API_KEY)"
            )
        return self.api_key

    async def upload_voice_sample(
        self,
        *,
        audio_bytes: bytes,
        text: str,
        name: str,
        model: Optional[str] = None,
    ) -> dict:
        """上传样本训练克隆音色。

        返回 {"vendor_voice_id": str(uri), "vendor_model": str}
        """
        if not audio_bytes:
            raise MusicVendorError("audio_bytes 不能为空")
        if not text or not text.strip():
            raise MusicVendorError("text(样本对应文本)必填, 用于一致性校验")
        if not name or not name.strip():
            raise MusicVendorError("name(音色别名)必填")

        use_model = model or self.clone_model
        url = f"{self.base_url}/v1/uploads/audio/voice"
        headers = {"Authorization": f"Bearer {self._require_key()}"}

        # 上游 customName 仅允许字母/数字/_/-（中文别名会被 400 code=20048 拒绝），
        # 合法字符集外的字符替换为 _；系统内显示名不受影响（落库仍用原始 name）
        safe_name = re.sub(r"[^A-Za-z0-9_-]", "_", name).strip("_")[:64] or "custom_voice"

        # multipart/form-data: file + model + customName + text
        files = {"file": ("sample.wav", audio_bytes, "audio/wav")}
        data = {
            "model": use_model,
            "customName": safe_name,
            "text": text,
        }
        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(
                    connect=10.0,
                    read=self.timeout,
                    write=30.0,
                    pool=10.0,
                )
            ) as client:
                resp = await client.post(url, headers=headers, files=files, data=data)
        except httpx.TimeoutException as exc:
            raise MusicVendorError(f"硅基流动上传超时: {exc}") from exc
        except httpx.HTTPError as exc:
            raise MusicVendorError(f"硅基流动上传失败: {exc}") from exc

        if resp.status_code != 200:
            raise MusicVendorError(
                f"硅基流动上传失败(上游 {resp.status_code}): {resp.text[:200]}"
            )
        body = resp.json()
        uri = body.get("uri")
        if not uri:
            raise MusicVendorError("硅基流动未返回 uri")
        return {
            "vendor_voice_id": uri,
            "vendor_model": use_model,
        }

    async def synthesize_tts(
        self,
        *,
        text: str,
        voice_id: str,
        model: Optional[str] = None,
        response_format: str = "mp3",
        speed: float = 1.0,
    ) -> bytes:
        """用克隆 voice_id 合成 TTS, 返回音频二进制。"""
        if not text or not text.strip():
            raise MusicVendorError("text 必填")
        if not voice_id or not voice_id.strip():
            raise MusicVendorError("voice_id 必填")

        use_model = model or self.tts_model
        url = f"{self.base_url}/v1/audio/speech"
        headers = {
            "Authorization": f"Bearer {self._require_key()}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": use_model,
            "input": text,
            "voice": voice_id,
            "response_format": response_format,
            # 防御 None/非数值：上游 400 会原样透传，这里兜底为 1.0
            "speed": max(0.25, min(4.0, float(speed) if speed is not None else 1.0)),
        }
        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(
                    connect=10.0,
                    read=self.timeout,
                    write=10.0,
                    pool=10.0,
                )
            ) as client:
                resp = await client.post(url, headers=headers, json=payload)
        except httpx.TimeoutException as exc:
            raise MusicVendorError(f"硅基流动 TTS 超时: {exc}") from exc
        except httpx.HTTPError as exc:
            raise MusicVendorError(f"硅基流动 TTS 失败: {exc}") from exc

        if resp.status_code != 200:
            if resp.status_code == 402:
                raise MusicVendorError(
                    "硅基流动账户余额不足（克隆音色 TTS 为付费服务），请充值后重试")
            raise MusicVendorError(
                f"硅基流动 TTS 失败(上游 {resp.status_code}): {resp.text[:200]}"
            )
        audio = resp.content
        if not audio:
            raise MusicVendorError("硅基流动 TTS 返回空音频")
        return audio

    # ── 未实现能力(走商汤) ──
    async def list_platform_voices(self, **_kwargs):
        raise NotImplementedError("请使用 SenseaudioVendor.list_platform_voices")

    async def submit_song(self, **_kwargs):
        raise NotImplementedError("请使用 SenseaudioVendor.submit_song")

    async def poll_song(self, _task_id: str):
        raise NotImplementedError("请使用 SenseaudioVendor.poll_song")

    async def draft_lyrics(self, **_kwargs):
        raise NotImplementedError("请使用 SenseaudioVendor.draft_lyrics")

    async def revise_lyrics(self, **_kwargs):
        raise NotImplementedError("请使用 SenseaudioVendor.revise_lyrics")
