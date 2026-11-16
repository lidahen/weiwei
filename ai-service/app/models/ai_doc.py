from sqlalchemy import Column, BigInteger, String, SmallInteger, Integer, Text
from app.models.base import Base


class AiDoc(Base):
    __tablename__ = "ai_doc"

    doc_id = Column(BigInteger, primary_key=True, autoincrement=True)
    knowledge_id = Column(BigInteger)
    doc_name = Column(String(200))
    source_type = Column(String(20))
    content = Column(Text)
    file_url = Column(String(500))
    file_name = Column(String(200))
    slice_count = Column(Integer, default=0)
    char_count = Column(Integer, default=0)
    status = Column(String(20))
    del_flag = Column(SmallInteger, default=0)
    create_by = Column(String(64))
    create_time = Column(String(30))
    update_by = Column(String(64))
    update_time = Column(String(30))
