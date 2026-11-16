from sqlalchemy import Column, BigInteger, String, SmallInteger, Integer, Text, DateTime, text
from app.models.base import Base


class AiViralTemplate(Base):
    """爆款结构模板：由 decomposer 从爆款视频拆解而来，只存结构与镜头语言，不存素材。"""

    __tablename__ = "ai_viral_template"

    template_id = Column(BigInteger, primary_key=True, autoincrement=True)
    source_url = Column(String(500))
    source_video_url = Column(String(500))
    title = Column(String(200))
    asr_text = Column(Text)
    structure_json = Column(Text)
    hook_type = Column(String(50))
    duration_sec = Column(Integer)
    shot_count = Column(Integer)
    status = Column(String(20), default="analyzing")
    error_msg = Column(Text)
    create_by = Column(String(64))
    create_time = Column(DateTime, server_default=text('CURRENT_TIMESTAMP'))
    update_time = Column(DateTime, server_onupdate=text('CURRENT_TIMESTAMP'))
    del_flag = Column(SmallInteger, default=0)
