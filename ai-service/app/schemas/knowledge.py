from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel
from typing import Optional


class CamelResponseModel(BaseModel):
    """输出时使用驼峰字段名，与前端保持一致"""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class KnowledgeCreate(CamelResponseModel):
    knowledge_name: str
    knowledge_desc: Optional[str] = None
    welcome_msg: Optional[str] = None
    multi_round: int = Field(default=5, description="会话轮数")
    top_k: int = Field(default=3, description="匹配条数")
    score: float = Field(default=0.5, description="匹配率阈值")
    slice_size: int = Field(default=3000, description="切片大小(字符)")
    knowledge_sort: int = 1
    create_by: Optional[str] = None


class KnowledgeUpdate(CamelResponseModel):
    knowledge_id: int
    knowledge_name: Optional[str] = None
    knowledge_desc: Optional[str] = None
    welcome_msg: Optional[str] = None
    multi_round: Optional[int] = None
    top_k: Optional[int] = None
    score: Optional[float] = None
    slice_size: Optional[int] = None
    knowledge_sort: Optional[int] = None


class KnowledgeResponse(CamelResponseModel):
    knowledge_id: int
    knowledge_name: Optional[str] = None
    knowledge_desc: Optional[str] = None
    welcome_msg: Optional[str] = None
    multi_round: Optional[int] = None
    top_k: Optional[int] = None
    score: Optional[float] = None
    slice_size: Optional[int] = None
    knowledge_sort: Optional[int] = None
    del_flag: Optional[int] = None
    create_by: Optional[str] = None
    create_time: Optional[str] = None
    update_by: Optional[str] = None
    update_time: Optional[str] = None


class KnowledgeOption(BaseModel):
    """下拉选项：只返回 id 和名称"""

    knowledge_id: int
    knowledge_name: str
