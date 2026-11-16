"""AI 用量日账单模型（与 Java 侧只读查询共用表，DDL 见 docs/db 基线）。"""
from sqlalchemy import BigInteger, Date, DateTime, Integer
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class AiUsageDaily(Base):
    __tablename__ = "ai_usage_daily"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    tenant_id: Mapped[int] = mapped_column(BigInteger, default=1, comment="租户ID")
    stat_date: Mapped[object] = mapped_column(Date, comment="统计日期")
    calls: Mapped[int] = mapped_column(Integer, default=0, comment="调用次数")
    prompt_tokens: Mapped[int] = mapped_column(BigInteger, default=0, comment="输入token累计")
    completion_tokens: Mapped[int] = mapped_column(BigInteger, default=0, comment="输出token累计")
    update_time: Mapped[object] = mapped_column(DateTime, nullable=True, comment="更新时间")
