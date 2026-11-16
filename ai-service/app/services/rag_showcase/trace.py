"""查询 trace：内存环形缓冲 + JSONL 落盘。

面试演示时可以直接回放「这条问题召回了什么、分数多少、为什么拒答」；
生产环境应改为脱敏/采样，这里保留查询原文是为了本地调试。
"""
from __future__ import annotations

import json
import logging
from collections import deque
from datetime import datetime, timezone
from pathlib import Path

logger = logging.getLogger(__name__)


class TraceStore:
    def __init__(self, path: Path | None, limit: int = 100) -> None:
        self.path = Path(path) if path else None
        self._items: deque[dict] = deque(maxlen=max(1, limit))

    def add(self, trace: dict) -> None:
        record = dict(trace)
        record.setdefault("timestamp", datetime.now(timezone.utc).isoformat())
        self._items.append(record)
        if self.path is None:
            return
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            with self.path.open("a", encoding="utf-8") as f:
                f.write(json.dumps(record, ensure_ascii=False) + "\n")
        except Exception as e:  # noqa: BLE001 —— trace 是观测旁路，不能影响主流程
            logger.warning("rag trace write failed: %s", e)

    def recent(self, limit: int = 50) -> list[dict]:
        items = list(self._items)
        return items[-max(1, limit):]
