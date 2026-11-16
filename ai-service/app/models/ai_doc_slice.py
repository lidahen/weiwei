from sqlalchemy import Column, BigInteger, String, Integer, Text
from app.models.base import Base


class AiDocSlice(Base):
    __tablename__ = "ai_doc_slice"

    slice_id = Column(BigInteger, primary_key=True, autoincrement=True)
    doc_id = Column(BigInteger)
    knowledge_id = Column(BigInteger)
    slice_index = Column(Integer)
    content = Column(Text)
    char_count = Column(Integer)
    vector_status = Column(String(20))
    create_time = Column(String(30))
