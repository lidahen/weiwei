"""硅基流动克隆 vendor 单元测试。"""
from __future__ import annotations

import pytest
import respx
from httpx import Response

from app.services.music_vendor.base import (
    MusicVendorError,
    VendorNotConfiguredError,
)
from app.services.music_vendor.siliconflow_clone import SiliconflowCloneVendor


@pytest.fixture
def vendor():
    return SiliconflowCloneVendor(api_key="test-key", timeout=5)


# ── upload_voice_sample ──


@pytest.mark.asyncio
async def test_upload_voice_sample_returns_uri(vendor):
    with respx.mock(base_url="https://api.siliconflow.cn/v1") as mock:
        mock.post("/uploads/audio/voice").mock(
            return_value=Response(
                200, json={"uri": "speech:my-voice:abc:def"}
            )
        )
        result = await vendor.upload_voice_sample(
            audio_bytes=b"FAKEAUDIO",
            text="慢工出细活",
            name="my-voice",
            model="IndexTeam/IndexTTS-2",
        )
    assert result["vendor_voice_id"] == "speech:my-voice:abc:def"
    assert result["vendor_model"] == "IndexTeam/IndexTTS-2"


@pytest.mark.asyncio
async def test_upload_voice_sample_no_key_raises(monkeypatch):
    # 清空所有可能的 key 兜底源(settings 兜底可能从 .env 拿到值)
    monkeypatch.setattr("app.services.music_vendor.siliconflow_clone.settings.siliconflow_voice_api_key", "")
    monkeypatch.setattr("app.services.music_vendor.siliconflow_clone.settings.tts_api_key", "")
    monkeypatch.setattr("app.services.music_vendor.siliconflow_clone.settings.embedding_api_key", "")
    vendor = SiliconflowCloneVendor(api_key="")
    with pytest.raises(VendorNotConfiguredError):
        await vendor.upload_voice_sample(
            audio_bytes=b"x", text="t", name="n", model="m"
        )


@pytest.mark.asyncio
async def test_upload_voice_sample_empty_audio_raises(vendor):
    with pytest.raises(MusicVendorError):
        await vendor.upload_voice_sample(
            audio_bytes=b"", text="t", name="n", model="m"
        )


@pytest.mark.asyncio
async def test_upload_voice_sample_missing_text_raises(vendor):
    with pytest.raises(MusicVendorError):
        await vendor.upload_voice_sample(
            audio_bytes=b"x", text="", name="n", model="m"
        )


@pytest.mark.asyncio
async def test_upload_voice_sample_upstream_500(vendor):
    with respx.mock(base_url="https://api.siliconflow.cn/v1") as mock:
        mock.post("/uploads/audio/voice").mock(
            return_value=Response(500, text="boom")
        )
        with pytest.raises(MusicVendorError):
            await vendor.upload_voice_sample(
                audio_bytes=b"x", text="t", name="n", model="m"
            )


# ── synthesize_tts ──


@pytest.mark.asyncio
async def test_synthesize_tts_returns_audio(vendor):
    with respx.mock(base_url="https://api.siliconflow.cn/v1") as mock:
        route = mock.post("/audio/speech").mock(
            return_value=Response(
                200,
                content=b"FAKE_MP3_BYTES",
                headers={"content-type": "audio/mpeg"},
            )
        )
        audio = await vendor.synthesize_tts(
            text="你好世界", voice_id="speech:my-voice:abc:def"
        )
    assert audio == b"FAKE_MP3_BYTES"
    sent = route.calls.last.request
    import json
    payload = json.loads(sent.read())
    assert payload["voice"] == "speech:my-voice:abc:def"
    assert payload["response_format"] == "mp3"
    assert 0.25 <= payload["speed"] <= 4.0


@pytest.mark.asyncio
async def test_synthesize_tts_empty_text_raises(vendor):
    with pytest.raises(MusicVendorError):
        await vendor.synthesize_tts(text="", voice_id="x")


@pytest.mark.asyncio
async def test_synthesize_tts_speed_clamped(vendor):
    with respx.mock(base_url="https://api.siliconflow.cn/v1") as mock:
        route = mock.post("/audio/speech").mock(
            return_value=Response(200, content=b"x")
        )
        await vendor.synthesize_tts(text="hi", voice_id="x", speed=99.0)
    import json
    payload = json.loads(route.calls.last.request.read())
    assert payload["speed"] == 4.0  # clamp 上限


# ── 未实现能力 ──


@pytest.mark.asyncio
async def test_submit_song_not_implemented(vendor):
    with pytest.raises(NotImplementedError):
        await vendor.submit_song(prompt="x")


@pytest.mark.asyncio
async def test_draft_lyrics_not_implemented(vendor):
    with pytest.raises(NotImplementedError):
        await vendor.draft_lyrics(prompt="x")
