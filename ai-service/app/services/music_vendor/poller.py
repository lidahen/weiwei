"""异步任务轮询器。

通用: 给定 vendor 和 task_id, 循环调 vendor.poll_song 直到终态(SUCCESS/FAILED)
或超时。参考 agnes_video 的轮询模式。
"""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Protocol

from app.config import settings
from app.services.music_vendor.base import (
    MusicFailedError,
    MusicTimeoutError,
)

logger = logging.getLogger(__name__)


class _Pollable(Protocol):
    async def poll_song(self, task_id: str) -> dict: ...


class MusicTaskPoller:
    def __init__(
        self,
        vendor: _Pollable,
        *,
        max_wait_s: int | None = None,
        interval_s: int | None = None,
    ):
        self.vendor = vendor
        self.max_wait_s = max_wait_s or settings.senseaudio_poll_max_wait
        self.interval_s = interval_s or settings.senseaudio_poll_interval

    async def poll(self, task_id: str) -> dict:
        deadline = time.monotonic() + self.max_wait_s
        attempt = 0
        while True:
            attempt += 1
            result = await self.vendor.poll_song(task_id)
            status = result.get("status")
            if status == "SUCCESS":
                logger.info("music poll success task_id=%s attempts=%d", task_id, attempt)
                return result
            if status == "FAILED":
                reason = result.get("fail_reason") or "上游任务失败"
                logger.warning("music poll failed task_id=%s reason=%s", task_id, reason)
                raise MusicFailedError(f"歌曲生成失败: {reason}")
            # PENDING: 检查是否超时(max_wait_s=0 也应立即超时)
            if time.monotonic() >= deadline:
                logger.warning("music poll timeout task_id=%s attempts=%d", task_id, attempt)
                raise MusicTimeoutError(
                    f"歌曲生成超时(>{self.max_wait_s}s), 请稍后重试"
                )
            # 让出事件循环, 避免 interval=0 时死循环
            sleep_s = max(self.interval_s, 0.01)
            await asyncio.sleep(sleep_s)
