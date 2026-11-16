from typing import List, Optional

from pydantic import BaseModel, Field


# ════════════════════════════════════════════════════════════
# 人设
# ════════════════════════════════════════════════════════════

class PersonaSaveReq(BaseModel):
    persona_id: Optional[int] = None
    name: str = Field(min_length=1, max_length=64)
    description: Optional[str] = None
    style_fewshot: Optional[str] = None
    look_image_urls: Optional[List[str]] = None
    voice_id: Optional[str] = None
    voice_name: Optional[str] = None
    banned_words: Optional[str] = None
    status: int = 1
    create_by: Optional[str] = None


class PersonaGenerateLookReq(BaseModel):
    persona_id: int
    appearance_prompt: str = Field(min_length=5, description="人物外观描述（性别/年龄/形象/着装风格）")
    count: int = Field(default=3, ge=1, le=5)


class PersonaLookSetReq(BaseModel):
    """把一张已有图片（如图片生成页的作品）设为人设定妆照。"""
    persona_id: int
    image_url: str = Field(min_length=5)
    primary: bool = Field(default=True, description="True=设为主形象（数组首位，渲染参考图取它）")


# ════════════════════════════════════════════════════════════
# 拆解
# ════════════════════════════════════════════════════════════

class DecomposeReq(BaseModel):
    source_url: str = Field(min_length=5, description="抖音分享链接或视频直链")
    create_by: Optional[str] = None


# ════════════════════════════════════════════════════════════
# 作品项目
# ════════════════════════════════════════════════════════════

class ProjectCreateReq(BaseModel):
    source_type: str = Field(default="viral_clone", pattern="^(viral_clone|original|image_text)$")
    template_id: Optional[int] = None
    persona_id: Optional[int] = None
    topic: str = Field(min_length=2, description="选题/主题描述（复刻模式可传补充方向）")
    llm_model_id: Optional[int] = Field(default=None, description="指定写稿 LLM 模型（缺省用模型管理默认）")
    create_by: Optional[str] = None


class StoryboardUpdateReq(BaseModel):
    """人工编辑后的分镜 JSON（§4 结构），按 index 同步分镜行。

    text 未变的镜保留已生成产物；text 变更的镜失效 audio（下次渲染重配）。
    """
    storyboard: dict


class ShotRetryReq(BaseModel):
    stage: str = Field(pattern="^(image|video|audio)$")


class RenderReq(BaseModel):
    """启动渲染：可指定生图/生视频模型（缺省走模型管理默认）。

    选择会落库到 storyboard.meta，单镜重试沿用同一模型。
    """
    image_model_id: Optional[int] = None
    video_model_id: Optional[int] = None


class HookVariantsReq(BaseModel):
    """一键多变体：克隆项目并生成 N 版替换钩子（正文与其余镜产物复用）。"""
    count: int = Field(default=3, ge=1, le=5)


class PublishPackageReq(BaseModel):
    extra_instructions: Optional[str] = Field(default=None, description="发布文案补充要求")
