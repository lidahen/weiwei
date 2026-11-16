from sqlalchemy import Column, BigInteger, String, SmallInteger, Integer, Float
from app.models.base import Base


class AiKnowledge(Base):
    __tablename__ = "ai_knowledge"

    knowledge_id = Column(BigInteger, primary_key=True, autoincrement=True)
    knowledge_name = Column(String(100))
    knowledge_desc = Column(String(500))
    welcome_msg = Column(String(500))
    multi_round = Column(Integer, default=5)
    top_k = Column(Integer, default=3)
    score = Column(Float, default=0.5)
    slice_size = Column(Integer, default=3000)
    knowledge_sort = Column(Integer, default=0)
    del_flag = Column(SmallInteger, default=0)
    create_by = Column(String(64))
    create_time = Column(String(30))
    update_by = Column(String(64))
    update_time = Column(String(30))
