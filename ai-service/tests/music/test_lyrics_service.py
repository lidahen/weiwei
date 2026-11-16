"""lyrics_service 业务层单元测试。"""
from __future__ import annotations

from unittest.mock import AsyncMock, patch

import pytest

from app.services import lyrics_service
from app.services.music_vendor.base import MusicVendorError


@pytest.mark.asyncio
async def test_draft_normalizes_vendor_result():
    """draft() 标准化 vendor 返回值(text→lyrics 字段)。"""
    fake_vendor_items = [
        {
            "text": "[verse]\n海风吹过",
            "title": "潮汐记得",
            "bpm": 92,
            "key_scale": "C",
            "time_signature": "4/4",
            "duration": 180,
            "vocal_language": "zh",
            "caption": "流行抒情",
        }
    ]
    with patch.object(
        lyrics_service,
        "_resolve_vendor",
        return_value=_FakeVendor(draft_lyrics=AsyncMock(return_value=fake_vendor_items)),
    ):
        items = await lyrics_service.draft(prompt="夏日海边")
    assert items[0]["lyrics"] == "[verse]\n海风吹过"
    assert items[0]["title"] == "潮汐记得"
    assert items[0]["bpm"] == 92


@pytest.mark.asyncio
async def test_draft_empty_prompt_raises():
    with pytest.raises(MusicVendorError):
        await lyrics_service.draft(prompt="")


@pytest.mark.asyncio
async def test_draft_duration_out_of_range():
    with pytest.raises(MusicVendorError):
        await lyrics_service.draft(prompt="x", expect_duration=5)
    with pytest.raises(MusicVendorError):
        await lyrics_service.draft(prompt="x", expect_duration=1000)


@pytest.mark.asyncio
async def test_revise_normalizes_vendor_result():
    fake_items = [{"text": "[verse]\n改写后", "title": "新版"}]
    with patch.object(
        lyrics_service,
        "_resolve_vendor",
        return_value=_FakeVendor(revise_lyrics=AsyncMock(return_value=fake_items)),
    ):
        items = await lyrics_service.revise(
            original_lyrics="[verse]\n原文", instruction="更抒情"
        )
    assert items[0]["lyrics"] == "[verse]\n改写后"


class _FakeVendor:
    """仅实现 lyrics_service 调用的两个方法。"""

    def __init__(self, draft_lyrics=None, revise_lyrics=None):
        self.draft_lyrics = draft_lyrics
        self.revise_lyrics = revise_lyrics

    async def draft_lyrics(self, **_):
        raise NotImplementedError

    async def revise_lyrics(self, **_):
        raise NotImplementedError
