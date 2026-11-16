from pydantic import BaseModel
from typing import Optional, List


class ChatMsg(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMsg]
    user_id: Optional[int] = None
    session_id: Optional[str] = None
    resume: Optional[str] = None


class ProductCard(BaseModel):
    product_id: int
    title: Optional[str] = None
    cover: Optional[str] = None
    price: Optional[float] = None
    original_price: Optional[float] = None
    condition: Optional[str] = None
    city: Optional[str] = None
    seller_nick_name: Optional[str] = None


class PendingConfirm(BaseModel):
    """工具请求确认：挂起图等待用户确认/修改。"""
    type: str = "confirm"
    tool: Optional[str] = None
    message: Optional[str] = None
    # confirm_token 仅在服务端内部使用，不直接暴露给前端；
    # 前端只需原样展示 message，用户确认/修改后把回复随 resume 回传。
    session_id: Optional[str] = None


class ChatResponse(BaseModel):
    reply: str
    products: List[dict] = []
    pending_confirm: Optional[PendingConfirm] = None
    # 本次回答 token 用量（{promptTokens, completionTokens}），供 Java 侧落账
    usage: Optional[dict] = None
