"""AI 音乐 FastAPI 路由。

【冻结区 — HOBBY】2026-10-15 起停止迭代（docs/ai/AI功能评估-多余与深化-2026-10-15.md）：
与二手交易主业零协同，仅作技术演示保留；不新增功能，仅在缺陷时最小修复。
若 ai_usage_daily 长期无真实调用量，可下线本路由（main.py 摘除 import 即可）。

用户身份: 由 BFF 在 header X-User-Id 传入, ai-service 不解析 token。
配额扣减 / 落库 / 内容审核在 BFF 层。
"""
from __future__ import annotations

import logging
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, UploadFile
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db

from app.schemas.music import (
    LyricsDraftReq,
    LyricsDraftResp,
    LyricsReviseReq,
    LyricsReviseResp,
    PlatformVoiceListResp,
    PlatformVoiceOut,
    SongStatusResp,
    SongSubmitReq,
    SongSubmitResp,
    TTSReq,
    VoiceProfileUploadResp,
)
from app.services import lyrics_service, music_service, voice_clone_service
from app.services.music_vendor.base import (
    MusicFailedError,
    MusicTimeoutError,
    MusicVendorError,
    VendorNotConfiguredError,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai/music", tags=["music"])


def _require_user(x_user_id: Optional[str] = Header(None)) -> str:
    if not x_user_id or not x_user_id.strip():
        raise HTTPException(status_code=401, detail={"code": "AUTH_REQUIRED", "message": "缺少用户身份"})
    return x_user_id.strip()


def _map_vendor_error(exc: MusicVendorError) -> HTTPException:
    if isinstance(exc, VendorNotConfiguredError):
        return HTTPException(status_code=503, detail={"code": "VENDOR_NOT_CONFIGURED", "message": str(exc)})
    if isinstance(exc, MusicTimeoutError):
        return HTTPException(status_code=504, detail={"code": "MUSIC_TIMEOUT", "message": str(exc)})
    if isinstance(exc, MusicFailedError):
        return HTTPException(status_code=502, detail={"code": "MUSIC_FAILED", "message": str(exc)})
    return HTTPException(status_code=502, detail={"code": "MUSIC_VENDOR_ERROR", "message": str(exc)})


# ════════════════════════════════════════════════════════════
# 平台音色(只读,缓存到 ai-service 本地 1h — 简化为不缓存,MVP)
# ════════════════════════════════════════════════════════════


@router.get("/voices/platform", response_model=PlatformVoiceListResp)
async def list_platform_voices(voice_type: str = "all"):
    """获取商汤平台预置音色(system/voice_clone/voice_generation/all)。"""
    from app.services.music_vendor.senseaudio import SenseaudioVendor

    try:
        vendor = SenseaudioVendor()
        raw = await vendor.list_platform_voices(voice_type=voice_type)
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc

    return PlatformVoiceListResp(
        voices=[PlatformVoiceOut(**item) for item in raw],
    )


# ════════════════════════════════════════════════════════════
# 用户私有音色(克隆)
# ════════════════════════════════════════════════════════════


@router.post("/voices", response_model=VoiceProfileUploadResp)
async def upload_voice(
    name: str = Form(..., min_length=1, max_length=64),
    text: str = Form(..., min_length=1),
    duration_ms: Optional[int] = Form(None),
    consent_signed: bool = Form(False),
    consent_ip: Optional[str] = Form(None),
    file: UploadFile = File(...),
    x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
):
    """上传样本训练克隆音色(硅基流动)。"""
    _require_user(x_user_id)
    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail={"code": "EMPTY_AUDIO", "message": "音频文件为空"})
    try:
        result = await voice_clone_service.upload_and_train(
            audio_bytes=audio_bytes,
            text=text,
            name=name,
            duration_ms=duration_ms,
            consent_signed=consent_signed,
            consent_ip=consent_ip,
        )
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc
    return VoiceProfileUploadResp(**result)


@router.post("/tts")
async def tts(req: TTSReq, x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    """用克隆 voice 合成语音, 返回音频二进制。"""
    _require_user(x_user_id)
    try:
        audio = await voice_clone_service.synthesize_with_voice(
            text=req.text,
            voice_id=req.voice_id,
            response_format=req.response_format,
            speed=req.speed,
        )
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc

    media_type = {
        "mp3": "audio/mpeg",
        "wav": "audio/wav",
        "pcm": "audio/pcm",
        "opus": "audio/opus",
    }.get(req.response_format, "application/octet-stream")
    from fastapi.responses import Response

    return Response(content=audio, media_type=media_type)


# ════════════════════════════════════════════════════════════
# AI 歌词助手
# ════════════════════════════════════════════════════════════


@router.post("/lyrics/draft", response_model=LyricsDraftResp)
async def draft_lyrics(req: LyricsDraftReq, x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    _require_user(x_user_id)
    try:
        items = await lyrics_service.draft(
            prompt=req.prompt,
            style=req.style,
            rhyme=req.rhyme,
            language=req.language,
            expect_duration=req.expect_duration,
            additional_prompt=req.additional_prompt,
        )
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc
    return LyricsDraftResp(candidates=items)


@router.post("/lyrics/revise", response_model=LyricsReviseResp)
async def revise_lyrics(req: LyricsReviseReq, x_user_id: Optional[str] = Header(None, alias="X-User-Id")):
    _require_user(x_user_id)
    try:
        items = await lyrics_service.revise(
            original_lyrics=req.original_lyrics,
            instruction=req.instruction,
        )
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc
    return LyricsReviseResp(candidates=items)


# ════════════════════════════════════════════════════════════
# 音乐作品
# ════════════════════════════════════════════════════════════


@router.post("/songs", response_model=SongSubmitResp)
async def submit_song(
    req: SongSubmitReq,
    db: AsyncSession = Depends(get_db),
    x_user_id: Optional[str] = Header(None, alias="X-User-Id"),
):
    """提交歌曲生成任务(异步)。返回 vendor task_id, BFF 落库后再返回 work_id。

    用户可传 model_id 选具体模型; 未传则用 ai_model 表 default_flag=1 的默认模型。
    """
    _require_user(x_user_id)
    if req.type == "platform_cover" and not req.voice_id:
        raise HTTPException(
            status_code=400,
            detail={"code": "VOICE_ID_REQUIRED", "message": "翻唱类型必须指定 voice_id"},
        )
    try:
        # model_id 是可选字段; 不传则 vendor_router 走默认
        model_id = getattr(req, "model_id", None)
        task_id = await music_service.submit_song(
            db=db,
            prompt=req.prompt,
            style=req.style,
            lyrics=req.lyrics,
            voice_id=req.voice_id,
            gender=req.gender,
            mode=req.mode,
            bpm=req.bpm,
            key_scale=req.key_scale,
            time_signature=req.time_signature,
            audio_format=req.audio_format,
            expect_duration=req.expect_duration,
            language=req.language,
            model_id=model_id,
        )
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc
    return SongSubmitResp(vendor_task_id=task_id)


@router.get("/songs/status", response_model=SongStatusResp)
async def poll_song_status(vendor_task_id: str, db: AsyncSession = Depends(get_db)):
    """查询歌曲生成任务状态(单次, 不在 ai-service 内做长轮询)。"""
    try:
        result = await music_service.poll_song_status(db, vendor_task_id)
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc
    return SongStatusResp(
        vendor_task_id=result["task_id"],
        status=result["status"],
        fail_reason=result.get("fail_reason"),
        songs=result.get("songs") or [],
    )


@router.post("/songs/wait", response_model=SongStatusResp)
async def wait_song(vendor_task_id: str, db: AsyncSession = Depends(get_db)):
    """同步等待歌曲生成完成(最多 senseaudio_poll_max_wait 秒)。"""
    try:
        result = await music_service.poll_until_done(db, vendor_task_id)
    except VendorNotConfiguredError as exc:
        raise _map_vendor_error(exc) from exc
    except (MusicTimeoutError, MusicFailedError) as exc:
        raise _map_vendor_error(exc) from exc
    except MusicVendorError as exc:
        raise _map_vendor_error(exc) from exc
    return SongStatusResp(
        vendor_task_id=result["task_id"],
        status=result["status"],
        fail_reason=result.get("fail_reason"),
        songs=result.get("songs") or [],
    )
