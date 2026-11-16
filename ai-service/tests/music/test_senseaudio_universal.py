"""SenseaudioUniversalVendor 路由测试 — 覆盖 6 个能力分支 + NotImplementedError 占位"""
from __future__ import annotations

import pytest
import respx
from httpx import Response

from app.services.music_vendor.base import (
    MusicVendorError,
    VendorNotConfiguredError,
)
from app.services.music_vendor.senseaudio_universal import (
    SenseaudioRejectedError,
    SenseaudioUniversalVendor,
    normalize_image_size,
    parse_supported_sizes,
)


@pytest.fixture
def img_vendor():
    return SenseaudioUniversalVendor(api_key="test-key", model_name="senseaudio-image-2.0-260319", timeout=5)


@pytest.fixture
def music_vendor():
    return SenseaudioUniversalVendor(api_key="test-key", model_name="senseaudio-music-2.0-260626", timeout=5)


@pytest.fixture
def video_vendor():
    return SenseaudioUniversalVendor(api_key="test-key", model_name="doubao-seedance-2-0-260128", timeout=5)


# ── 路由判定 ──


def test_route_image():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-image-2.0-260319")
    assert v._route() == "image"


def test_route_music():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-music-2.0-260626")
    assert v._route() == "music"


def test_route_video():
    v = SenseaudioUniversalVendor(api_key="k", model_name="doubao-seedance-2-0-260128")
    assert v._route() == "video"


def test_route_tts():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-tts-1.5-260319")
    assert v._route() == "tts"


def test_route_asr():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-asr-pro-1.5-260319")
    assert v._route() == "asr"


def test_route_isolate():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-voice-isolation-1.5")
    assert v._route() == "isolate"


def test_route_audio_a1():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-a1")
    assert v._route() == "audio_gen"


def test_route_default_chat():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-s2-flash")
    assert v._route() == "chat"


# ── 构造校验 ──


def test_empty_api_key_raises():
    with pytest.raises(VendorNotConfiguredError):
        SenseaudioUniversalVendor(api_key="", model_name="x")


# ── IMAGE ──


@pytest.mark.asyncio
async def test_image_generate(img_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        route = mock.post("/v1/image/sync").mock(
            return_value=Response(200, json={"url": "https://x.test/img.jpg"})
        )
        result = await img_vendor.image_generate(prompt="海边少女", size="1536x864")
    assert result["url"] == "https://x.test/img.jpg"
    import json
    payload = json.loads(route.calls.last.request.read())
    assert payload["model"] == "senseaudio-image-2.0-260319"
    assert payload["prompt"] == "海边少女"
    assert payload["size"] == "1536x864"


# ── IMAGE 尺寸归一化（商汤只认官方档位，Agnes 档位表需收敛）──

_SUPPORTED = ["1024x1024", "1536x864", "864x1536", "2048x1152", "3840x2160"]


def test_parse_supported_sizes_from_dict():
    assert parse_supported_sizes({"sizes": ["1024x1024", "2048x1152"]}) == ["1024x1024", "2048x1152"]


def test_parse_supported_sizes_from_json_string():
    raw = '{"price_per_img": 0.5, "sizes": ["1024x1024", "3840x2160"]}'
    assert parse_supported_sizes(raw) == ["1024x1024", "3840x2160"]


def test_parse_supported_sizes_falls_back_on_garbage():
    default = ["1024x1024", "1536x864", "864x1536", "2048x1152", "3840x2160"]
    assert parse_supported_sizes(None) == default
    assert parse_supported_sizes("{not json") == default
    assert parse_supported_sizes({"sizes": ["not-a-size"]}) == default


def test_normalize_keeps_supported_size_untouched():
    for s in _SUPPORTED:
        assert normalize_image_size(s, _SUPPORTED) == s


def test_normalize_maps_4_3_to_closest_supported():
    # 2K 4:3 (2304x1728) 不在商汤档位内 → 就近取比例最接近的 1:1
    assert normalize_image_size("2304x1728", _SUPPORTED) == "1024x1024"
    assert normalize_image_size("1728x2304", _SUPPORTED) == "864x1536"


def test_normalize_maps_16_9_to_larger_supported():
    assert normalize_image_size("2624x1472", _SUPPORTED) == "2048x1152"


def test_normalize_unparsable_size_falls_back_to_first():
    assert normalize_image_size("bogus", _SUPPORTED) == "1024x1024"
    assert normalize_image_size("", _SUPPORTED) == "1024x1024"


@pytest.mark.asyncio
async def test_image_rejection_raises_dedicated_error(img_vendor):
    """400 + upstream_invalid_request/400100 → 内容被拒专用异常（带可操作文案）。"""
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/image/sync").mock(
            return_value=Response(
                400,
                json={
                    "code": "upstream_invalid_request",
                    "message": "图片生成请求无效，请检查输入内容",
                    "ref_code": 400100,
                },
            )
        )
        with pytest.raises(SenseaudioRejectedError) as exc:
            await img_vendor.image_generate(prompt="x", size="1024x1024")
    assert "内容安全审核" in str(exc.value)


@pytest.mark.asyncio
async def test_image_other_400_stays_generic_error(img_vendor):
    """其它 400 不误判为内容拒绝，保留上游报文便于排查。"""
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/image/sync").mock(
            return_value=Response(400, json={"code": "invalid_api_key"})
        )
        with pytest.raises(MusicVendorError) as exc:
            await img_vendor.image_generate(prompt="x", size="1024x1024")
    assert not isinstance(exc.value, SenseaudioRejectedError)


# ── MUSIC ──


@pytest.mark.asyncio
async def test_music_submit_song(music_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v2/music/song/create").mock(
            return_value=Response(200, json={"task_id": "music-task-universal"})
        )
        tid = await music_vendor.submit_song(prompt="海边", style="流行", gender="female", expect_duration=60)
    assert tid == "music-task-universal"


@pytest.mark.asyncio
async def test_music_poll_success(music_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.get("/v1/music/song/pending/t1").mock(
            return_value=Response(200, json={
                "task_id": "t1", "status": "SUCCESS",
                "response": {"data": [{"id": "m1", "title": "t", "audio_url": "u", "duration": 60}]}
            })
        )
        r = await music_vendor.poll_song("t1")
    assert r["status"] == "SUCCESS"
    assert r["songs"][0]["duration_ms"] == 60000


@pytest.mark.asyncio
async def test_music_draft_lyrics(music_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/music/lyrics/create").mock(
            return_value=Response(200, json={"data": [{"text": "[verse]\n..."}]})
        )
        items = await music_vendor.draft_lyrics(prompt="海边")
    assert len(items) == 1


# ── VIDEO ──


@pytest.mark.asyncio
async def test_video_create(video_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/video/create").mock(
            return_value=Response(200, json={"task_id": "video-task-1"})
        )
        tid = await video_vendor.create_video(
            content=[{"type": "text", "text": "海边"}],
            resolution="720p", ratio="16:9", duration=15
        )
    assert tid == "video-task-1"


@pytest.mark.asyncio
async def test_video_poll(video_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.get("/v1/video/status").mock(
            return_value=Response(200, json={
                "task_id": "v1", "status": "completed", "video_url": "https://x/v.mp4", "duration": 15
            })
        )
        r = await video_vendor.poll_video("v1")
    assert r["status"] == "completed"
    assert r["video_url"] == "https://x/v.mp4"


# ── 已实现能力的参数契约 ──


@pytest.mark.asyncio
async def test_synthesize_tts_requires_voice_id():
    """TTS 已于 2026-10-03 实装（POST /v1/t2a_v2），voice_id 为必填参数。

    历史沿革（避免再次误判为回归）：本用例原名 test_synthesize_tts_not_implemented，
    写于 2026-09-10 尚未实现的时期，断言抛 NotImplementedError 且信息含 /v1/t2a_v2。
    2026-10-03 提交 16e4c03 实装 TTS 后未同步更新本用例；当时测试套件连收集都失败
    （缺 respx / python-multipart / fakeredis），因此这个红灯长期未被任何人看到。
    """
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-tts-1.5-260319")
    with pytest.raises(MusicVendorError, match="voice_id"):
        await v.synthesize_tts(text="x")


@pytest.mark.asyncio
async def test_transcribe_not_implemented():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-asr-pro-1.5-260319")
    with pytest.raises(NotImplementedError, match="/v1/audio/transcriptions"):
        await v.transcribe()


@pytest.mark.asyncio
async def test_isolate_vocals_not_implemented():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-voice-isolation-1.5")
    with pytest.raises(NotImplementedError, match="/v1/isolation/sync"):
        await v.isolate_vocals()


@pytest.mark.asyncio
async def test_generate_sfx_not_implemented():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-sfx-1.0-260626")
    with pytest.raises(NotImplementedError, match="/v1/sound-effects/generations"):
        await v.generate_sfx()


@pytest.mark.asyncio
async def test_start_realtime_not_implemented():
    v = SenseaudioUniversalVendor(api_key="k", model_name="senseaudio-realtime-1.0")
    with pytest.raises(NotImplementedError, match="/v1/realtime/invoke"):
        await v.start_realtime()


# ── 错误处理 ──


@pytest.mark.asyncio
async def test_image_upstream_error(img_vendor):
    with respx.mock(base_url="https://api.senseaudio.cn") as mock:
        mock.post("/v1/image/sync").mock(return_value=Response(500, text="upstream"))
        with pytest.raises(MusicVendorError, match="500"):
            await img_vendor.image_generate(prompt="x")
