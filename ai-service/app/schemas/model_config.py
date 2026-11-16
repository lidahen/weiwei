from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import Optional


# ── model_type 枚举(供下拉框使用, 不动 DB schema, 仅应用层校验) ──
MODEL_TYPES: list[dict[str, str]] = [
    {"value": "LLM",        "label": "大语言模型"},
    {"value": "VISION",     "label": "视觉问答模型"},
    {"value": "EMBEDDING",  "label": "文本嵌入模型"},
    {"value": "IMAGE",      "label": "图片生成"},
    {"value": "VIDEO",      "label": "视频生成"},
    {"value": "TTS",        "label": "语音合成"},
    {"value": "ASR",        "label": "语音识别"},
    {"value": "TRANSLATE",  "label": "同声传译"},
    {"value": "ISOLATE",    "label": "人声分离"},
    {"value": "MUSIC",      "label": "音乐生成"},
    {"value": "SFX",        "label": "音效生成"},
    {"value": "AUDIO_GEN",  "label": "数字人口播音频"},
    {"value": "REALTIME",   "label": "实时语音对话"},
]


class CamelResponseModel(BaseModel):
    """输出时使用驼峰字段名，与 Java 侧/前端保持一致"""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class AiModelCreate(CamelResponseModel):
    """继承驼峰别名配置：Java 侧转发的是驼峰 JSON（modelType/modelName 等）"""
    model_provider: str = "openai"
    model_type: str
    model_name: str
    model_alias: Optional[str] = None
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    temperature: Optional[str] = None
    top_p: Optional[str] = None
    ext_param: Optional[str] = None
    default_flag: int = 0
    status: int = 1
    create_by: Optional[str] = None


class AiModelUpdate(CamelResponseModel):
    model_id: int
    model_provider: Optional[str] = None
    model_type: Optional[str] = None
    model_name: Optional[str] = None
    model_alias: Optional[str] = None
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    temperature: Optional[str] = None
    top_p: Optional[str] = None
    ext_param: Optional[str] = None
    default_flag: Optional[int] = None
    status: Optional[int] = None


class AiModelResponse(CamelResponseModel):
    model_id: int
    model_provider: Optional[str] = None
    model_type: Optional[str] = None
    model_name: Optional[str] = None
    model_alias: Optional[str] = None
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    temperature: Optional[str] = None
    top_p: Optional[str] = None
    ext_param: Optional[str] = None
    default_flag: Optional[int] = None
    status: Optional[int] = None
    del_flag: Optional[int] = None
    create_by: Optional[str] = None
    create_time: Optional[str] = None
    update_time: Optional[str] = None


class ChangeStatusRequest(CamelResponseModel):
    model_id: int
    default_flag: int
