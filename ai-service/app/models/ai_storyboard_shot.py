from sqlalchemy import Column, BigInteger, String, SmallInteger, Integer, Text, DateTime, text as sa_text
from app.models.base import Base


class AiStoryboardShot(Base):
    """作品分镜：一行一镜，产物字段由 composer 逐级填充，支持镜级重试。"""

    __tablename__ = "ai_storyboard_shot"

    shot_id = Column(BigInteger, primary_key=True, autoincrement=True)
    project_id = Column(BigInteger, nullable=False)
    shot_index = Column(Integer, nullable=False)
    text = Column(String(1000))
    mode = Column(String(20), default="img2vid")  # text2vid/img2vid/keyframe
    image_prompt = Column(Text)
    video_prompt = Column(Text)
    reference_images = Column(Text)
    image_task_id = Column(BigInteger)
    video_task_id = Column(BigInteger)
    image_url = Column(String(500))
    clip_url = Column(String(500))
    audio_url = Column(String(500))
    audio_duration_ms = Column(Integer)
    shot_status = Column(String(20), default="pending")
    error_msg = Column(Text)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=sa_text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=sa_text('CURRENT_TIMESTAMP'))
    del_flag = Column(SmallInteger, default=0)
