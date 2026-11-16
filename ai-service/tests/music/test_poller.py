"""MusicTaskPoller 单元测试。"""
from __future__ import annotations


import pytest

from app.services.music_vendor.base import (
    MusicFailedError,
    MusicTimeoutError,
)
from app.services.music_vendor.poller import MusicTaskPoller


class _FakeVendor:
    def __init__(self, results):
        self._results = list(results)
        self.calls = 0

    async def poll_song(self, task_id):
        self.calls += 1
        if not self._results:
            return {"task_id": task_id, "status": "PENDING", "songs": []}
        return self._results.pop(0)


@pytest.mark.asyncio
async def test_poller_returns_immediately_on_success():
    vendor = _FakeVendor(
        [{"task_id": "t", "status": "SUCCESS", "songs": [{"id": "m1"}], "fail_reason": None}]
    )
    poller = MusicTaskPoller(vendor, max_wait_s=10, interval_s=0.01)
    result = await poller.poll("t")
    assert result["status"] == "SUCCESS"
    assert vendor.calls == 1


@pytest.mark.asyncio
async def test_poller_waits_until_success():
    vendor = _FakeVendor(
        [
            {"task_id": "t", "status": "PENDING", "songs": []},
            {"task_id": "t", "status": "PENDING", "songs": []},
            {"task_id": "t", "status": "SUCCESS", "songs": [{"id": "m1"}]},
        ]
    )
    poller = MusicTaskPoller(vendor, max_wait_s=10, interval_s=0.01)
    result = await poller.poll("t")
    assert result["status"] == "SUCCESS"
    assert vendor.calls == 3


@pytest.mark.asyncio
async def test_poller_raises_on_failed():
    vendor = _FakeVendor(
        [{"task_id": "t", "status": "FAILED", "fail_reason": "内容违规"}]
    )
    poller = MusicTaskPoller(vendor, max_wait_s=10, interval_s=0.01)
    with pytest.raises(MusicFailedError, match="内容违规"):
        await poller.poll("t")


@pytest.mark.asyncio
async def test_poller_raises_on_timeout(monkeypatch):
    """PENDING 永远不结束, 用 mock 时间让 deadline 提前到达。"""
    vendor = _FakeVendor([])  # 永远 PENDING

    # monotonic: 第一次 1000.0(建立 deadline=1010.0), 之后一律 9999.0(已超时)
    state = {"n": 0}

    def fake_monotonic():
        state["n"] += 1
        if state["n"] == 1:
            return 1000.0
        return 9999.0  # 远大于 deadline

    monkeypatch.setattr(
        "app.services.music_vendor.poller.time.monotonic",
        fake_monotonic,
    )
    poller = MusicTaskPoller(vendor, max_wait_s=10, interval_s=0.01)
    with pytest.raises(MusicTimeoutError):
        await poller.poll("t")
