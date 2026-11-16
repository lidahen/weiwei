from sqlalchemy import Column, BigInteger, String, SmallInteger, Text, DateTime, text
from app.models.base import Base


class AiModel(Base):
    __tablename__ = "ai_model"

    model_id = Column(BigInteger, primary_key=True, autoincrement=True)
    model_provider = Column(String(50), default="openai")
    model_type = Column(String(20))
    model_name = Column(String(100))
    model_alias = Column(String(100))
    api_key = Column(String(500))
    base_url = Column(String(500))
    temperature = Column(String(10))
    top_p = Column(String(10))
    ext_param = Column(Text)
    default_flag = Column(SmallInteger, default=0)
    status = Column(SmallInteger, default=1)
    del_flag = Column(SmallInteger, default=0)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=text('CURRENT_TIMESTAMP'))
