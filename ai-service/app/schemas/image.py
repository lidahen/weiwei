from pydantic import BaseModel
from typing import Optional


class ImageGenerateRequest(BaseModel):
    model_id: Optional[int] = None
    prompt: str
    negative_prompt: Optional[str] = None
    # 尺寸：档位（1K/2K/3K/4K）或精确 "宽x高"；档位配合 ratio 解析为官方原生尺寸
    size: Optional[str] = "1K"
    ratio: Optional[str] = "1:1"
    gen_type: Optional[str] = "text2img"  # text2img / img2img / multi2img
    input_image: Optional[str] = None  # 单 URL 或 JSON 数组字符串（multi2img）
    response_format: Optional[str] = "url"
    style: Optional[str] = None
    seed: Optional[int] = None
    create_by: Optional[str] = None
    # 是否启用智能提示词增强（前端开关）
    enhance_prompt: Optional[bool] = True


class ImageTaskResponse(BaseModel):
    task_id: int
    model_id: Optional[int] = None
    prompt: Optional[str] = None
    status: Optional[str] = None
    result_url: Optional[str] = None
    error_msg: Optional[str] = None
