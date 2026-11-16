"""商汤 senseaudio vendor 单元测试(用 respx mock httpx)。"""
from __future__ import annotations

import pytest
import respx
from httpx import Response

from app.config import settings
from app.services.music_vendor.base import (
    MusicVendorError,
    VendorNotConfiguredError,
)
from app.services.music_vendor.senseaudio import SenseaudioVendor


@pytest.fixture
def vendor():
    # 强制注入 api_key,绕过环境变量
    return SenseaudioVendor(api_key="test-key", timeout=5)


# ── 平台音色 ──


@pytest.mark.asyncio
async def test_list_platform_voices_normalizes_three_categories(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/get_voice").mock(
            return_value=Response(
                200,
                json={
                    "system_voice": [
                        {"voice_id": "sys1", "voice_name": "青年男声", "description": ["普通话"]}
                    ],
                    "voice_cloning": [
                        {"voice_id": "vc1", "voice_name": "克隆A", "description": ["少年"]}
                    ],
                    "voice_generation": [],
                    "base_resp": {"status_code": 0, "status_msg": "success"},
                },
            )
        )
        result = await vendor.list_platform_voices(voice_type="all")
    assert len(result) == 2
    categories = {r["category"] for r in result}
    assert categories == {"system", "voice_clone"}
    assert result[0]["vendor"] == "senseaudio"


@pytest.mark.asyncio
async def test_list_platform_voices_no_key_raises():
    """Universal vendor 在 __init__ 时就抛 VendorNotConfiguredError(更早更安全)."""
    with pytest.raises(VendorNotConfiguredError):
        SenseaudioVendor(api_key="")


# ── submit_song ──


@pytest.mark.asyncio
async def test_submit_song_returns_task_id(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        route = mock.post("/v2/music/song/create").mock(
            return_value=Response(200, json={"task_id": "music-task-xxx"})
        )
        task_id = await vendor.submit_song(
            prompt="夏日海边", style="流行", gender="female", expect_duration=120
        )
    assert task_id == "music-task-xxx"
    sent = route.calls.last.request
    body = sent.read()
    import json
    payload = json.loads(body)
    assert payload["model"] == settings.senseaudio_music_model
    assert payload["prompt"] == "夏日海边"
    assert payload["vocal_settings"]["gender"] == "female"
    assert payload["audio_settings"]["duration"] == 120


@pytest.mark.asyncio
async def test_submit_song_voice_id_in_vocal_settings(vendor):
    """平台音色翻唱: voice_id 注入 vocal_settings.voice_id。"""
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        route = mock.post("/v2/music/song/create").mock(
            return_value=Response(200, json={"task_id": "task-cover"})
        )
        await vendor.submit_song(
            lyrics="[verse]\n...歌词", voice_id="vc-male-001"
        )
    import json
    payload = json.loads(route.calls.last.request.read())
    assert payload["lyrics"].startswith("[verse]")
    assert payload["vocal_settings"]["voice_id"] == "vc-male-001"


# ── poll_song ──


@pytest.mark.asyncio
async def test_poll_song_pending(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.get("/v1/music/song/pending/abc").mock(
            return_value=Response(200, json={"task_id": "abc", "status": "PENDING"})
        )
        result = await vendor.poll_song("abc")
    assert result["status"] == "PENDING"
    assert result["songs"] == []


@pytest.mark.asyncio
async def test_poll_song_success_normalizes_songs(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.get("/v1/music/song/pending/abc").mock(
            return_value=Response(
                200,
                json={
                    "task_id": "abc",
                    "status": "SUCCESS",
                    "response": {
                        "task_id": "abc",
                        "data": [
                            {
                                "id": "m1",
                                "title": "潮汐记得",
                                "audio_url": "https://x/m.mp3",
                                "cover_url": "https://x/c.jpg",
                                "lyrics": "[verse]...",
                                "duration": 180,
                                "derived_audio": {"mp3": "https://x/m.mp3", "wav": "https://x/m.wav"},
                            }
                        ],
                    },
                },
            )
        )
        result = await vendor.poll_song("abc")
    assert result["status"] == "SUCCESS"
    assert len(result["songs"]) == 1
    song = result["songs"][0]
    assert song["id"] == "m1"
    assert song["duration_ms"] == 180_000  # 秒转毫秒
    assert song["derived_audio"]["wav"].endswith(".wav")


@pytest.mark.asyncio
async def test_poll_song_failed(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.get("/v1/music/song/pending/abc").mock(
            return_value=Response(
                200, json={"task_id": "abc", "status": "FAILED", "fail_reason": "内容违规"}
            )
        )
        result = await vendor.poll_song("abc")
    assert result["status"] == "FAILED"
    assert result["fail_reason"] == "内容违规"


@pytest.mark.asyncio
async def test_poll_song_upstream_error(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.get("/v1/music/song/pending/abc").mock(
            return_value=Response(500, text="upstream boom")
        )
        with pytest.raises(MusicVendorError):
            await vendor.poll_song("abc")


# ── draft_lyrics ──


@pytest.mark.asyncio
async def test_draft_lyrics_returns_data(vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/music/lyrics/create").mock(
            return_value=Response(
                200,
                json={
                    "task_id": "t1",
                    "data": [
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
                    ],
                },
            )
        )
        items = await vendor.draft_lyrics(prompt="夏日海边告别", expect_duration=180)
    assert len(items) == 1
    assert items[0]["text"].startswith("[verse]")


@pytest.mark.asyncio
async def test_draft_lyrics_empty_prompt_raises(vendor):
    with pytest.raises(MusicVendorError):
        await vendor.draft_lyrics(prompt="")


# ── 未实现能力 ──


@pytest.mark.asyncio
async def test_upload_voice_sample_not_implemented(vendor):
    with pytest.raises(NotImplementedError):
        await vendor.upload_voice_sample(audio_bytes=b"x", text="t", name="n", model="m")
