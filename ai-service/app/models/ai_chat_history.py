from sqlalchemy import Column, BigInteger, String, Text
from app.models.base import Base


class AiChatHistory(Base):
    __tablename__ = "ai_chat_history"

    msg_id = Column(BigInteger, primary_key=True, autoincrement=True)
    knowledge_id = Column(BigInteger)
    session_id = Column(String(100))
    role = Column(String(20))
    content = Column(Text)
    create_by = Column(String(64))
    create_time = Column(String(30))
