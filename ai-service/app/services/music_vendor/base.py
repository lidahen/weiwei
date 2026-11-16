"""AI 音乐供应商抽象接口。

约束: 不绑定具体 vendor, 每个 vendor 仅实现自己支持的子集,
未实现的方法抛 NotImplementedError, 由上层 service 路由。
"""
from __future__ import annotations

from typing import Optional, Protocol, runtime_checkable


class MusicVendorError(Exception):
    """vendor 调用异常, message 可直接展示给上层。"""


class MusicTimeoutError(MusicVendorError):
    """异步任务轮询超时。"""


class MusicFailedError(MusicVendorError):
    """异步任务上游明确失败, message 携带 fail_reason。"""


class VendorNotConfiguredError(MusicVendorError):
    """vendor 未配置 API Key。"""


@runtime_checkable
class MusicVendor(Protocol):
    """音乐供应商抽象接口。

    能力维度:
      - 平台音色列表: list_platform_voices
      - 文生歌/翻唱: submit_song + poll_song
      - AI 歌词: draft_lyrics + revise_lyrics
      - 用户克隆音色: upload_voice_sample
      - 克隆 TTS: synthesize_tts
    """

    name: str

    async def list_platform_voices(self, voice_type: str = "all") -> list[dict]:
        """列出平台音色(系统/克隆/生成/全部)。

        返回: [{voice_id, voice_name, description, created_time, category}, ...]
        """
        ...

    async def submit_song(
        self,
        *,
        prompt: Optional[str] = None,
        style: Optional[str] = None,
        lyrics: Optional[str] = None,
        prebuilt_lyrics: Optional[dict] = None,
        voice_id: Optional[str] = None,
        gender: str = "any",
        mode: str = "sing",
        bpm: Optional[int] = None,
        key_scale: Optional[str] = None,
        time_signature: Optional[str] = None,
        audio_format: str = "mp3",
        expect_duration: Optional[int] = None,
        language: str = "zh",
    ) -> str:
        """提交歌曲生成任务, 返回 vendor task_id(异步)。"""
        ...

    async def poll_song(self, task_id: str) -> dict:
        """查询歌曲任务状态。

        返回标准化结构:
          {
            "task_id": str,
            "status": "PENDING" | "SUCCESS" | "FAILED",
            "fail_reason": str | None,
            "songs": [
                {
                    "id": str,
                    "title": str,
                    "audio_url": str,
                    "cover_url": str,
                    "lyrics": str,
                    "duration_ms": int,
                    "derived_audio": {"mp3": str, "wav": str}
                }
            ]
          }
        """
        ...

    async def draft_lyrics(
        self,
        *,
        prompt: str,
        style: Optional[str] = None,
        rhyme: Optional[str] = None,
        language: str = "zh",
        expect_duration: Optional[int] = None,
        additional_prompt: Optional[str] = None,
    ) -> list[dict]:
        """AI 写词(同步返回 data[])。"""
        ...

    async def revise_lyrics(
        self,
        *,
        original_lyrics: str,
        instruction: str,
    ) -> list[dict]:
        """AI 歌词改写。"""
        ...

    async def upload_voice_sample(
        self,
        *,
        audio_bytes: bytes,
        text: str,
        name: str,
        model: str,
    ) -> dict:
        """上传样本训练克隆音色。

        返回 {"vendor_voice_id": str, "vendor_model": str}
        """
        ...

    async def synthesize_tts(
        self,
        *,
        text: str,
        voice_id: str,
        model: str,
        response_format: str = "mp3",
        speed: float = 1.0,
    ) -> bytes:
        """用克隆音色合成语音, 返回音频二进制。"""
        ...
