"""AI 内容初审（商品/动态）的请求 schema。

调用方是 Java 侧（Quartz 初审任务，透传 X-Tenant-Id 计费），
权限在 Java 层校验，本服务不做登录校验。
"""

from typing import List, Optional

from pydantic import field_validator

from app.schemas.model_config import CamelResponseModel

BIZ_TYPES = ("product", "post")
VALID_CONDITIONS = ("brand_new", "99", "95", "90", "80", "70_lower")


class ModerationViolation(CamelResponseModel):
    """单条违规明细（type/detail 均为单词，无驼峰转换差异）。"""

    type: str
    detail: str


class ModerationVerdict(CamelResponseModel):
    """初审结论（输出驼峰 riskLevel/modelName，与 Java 任务侧字段对齐）。"""

    risk_level: str
    action: str
    violations: list = []
    summary: str = ""
    model_name: Optional[str] = None


class ModerationRequest(CamelResponseModel):
    """初审请求：文本必填其一（title/description），图片可选（需 VISION 模型）。"""

    biz_type: str = "product"
    title: Optional[str] = None
    description: Optional[str] = None
    category_name: Optional[str] = None
    price: Optional[float] = None
    condition: Optional[str] = None
    images: Optional[List[str]] = None
    user_id: Optional[str] = None

    @field_validator("biz_type")
    @classmethod
    def _check_biz_type(cls, v: str) -> str:
        v = (v or "").strip()
        if v not in BIZ_TYPES:
            raise ValueError("bizType 仅支持 product/post")
        return v

    @field_validator("condition")
    @classmethod
    def _check_condition(cls, v: Optional[str]) -> Optional[str]:
        if v and v not in VALID_CONDITIONS:
            return None  # 未知成色值不参与判定，静默忽略
        return v

    @field_validator("title")
    @classmethod
    def _check_title(cls, v: Optional[str]) -> Optional[str]:
        v = (v or "").strip()
        return v[:120] or None

    @field_validator("description")
    @classmethod
    def _check_description(cls, v: Optional[str]) -> Optional[str]:
        v = (v or "").strip()
        return v[:2000] or None

    @field_validator("images")
    @classmethod
    def _check_images(cls, v: Optional[List[str]]) -> Optional[List[str]]:
        if not v:
            return None
        urls = [str(u).strip()[:500] for u in v if str(u).strip()]
        return urls[:5] or None
