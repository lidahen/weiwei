from typing import Optional

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class _CamelModel(BaseModel):
    """接受 camelCase 别名, 与前端字段命名保持一致。"""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class VideoGenerateRequest(_CamelModel):
    """视频生成请求 - 接受 camelCase 别名(前端 JS 习惯)。"""
    model_id: Optional[int] = None
    prompt: str
    gen_type: Optional[str] = "text2vid"
    input_images: Optional[str] = None  # JSON array string or single URL
    width: Optional[int] = None
    height: Optional[int] = None
    num_frames: Optional[int] = None
    frame_rate: Optional[int] = None
    duration: Optional[int] = None  # 用户可选显式传 duration(秒), 否则用 numFrames/frameRate
    negative_prompt: Optional[str] = None
    seed: Optional[int] = None
    create_by: Optional[str] = None
    # 是否启用智能提示词增强（前端开关）
    enhance_prompt: Optional[bool] = True


class VideoTaskResponse(BaseModel):
    task_id: int
    model_id: Optional[int] = None
    agnes_task_id: Optional[str] = None
    agnes_video_id: Optional[str] = None
    prompt: Optional[str] = None
    status: Optional[str] = None
    progress: Optional[int] = None
    result_url: Optional[str] = None
    video_seconds: Optional[str] = None
    video_size: Optional[str] = None
    error_msg: Optional[str] = None
