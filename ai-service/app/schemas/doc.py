from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel
from typing import Optional, List


class CamelResponseModel(BaseModel):
    """输出时使用驼峰字段名，与前端保持一致"""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class DocFile(BaseModel):
    """上传文件信息（前端 fileList 元素）"""

    file_name: Optional[str] = Field(default=None, alias="fileName")
    file_url: Optional[str] = Field(default=None, alias="fileUrl")


class DocCreate(CamelResponseModel):
    doc_id: int = 0
    knowledge_id: int
    doc_name: Optional[str] = None
    source_type: str = "INPUT"
    content: Optional[str] = None
    file_url: Optional[str] = None
    file_name: Optional[str] = None
    file_list: Optional[List[DocFile]] = Field(default=None, alias="fileList")
    create_by: Optional[str] = None


class DocUpdate(CamelResponseModel):
    doc_id: int
    knowledge_id: Optional[int] = None
    doc_name: Optional[str] = None
    content: Optional[str] = None
    file_url: Optional[str] = None
    file_name: Optional[str] = None


class DocResponse(CamelResponseModel):
    doc_id: int
    knowledge_id: Optional[int] = None
    doc_name: Optional[str] = None
    source_type: Optional[str] = None
    content: Optional[str] = None
    file_url: Optional[str] = None
    file_name: Optional[str] = None
    slice_count: Optional[int] = None
    char_count: Optional[int] = None
    status: Optional[str] = None
    del_flag: Optional[int] = None
    create_by: Optional[str] = None
    create_time: Optional[str] = None
    update_time: Optional[str] = None
