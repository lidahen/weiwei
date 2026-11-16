from sqlalchemy import Column, BigInteger, String, SmallInteger, Integer, Text, DateTime, text
from app.models.base import Base


class AiVideoTask(Base):
    __tablename__ = "ai_video_task"

    task_id = Column(BigInteger, primary_key=True, autoincrement=True)
    model_id = Column(BigInteger, nullable=False)
    agnes_task_id = Column(String(256))
    agnes_video_id = Column(String(256))
    prompt = Column(Text, nullable=False)
    gen_type = Column(String(20), default="text2vid")
    input_images = Column(Text)
    negative_prompt = Column(Text)
    duration = Column(Integer, default=5)
    width = Column(Integer, default=1280)
    height = Column(Integer, default=720)
    num_frames = Column(Integer)
    frame_rate = Column(Integer)
    fps = Column(Integer, default=24)
    seed = Column(BigInteger)
    status = Column(String(20), default="pending")
    progress = Column(Integer, default=0)
    result_url = Column(String(500))
    video_seconds = Column(String(20))
    video_size = Column(String(20))
    error_msg = Column(Text)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=text('CURRENT_TIMESTAMP'))
    del_flag = Column(SmallInteger, default=0)
