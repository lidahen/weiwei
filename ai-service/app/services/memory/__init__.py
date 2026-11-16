"""Agent 长期记忆（事实层 + 特质层）。

分层：`constants`（词表/正则/上限）→ `logic`（纯函数：校验/衰减/渲染/UPSERT 规划）
→ `store`（DB 层）→ `extract`（异步提取）→ `aggregate`（特质聚合）→ `context`（注入编排）。

对外只暴露包级入口（下游按名调用，不依赖子模块路径）：读路径 `build_context`
（LangGraph 的 memory_recall 节点用），写路径 `schedule_extract`（runner 每轮回复后异步用）。
"""
from app.services.memory.context import build_context
from app.services.memory.extract import schedule_extract

__all__ = ["build_context", "schedule_extract"]
