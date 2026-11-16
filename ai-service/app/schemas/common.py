from pydantic import BaseModel
from typing import Any


class ApiResponse(BaseModel):
    code: int = 200
    msg: str = "success"
    data: Any = None


class PageResult(BaseModel):
    code: int = 200
    msg: str = "success"
    data: dict = {}
