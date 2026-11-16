"""AI 音乐 FastAPI 请求/响应 schema。"""
from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class _CamelModel(BaseModel):
    """接受 camelCase 别名, 保持与前端/项目其他 schema 一致。"""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


# ── 平台音色 ──
class PlatformVoiceOut(BaseModel):
    voice_id: str
    voice_name: str
    description: list[str] = []
    category: str  # system / voice_clone / voice_generation
    vendor: str
    created_time: Optional[str] = None


class PlatformVoiceListResp(BaseModel):
    voices: list[PlatformVoiceOut]


# ── 用户私有音色 ──
class VoiceProfileUploadReq(BaseModel):
    """multipart 上传: file + 字段。 ai-service 仅接收字段, 文件由路由层独立读取。"""

    name: str = Field(..., min_length=1, max_length=64)
    text: str = Field(..., min_length=1, max_length=1024, description="样本朗读文本")
    duration_ms: Optional[int] = Field(None, ge=0)
    consent_signed: bool = Field(False, description="用户已签署授权")
    consent_ip: Optional[str] = None


class VoiceProfileUploadResp(BaseModel):
    vendor: str
    vendor_voice_id: str
    vendor_model: str
    name: str
    status: int  # 2=可用
    consent_signed: bool


# ── AI 歌词 ──
class LyricsDraftReq(BaseModel):
    prompt: str = Field(..., min_length=1)
    style: Optional[str] = None
    rhyme: Optional[str] = None
    language: str = "zh"
    expect_duration: Optional[int] = Field(None, ge=10, le=600)
    additional_prompt: Optional[str] = None


class LyricsItem(BaseModel):
    title: Optional[str] = None
    lyrics: Optional[str] = None
    caption: Optional[str] = None
    bpm: Optional[int] = None
    key_scale: Optional[str] = None
    time_signature: Optional[str] = None
    duration: Optional[float] = None
    vocal_language: Optional[str] = None


class LyricsDraftResp(BaseModel):
    candidates: list[LyricsItem]


class LyricsReviseReq(BaseModel):
    original_lyrics: str = Field(..., min_length=1)
    instruction: str = Field(..., min_length=1)


class LyricsReviseResp(BaseModel):
    candidates: list[LyricsItem]


# ── 音乐作品 ──
SongType = Literal["text_to_song", "platform_cover", "clone_tts", "instrumental"]


class SongSubmitReq(_CamelModel):
    type: SongType = "text_to_song"
    prompt: Optional[str] = Field(None, description="主题/自然语言描述")
    style: Optional[str] = None
    lyrics: Optional[str] = Field(None, description="用户自带歌词(含 [verse][chorus] 标签)")
    voice_id: Optional[str] = Field(None, description="平台音色 ID(翻唱)或克隆 voice_id")
    voice_profile_id: Optional[int] = Field(None, description="关联 ai_voice_profile.id(BFF 落库用)")
    gender: Literal["male", "female", "any", "duet"] = "any"
    mode: Literal["sing", "lyrics_to_instrumental", "instrumental"] = "sing"
    bpm: Optional[int] = None
    key_scale: Optional[str] = None
    time_signature: Optional[str] = None
    audio_format: str = "mp3"
    expect_duration: Optional[int] = Field(None, ge=10, le=600)
    language: str = "zh"
    title: Optional[str] = None
    style_tags: Optional[str] = None
    model_id: Optional[int] = Field(None, description="用户在前端选的 ai_model.id, 不传走默认")


class SongSubmitResp(BaseModel):
    vendor_task_id: str
    vendor: str = "senseaudio"


class SongStatusResp(BaseModel):
    vendor_task_id: str
    status: Literal["PENDING", "SUCCESS", "FAILED"]
    fail_reason: Optional[str] = None
    songs: list[dict] = Field(default_factory=list)


# ── TTS 合成 ──
class TTSReq(BaseModel):
    text: str = Field(..., min_length=1, max_length=128_000)
    voice_id: str = Field(..., min_length=1)
    response_format: Literal["mp3", "wav", "pcm", "opus"] = "mp3"
    speed: float = Field(1.0, ge=0.25, le=4.0)
