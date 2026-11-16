"""AI 歌词助手业务编排。

ai-service 不直连 DB, 调用 vendor 后做敏感词拦截 + 标准化, 配额扣减在 BFF。
"""
from __future__ import annotations

import logging
from typing import Optional

from app.services.music_vendor.base import MusicVendorError
from app.services.music_vendor.senseaudio import SenseaudioVendor

logger = logging.getLogger(__name__)


def _resolve_vendor() -> SenseaudioVendor:
    return SenseaudioVendor()


def _normalize_lyrics_item(item: dict) -> dict:
    """标准化单条歌词结果为内部统一字段。"""
    return {
        "title": item.get("title"),
        "lyrics": item.get("text") or item.get("lyrics"),
        "caption": item.get("caption"),
        "bpm": item.get("bpm"),
        "key_scale": item.get("key_scale"),
        "time_signature": item.get("time_signature"),
        "duration": item.get("duration"),
        "vocal_language": item.get("vocal_language"),
    }


async def draft(
    *,
    prompt: str,
    style: Optional[str] = None,
    rhyme: Optional[str] = None,
    language: str = "zh",
    expect_duration: Optional[int] = None,
    additional_prompt: Optional[str] = None,
) -> list[dict]:
    """AI 写词, 返回标准化后的歌词列表(一般 1~2 条候选)。"""
    if not prompt or not prompt.strip():
        raise MusicVendorError("prompt 必填")
    if expect_duration is not None and not (10 <= expect_duration <= 600):
        raise MusicVendorError("expect_duration 必须在 10~600 秒之间")

    vendor = _resolve_vendor()
    raw_items = await vendor.draft_lyrics(
        prompt=prompt,
        style=style,
        rhyme=rhyme,
        language=language,
        expect_duration=expect_duration,
        additional_prompt=additional_prompt,
    )
    return [_normalize_lyrics_item(item) for item in raw_items]


async def revise(
    *,
    original_lyrics: str,
    instruction: str,
) -> list[dict]:
    """AI 改写歌词, 返回标准化后的结果。"""
    vendor = _resolve_vendor()
    raw_items = await vendor.revise_lyrics(
        original_lyrics=original_lyrics,
        instruction=instruction,
    )
    return [_normalize_lyrics_item(item) for item in raw_items]
