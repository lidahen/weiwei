"""AI 商品文案 / 配图 / 社区日报辅助生成的请求与响应 schema。

沿用项目惯例：继承 CamelResponseModel，输出驼峰字段名，
与 Java 代理层 / 前端保持一致（populate_by_name 使 snake_case 入参也兼容）。
"""

from typing import Dict, List, Optional

from pydantic import field_validator

from app.schemas.model_config import CamelResponseModel


class ProductCopyRequest(CamelResponseModel):
    """文案生成请求：商品名必填，其余字段可选用于丰富上下文。"""

    product_name: str
    category: Optional[str] = None
    condition: Optional[str] = None
    price: Optional[float] = None
    keywords: Optional[str] = None
    # 调用用户标识（用量计费明细落账用，Java 透传/前端携带均可）
    user_id: Optional[str] = None

    @field_validator("product_name")
    @classmethod
    def _check_product_name(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("商品名不能为空")
        if len(v) > 60:
            raise ValueError("商品名不能超过60字")
        return v

    @field_validator("keywords", "category", "condition")
    @classmethod
    def _check_length(cls, v: Optional[str]) -> Optional[str]:
        if v and len(v) > 200:
            raise ValueError("字段长度不能超过200字")
        return v


class ProductImageRequest(CamelResponseModel):
    """配图生成请求：商品名必填。"""

    product_name: str
    category: Optional[str] = None
    condition: Optional[str] = None
    price: Optional[float] = None
    keywords: Optional[str] = None
    size: Optional[str] = None
    # 调用用户标识（用量计费明细落账用，Java 透传/前端携带均可）
    user_id: Optional[str] = None

    @field_validator("product_name")
    @classmethod
    def _check_product_name(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("商品名不能为空")
        if len(v) > 60:
            raise ValueError("商品名不能超过60字")
        return v

    @field_validator("keywords", "category", "condition")
    @classmethod
    def _check_length(cls, v: Optional[str]) -> Optional[str]:
        if v and len(v) > 200:
            raise ValueError("字段长度不能超过200字")
        return v


class ProductCopyData(CamelResponseModel):
    """文案生成结果（输出驼峰：titles/description/modelId/modelName）。"""

    titles: List[str] = []
    description: str = ""
    model_id: Optional[int] = None
    model_name: Optional[str] = None


class ProductImageData(CamelResponseModel):
    """配图生成结果（输出驼峰：taskId/imageUrl/modelId/modelName）。"""

    task_id: Optional[int] = None
    image_url: Optional[str] = None
    model_id: Optional[int] = None
    model_name: Optional[str] = None


class DailyReportRequest(CamelResponseModel):
    """社区日报生成请求（Java 代理层传入聚合指标，stats 内容结构见 daily_report.py）。"""

    stats: Dict = {}
    tenant_name: Optional[str] = None
    # 日报日期 yyyy-MM-dd（统计区间口径，拼进提示词与保底标题）
    date: Optional[str] = None
    # 调用用户标识（用量计费明细落账用；定时任务路径无用户，留空）
    user_id: Optional[str] = None

    @field_validator("tenant_name", "date")
    @classmethod
    def _check_length(cls, v: Optional[str]) -> Optional[str]:
        if v and len(v) > 64:
            raise ValueError("字段长度不能超过64字")
        return v


class DailyReportData(CamelResponseModel):
    """日报生成结果（输出驼峰：title/content）。"""

    title: str = ""
    content: str = ""


class ProductRecognitionRequest(CamelResponseModel):
    """AI 识图填单请求：1-5 张商品图（OSS 相对路径或绝对 URL），hint 为用户补充说明。"""

    images: List[str]
    hint: Optional[str] = None
    user_id: Optional[str] = None

    @field_validator("images")
    @classmethod
    def _check_images(cls, v: List[str]) -> List[str]:
        urls = [str(u).strip()[:500] for u in (v or []) if str(u).strip()]
        if not urls:
            raise ValueError("请至少上传一张商品图片")
        if len(urls) > 5:
            raise ValueError("最多识别 5 张图片")
        return urls

    @field_validator("hint")
    @classmethod
    def _check_hint(cls, v: Optional[str]) -> Optional[str]:
        v = (v or "").strip()
        return v[:200] or None


class ProductRecognitionData(CamelResponseModel):
    """识图填单结果：类目 code + 名称、标题、描述、成色（未识别的字段为 None）。"""

    cat_l1_code: Optional[str] = None
    cat_l1_name: Optional[str] = None
    cat_l2_code: Optional[str] = None
    cat_l2_name: Optional[str] = None
    title: Optional[str] = None
    description: Optional[str] = None
    condition: Optional[str] = None
    model_id: Optional[int] = None
    model_name: Optional[str] = None


class ProductValuationRequest(CamelResponseModel):
    """AI 估价请求：标题必填；类目未选时服务端尝试 LLM 按标题分类。"""

    title: str
    description: Optional[str] = None
    cat_l1: Optional[str] = None
    cat_l2: Optional[str] = None
    condition: Optional[str] = None
    user_id: Optional[str] = None

    @field_validator("title")
    @classmethod
    def _check_title(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("标题不能为空")
        return v[:120]

    @field_validator("description")
    @classmethod
    def _check_description(cls, v: Optional[str]) -> Optional[str]:
        v = (v or "").strip()
        return v[:500] or None


class ProductValuationData(CamelResponseModel):
    """估价结果：成交样本分位数 + 成色调整后的建议区间（价格均来自真实数据，非模型生成）。"""

    sample_count: int = 0
    transaction_count: int = 0
    price_low: Optional[float] = None
    price_high: Optional[float] = None
    median: Optional[float] = None
    p25: Optional[float] = None
    p75: Optional[float] = None
    suggestion: Optional[float] = None
    explanation: str = ""
    source: str = "transaction_stats"
    model_name: Optional[str] = None


class HomeWidgetWeather(CamelResponseModel):
    """首页小组件天气块（open-meteo 当日实况；天气源故障时整个字段为 None）。"""

    city: str
    text: str
    icon: str
    temp: Optional[float] = None
    temp_max: Optional[float] = None
    temp_min: Optional[float] = None


class HomeWidgetData(CamelResponseModel):
    """首页小组件数据：日期 + 天气 + 激励语池（5 条，前端「换一句」本地轮换）。"""

    city: str
    date: str
    weekday: str
    weather: Optional[HomeWidgetWeather] = None
    quotes: List[str] = []
    source: str = "template"  # ai / cache / template


class PublishPreviewRequest(CamelResponseModel):
    """发布流水线请求（拍照即发布）：图片必填，hint 可选。

    一次调用串联 识图填单 → 统计估价 → 文案生成 三步，
    各步独立降级：识图失败则整单失败；估价/文案失败时对应字段为 None，
    前端仍可用识图结果手动发布。
    """

    images: List[str]
    hint: Optional[str] = None
    user_id: Optional[str] = None

    @field_validator("images")
    @classmethod
    def _check_images(cls, v: List[str]) -> List[str]:
        urls = [str(u).strip()[:500] for u in (v or []) if str(u).strip()]
        if not urls:
            raise ValueError("请至少上传一张商品图片")
        if len(urls) > 5:
            raise ValueError("最多识别 5 张图片")
        return urls

    @field_validator("hint")
    @classmethod
    def _check_hint(cls, v: Optional[str]) -> Optional[str]:
        v = (v or "").strip()
        return v[:200] or None


class PublishPreviewData(CamelResponseModel):
    """发布流水线结果：识图结果 + 估价 + 文案，后两步失败时为 None（前端降级手动填）。"""

    # 第 1 步：识图填单（失败则整单 500）
    cat_l1_code: Optional[str] = None
    cat_l1_name: Optional[str] = None
    cat_l2_code: Optional[str] = None
    cat_l2_name: Optional[str] = None
    title: Optional[str] = None
    description: Optional[str] = None
    condition: Optional[str] = None
    # 第 2 步：统计估价（失败为 None，不阻塞发布；类型化声明使 by_alias 统一驼峰输出）
    valuation: Optional["ProductValuationData"] = None
    # 第 3 步：文案（失败为 None，不阻塞发布）
    titles: Optional[List[str]] = None
    description_ai: Optional[str] = None
    model_id: Optional[int] = None
    model_name: Optional[str] = None
