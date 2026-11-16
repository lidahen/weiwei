"""OpenAI images 协议适配（gpt-image 系列模型，如 OpenOx 中转的 gpt-image-2）。

与 Agnes 协议的差异：
- 文生图同为 POST {base}/v1/images/generations（JSON），但 response_format 放请求体顶层
  （Agnes 要求放在 extra_body 内），且显式传 n=1。
- 图生图/多图合成不走 generations 的 extra_body.image，而是 OpenAI 官方 edits 端点
  POST {base}/v1/images/edits（multipart/form-data），参考图需下载后以文件上传。
- 尺寸有硬约束：宽高为 16 的倍数、长宽比 ≤ 3:1、最长边 ≤ 3840、
  总像素 655,360 ~ 8,294,400，需把任务里解析出的 WxH 钳制到合法范围。

协议选择基于 ai_model.model_provider（见 resolve_api_format）；
历史 Agnes 模型（含默认值 openai）不受影响，仍走原协议。
"""

import logging
import math
from typing import Any, Dict, List, Optional, Tuple

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

API_FORMAT_AGNES = "agnes"
API_FORMAT_OPENAI_IMAGES = "openai_images"

# 显式声明为 OpenAI images 协议的 model_provider 取值（不区分大小写）。
# 注意：默认值 "openai" 不在其中——历史 Agnes 模型用的就是默认值，必须保持原协议。
OPENAI_IMAGES_PROVIDERS = {
    "openox",
    "openai-images",
    "openai_images",
    "gpt-image",
    "gpt_image",
}

# gpt-image 系列的尺寸硬约束（OpenOx 接入文档）
_MIN_PIXELS = 655_360
_MAX_PIXELS = 8_294_400
_MAX_SIDE = 3840
_MAX_RATIO = 3.0
_SIZE_MULTIPLE = 16

# edits 端点接受的图片格式
_ALLOWED_CONTENT_TYPES = {"image/png", "image/jpeg", "image/webp"}
_CONTENT_TYPE_BY_EXT = {
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
}
_MAX_INPUT_IMAGE_BYTES = 50 * 1024 * 1024  # 官方限制单张 ≤ 50MB


def resolve_api_format(model: Any) -> str:
    """根据 ai_model 行决定图片接口协议。

    - model_provider 显式声明为 OpenAI images 协议提供商（如 openox / gpt-image）→
      API_FORMAT_OPENAI_IMAGES
    - 其余（含默认 "openai"、空值）一律 API_FORMAT_AGNES，保证历史模型行为不变
    """
    provider = (getattr(model, "model_provider", None) or "").strip().lower()
    if provider in OPENAI_IMAGES_PROVIDERS:
        return API_FORMAT_OPENAI_IMAGES
    return API_FORMAT_AGNES


def _floor_to_multiple(value: int) -> int:
    return max(_SIZE_MULTIPLE, (value // _SIZE_MULTIPLE) * _SIZE_MULTIPLE)


def _ceil_to_multiple(value: int) -> int:
    return max(_SIZE_MULTIPLE, ((value + _SIZE_MULTIPLE - 1) // _SIZE_MULTIPLE) * _SIZE_MULTIPLE)


def map_size_for_gpt_image(size: Optional[str]) -> str:
    """把已解析的 "宽x高" 钳制到 gpt-image 系列的尺寸约束，返回新 "宽x高"。

    步骤：比例 ≤3:1 → 等比缩放使最长边 ≤3840 且总像素 ≤8,294,400 →
    （若低于最小像素则等比放大）→ 对齐到 16 的倍数。
    无法解析时回退 1024x1024。
    """
    parts = (size or "").lower().split("x")
    if len(parts) != 2:
        return "1024x1024"
    try:
        width, height = int(parts[0]), int(parts[1])
    except ValueError:
        return "1024x1024"
    if width <= 0 or height <= 0:
        return "1024x1024"

    # 长宽比 ≤ 3:1（沿长边裁短，不做非等比变形以外的处理）
    if width / height > _MAX_RATIO:
        width = int(height * _MAX_RATIO)
    elif height / width > _MAX_RATIO:
        height = int(width * _MAX_RATIO)

    # 等比缩放到上限内（最长边、总像素同时满足；scale ≤ 1 表示只缩不放）
    scale = min(1.0, _MAX_SIDE / max(width, height), math.sqrt(_MAX_PIXELS / (width * height)))
    if width * height * scale * scale < _MIN_PIXELS:
        scale = math.sqrt(_MIN_PIXELS / (width * height))

    width = int(width * scale)
    height = int(height * scale)
    # 对齐 16 的倍数；贴着最小像素约束时向上取整，避免舍入后跌破下限
    if width * height <= _MIN_PIXELS * 1.01:
        width, height = _ceil_to_multiple(width), _ceil_to_multiple(height)
    else:
        width, height = _floor_to_multiple(width), _floor_to_multiple(height)
    return f"{width}x{height}"


def build_generation_body(
    model_name: str,
    prompt: str,
    size: str,
    response_format: str = "url",
) -> Dict[str, Any]:
    """文生图请求体（generations 端点，JSON）。response_format 位于请求体顶层。"""
    return {
        "model": model_name,
        "prompt": prompt,
        "n": 1,
        "size": size,
        "response_format": response_format or "url",
    }


def build_edit_form(
    model_name: str,
    prompt: str,
    size: str,
    response_format: str = "url",
) -> Dict[str, str]:
    """图生图 edits 端点的 form 字段（multipart 下的普通字段均为字符串）。"""
    return {
        "model": model_name,
        "prompt": prompt,
        "n": "1",
        "size": size,
        "response_format": response_format or "url",
    }


async def download_input_images(urls: List[str]) -> List[Tuple[str, bytes, str]]:
    """下载参考图，返回 [(文件名, 字节, content_type), ...]，供 edits 端点 multipart 上传。

    OpenAI images 协议不接受图片 URL，必须上传文件本体。
    """
    files: List[Tuple[str, bytes, str]] = []
    timeout = httpx.Timeout(
        connect=settings.agnes_connect_timeout,
        read=settings.agnes_read_timeout,
        write=30.0,
        pool=30.0,
    )
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        for index, url in enumerate(urls, start=1):
            response = await client.get(url)
            response.raise_for_status()
            content = response.content
            if len(content) > _MAX_INPUT_IMAGE_BYTES:
                raise ValueError(f"第 {index} 张参考图超过 50MB 限制")

            content_type = (response.headers.get("content-type") or "").split(";")[0].strip().lower()
            filename = url.rstrip("/").rsplit("/", 1)[-1].split("?")[0] or f"image-{index}.png"
            ext = ("." + filename.rsplit(".", 1)[-1].lower()) if "." in filename else ""
            if content_type not in _ALLOWED_CONTENT_TYPES:
                content_type = _CONTENT_TYPE_BY_EXT.get(ext, "image/png")
            if not filename.lower().endswith((".png", ".jpg", ".jpeg", ".webp")):
                filename = f"{filename or f'image-{index}'}.png"

            files.append((filename, content, content_type))
    return files


def build_multipart_files(
    downloaded: List[Tuple[str, bytes, str]],
) -> List[Tuple[str, Tuple[str, bytes, str]]]:
    """组装 httpx files 参数：单图用 image 字段，多图用 OpenAI 约定的 image[]。"""
    if len(downloaded) == 1:
        return [("image", downloaded[0])]
    return [("image[]", item) for item in downloaded]


def extract_openai_image_result(response_data: Dict[str, Any]) -> Tuple[Optional[str], bool]:
    """从 OpenAI images 协议响应中提取结果，返回 (数据, 是否为 base64)。

    url 优先；部分中转对 gpt-image 系列只回 b64_json，此时交由上层
    提示无法落库（result_url 列为 VARCHAR(500)），而非存入超长数据。
    """
    try:
        data_list = response_data.get("data") or []
        if not data_list:
            return None, False
        first_item = data_list[0]
        url = first_item.get("url")
        if url:
            return url, False
        b64 = first_item.get("b64_json")
        if b64:
            return b64, True
        return None, False
    except (AttributeError, KeyError, IndexError, TypeError):
        return None, False
