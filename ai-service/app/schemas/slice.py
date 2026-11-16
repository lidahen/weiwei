from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import Optional


class CamelResponseModel(BaseModel):
    """输出时使用驼峰字段名，与前端保持一致"""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SliceUpdate(CamelResponseModel):
    slice_id: int
    content: Optional[str] = None


class SliceResponse(CamelResponseModel):
    slice_id: int
    doc_id: Optional[int] = None
    knowledge_id: Optional[int] = None
    slice_index: Optional[int] = None
    content: Optional[str] = None
    char_count: Optional[int] = None
    vector_status: Optional[str] = None
    create_time: Optional[str] = None
