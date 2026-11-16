from sqlalchemy import Column, BigInteger, String, SmallInteger, Text, DateTime, text
from app.models.base import Base


class AiPersona(Base):
    """创作人设库：账号定位 + 文案风格样例 + 定妆照 + 克隆音色。"""

    __tablename__ = "ai_persona"

    persona_id = Column(BigInteger, primary_key=True, autoincrement=True)
    name = Column(String(64), nullable=False)
    description = Column(String(500))
    style_fewshot = Column(Text)
    look_image_urls = Column(Text)
    voice_id = Column(String(128))
    voice_name = Column(String(128))
    banned_words = Column(Text)
    status = Column(SmallInteger, default=1)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=text('CURRENT_TIMESTAMP'))
    del_flag = Column(SmallInteger, default=0)
