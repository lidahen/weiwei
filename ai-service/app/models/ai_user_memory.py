"""Agent 长期记忆模型（事实层 + 特质层，DDL 见 backend/data/agent-memory-init.sql）。"""
from sqlalchemy import JSON, BigInteger, DateTime, Integer, Numeric, SmallInteger, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class AiUserMemory(Base):
    __tablename__ = "ai_user_memory"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    tenant_id: Mapped[int] = mapped_column(BigInteger, default=1, comment="租户ID")
    user_id: Mapped[int] = mapped_column(BigInteger, comment="用户ID")
    memory_type: Mapped[str] = mapped_column(String(16), default="fact", comment="fact=事实 / trait=聚合特质")
    mem_key: Mapped[str] = mapped_column(String(32), comment="稳定短标识，取值见设计文档 3.2 词表")
    mem_value: Mapped[str] = mapped_column(String(500), comment="偏好值（自然语言短语）")
    confidence: Mapped[float] = mapped_column(Numeric(3, 2), default=0.60, comment="基础置信度 0~1")
    hit_count: Mapped[int] = mapped_column(Integer, default=1, comment="累计命中次数（跨会话累加）")
    session_count: Mapped[int] = mapped_column(Integer, default=1, comment="累计命中的不同会话数")
    last_session_id: Mapped[str] = mapped_column(String(100), default="", comment="最近一次命中的会话，用于会话内去重")
    evidence: Mapped[object] = mapped_column(JSON, nullable=True, comment="最近 5 条原话片段 [{text,at,session}]")
    scope: Mapped[str] = mapped_column(String(64), default="global", comment="global / category:数码 等")
    source: Mapped[str] = mapped_column(String(16), default="dialog", comment="dialog=对话提取 / behavior=工具行为")
    first_seen: Mapped[object] = mapped_column(DateTime, comment="首次出现时间")
    last_seen: Mapped[object] = mapped_column(DateTime, comment="最近出现时间")
    status: Mapped[int] = mapped_column(SmallInteger, default=1, comment="1=有效 0=已失效（覆盖/淘汰/超期）")
