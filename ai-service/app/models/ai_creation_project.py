from sqlalchemy import Column, BigInteger, String, SmallInteger, Text, DateTime, text
from app.models.base import Base


class AiCreationProject(Base):
    """创作作品项目：一次出片全流程的载体（复刻/自创/图文）。"""

    __tablename__ = "ai_creation_project"

    project_id = Column(BigInteger, primary_key=True, autoincrement=True)
    title = Column(String(200))
    source_type = Column(String(20), nullable=False)  # viral_clone/original/image_text
    template_id = Column(BigInteger)
    persona_id = Column(BigInteger)
    topic = Column(String(500))
    script_text = Column(Text)
    storyboard_json = Column(Text)
    status = Column(String(30), default="draft")
    final_video_url = Column(String(500))
    cover_url = Column(String(500))
    publish_package_json = Column(Text)
    error_msg = Column(Text)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=text('CURRENT_TIMESTAMP'))
    del_flag = Column(SmallInteger, default=0)
