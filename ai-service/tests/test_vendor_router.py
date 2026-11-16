"""vendor_router 单测 —— mock DB 返回 AiModel"""
from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.services import vendor_router
from app.services.music_vendor.base import MusicVendorError
from app.services.music_vendor.senseaudio_universal import SenseaudioUniversalVendor


def _fake_model(provider="senseaudio", model_type="IMAGE", model_name="senseaudio-image-2.0-260319",
                api_key="sk-test", base_url="https://api.senseaudio.cn"):
    return SimpleNamespace(
        model_provider=provider,
        model_type=model_type,
        model_name=model_name,
        api_key=api_key,
        base_url=base_url,
    )


@pytest.fixture(autouse=True)
def _clear_cache():
    vendor_router._cache.clear()
    yield
    vendor_router._cache.clear()


def _mock_execute(obj):
    """构造 db.execute 的同步返回值 mock。

    model_resolver 统一入口按 `limit(2)` + `scalars().all()` 读取（用于探测"多条默认"），
    因此返回值必须同时支持 scalar_one_or_none() 与 scalars().all() 两套 API。
    """
    result = MagicMock()
    result.scalar_one_or_none.return_value = obj
    result.scalars.return_value.all.return_value = [obj] if obj is not None else []
    return result


@pytest.mark.asyncio
async def test_get_vendor_returns_senseaudio_universal():
    db = AsyncMock()
    db.execute = AsyncMock(return_value=_mock_execute(_fake_model()))
    v = await vendor_router.get_vendor(db, "IMAGE")
    assert isinstance(v, SenseaudioUniversalVendor)
    assert v.model_name == "senseaudio-image-2.0-260319"
    assert v.api_key == "sk-test"


@pytest.mark.asyncio
async def test_get_vendor_returns_none_when_not_configured():
    db = AsyncMock()
    db.execute = AsyncMock(return_value=_mock_execute(None))
    v = await vendor_router.get_vendor(db, "TTS")
    assert v is None


@pytest.mark.asyncio
async def test_get_vendor_caches_result():
    db = AsyncMock()
    db.execute = AsyncMock(return_value=_mock_execute(_fake_model()))
    v1 = await vendor_router.get_vendor(db, "IMAGE")
    v2 = await vendor_router.get_vendor(db, "IMAGE")
    assert v1 is v2  # cached
    assert db.execute.await_count == 1  # 只查一次


@pytest.mark.asyncio
async def test_get_vendor_raises_for_unsupported_provider():
    db = AsyncMock()
    db.execute = AsyncMock(return_value=_mock_execute(_fake_model(provider="agnes")))
    with pytest.raises(MusicVendorError, match="暂不抽象"):
        await vendor_router.get_vendor(db, "IMAGE")


@pytest.mark.asyncio
async def test_get_vendor_routes_music():
    db = AsyncMock()
    db.execute = AsyncMock(return_value=_mock_execute(_fake_model(
        model_type="MUSIC", model_name="senseaudio-music-2.0-260626"
    )))
    v = await vendor_router.get_vendor(db, "MUSIC")
    assert isinstance(v, SenseaudioUniversalVendor)
    assert v._route() == "music"


@pytest.mark.asyncio
async def test_get_vendor_routes_video():
    db = AsyncMock()
    db.execute = AsyncMock(return_value=_mock_execute(_fake_model(
        model_type="VIDEO", model_name="doubao-seedance-2-0-260128"
    )))
    v = await vendor_router.get_vendor(db, "VIDEO")
    assert isinstance(v, SenseaudioUniversalVendor)
    assert v._route() == "video"


def test_invalidate_cache():
    vendor_router._cache["IMAGE"] = (0, "x")
    vendor_router.invalidate_cache("IMAGE")
    assert "IMAGE" not in vendor_router._cache
    vendor_router._cache["IMAGE"] = (0, "x")
    vendor_router.invalidate_cache()
    assert vendor_router._cache == {}
