from sqlalchemy import Column, BigInteger, String, Integer, Text, SmallInteger, DateTime, text
from app.models.base import Base


class AiImageTask(Base):
    __tablename__ = "ai_image_task"

    task_id = Column(BigInteger, primary_key=True, autoincrement=True)
    model_id = Column(BigInteger, nullable=False)
    prompt = Column(Text, nullable=False)
    negative_prompt = Column(Text)
    size = Column(String(20), default="1024x1024")
    gen_type = Column(String(20), default="text2img")
    input_image = Column(String(500))
    response_format = Column(String(20), default="url")
    width = Column(Integer, default=1024)
    height = Column(Integer, default=1024)
    seed = Column(BigInteger)
    style = Column(String(50))
    status = Column(String(20), default="pending")
    result_url = Column(String(500))
    error_msg = Column(Text)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=text('CURRENT_TIMESTAMP'))
    del_flag = Column(SmallInteger, default=0)
