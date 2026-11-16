"""关键配置缺失校验测试（2026-11-03 批次1）。

锁定不变量：missing_critical_settings 为纯函数（接收 Settings 参数），
列出为空的关键配置项名称；main 启动时据此打一条汇总 WARNING。
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import Settings, missing_critical_settings


def test_all_empty_returns_all_missing():
    s = Settings(ai_api_key="", internal_api_token="", embedding_api_key="")
    assert missing_critical_settings(s) == [
        "ai_api_key",
        "internal_api_token",
        "embedding_api_key",
    ]


def test_nonempty_key_not_listed():
    s = Settings(ai_api_key="sk-test", internal_api_token="", embedding_api_key="")
    assert "ai_api_key" not in missing_critical_settings(s)
    assert "internal_api_token" in missing_critical_settings(s)
