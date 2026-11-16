"""SenseNova（商汤日日新，platform.sensenova.cn）图片生成协议适配。

与项目内其它图片协议的差异：

| 项 | SenseNova | 备注 |
|---|---|---|
| base_url 约定 | `https://token.sensenova.cn`（**不带** `/v1`） | 与 IMAGE/VIDEO 既有约定一致，`/v1` 由本模块拼 |
| 文生图 | `POST {base}/v1/images/generations`（JSON） | 与 OpenAI images 协议同构 |
| 图片编辑 | `POST {base}/v1/images/edits`（**JSON，不是 multipart**） | 参考图字段是 `images: [{"image_url": ...}]`，最多 5 张 |
| size 约束 | 宽高均为 **32** 的倍数，单边 512~4096，长宽比 ≤ 3:1 | 与 gpt-image 的 16 倍数/最长边 3840 不同，不能复用其钳制函数 |
| 响应 | `{created, data:[{url | b64_json}], output_format, size, usage}` | 结构与 OpenAI images 一致，结果解析复用 |

文档：https://platform.sensenova.cn/docs
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

# 触发本协议的 ai_model.model_provider 取值（不区分大小写）
SENSENOVA_PROVIDERS = {"sensenova", "sense-nova", "dailynew"}

# size 约束（官方文档：images/generations 与 images/edits 相同）
SIZE_MULTIPLE = 32
MIN_SIDE = 512
MAX_SIDE = 4096
MAX_RATIO = 3.0

# 官方建议分辨率 + 1K/3K 档位，均为「32 倍数 + 单边 ≤4096 + 比例 ≤3:1」的合法值。
# 仅作为 ai_model.ext_param 未声明 sizes 时的兜底；声明了就以声明为准。
DEFAULT_SIZES: List[str] = [
    # 1:1
    "1024x1024", "2048x2048", "3072x3072", "4096x4096",
    # 4:3 / 3:4
    "1152x864", "864x1152",
    "2304x1728", "1728x2304",
    "3456x2592", "2592x3456",
    # 16:9 / 9:16
    "1312x736", "736x1312",
    "2624x1472", "1472x2624",
    "3936x2208", "2208x3936",
    # 3:2 / 2:3
    "1248x832", "832x1248",
    "2496x1664", "1664x2496",
    "3744x2496", "2496x3744",
    # 21:9
    "1568x672", "3136x1344",
]

_SIZE_RE = re.compile(r"^(\d{2,5})x(\d{2,5})$", re.IGNORECASE)


def is_sensenova_provider(model: Any) -> bool:
    """ai_model.model_provider 是否指向 SenseNova 协议。"""
    provider = (getattr(model, "model_provider", None) or "").strip().lower()
    return provider in SENSENOVA_PROVIDERS


def parse_declared_sizes(ext_param: Any) -> List[str]:
    """从 ai_model.ext_param 解析模型声明的输出尺寸白名单（无声明返回空）。"""
    if isinstance(ext_param, str):
        try:
            ext_param = json.loads(ext_param)
        except (ValueError, TypeError):
            return []
    raw = ext_param.get("sizes") if isinstance(ext_param, dict) else None
    if not isinstance(raw, list):
        return []
    return [
        s for s in (str(x).strip().lower() for x in raw)
        if _SIZE_RE.match(s)
    ]


def _snap_to_constraints(size: str) -> str:
    """把任意 WxH 钳制到 SenseNova 约束：32 倍数、单边 512~4096、比例 ≤3:1。"""
    m = _SIZE_RE.match((size or "").strip().lower())
    if not m:
        return "1024x1024"
    width, height = int(m.group(1)), int(m.group(2))
    if width <= 0 or height <= 0:
        return "1024x1024"

    # 先压比例，再对齐倍数：宽高比按长边裁剪，避免非等比变形之外的额外处理
    if width / height > MAX_RATIO:
        width = int(height * MAX_RATIO)
    elif height / width > MAX_RATIO:
        height = int(width * MAX_RATIO)

    def _clamp_side(value: int) -> int:
        # 就近对齐 32 的倍数，并夹在 [512, 4096] 内
        snapped = max(SIZE_MULTIPLE, int(round(value / SIZE_MULTIPLE)) * SIZE_MULTIPLE)
        return min(MAX_SIDE, max(MIN_SIDE, snapped))

    width, height = _clamp_side(width), _clamp_side(height)

    # 两侧各自就近对齐后，比例可能被推过 3:1（如 3000x1000 → 3008x992 = 3.03）。
    # 这里把长边按 32 递减到合法为止，宁可少一点分辨率也不能让上游 400。
    while width / height > MAX_RATIO and width - SIZE_MULTIPLE >= MIN_SIDE:
        width -= SIZE_MULTIPLE
    while height / width > MAX_RATIO and height - SIZE_MULTIPLE >= MIN_SIDE:
        height -= SIZE_MULTIPLE

    return f"{width}x{height}"


def normalize_sensenova_size(size: Optional[str], supported: Optional[List[str]] = None) -> str:
    """把任务里的尺寸归一化到 SenseNova 可接受的取值。

    优先落在模型声明的白名单里（比例最接近 → 面积最接近，保构图比保分辨率重要），
    声明为空时退化为「按 32 倍数 + 范围 + 比例」钳制。
    """
    whitelist = supported if supported else []
    cur = (size or "").strip().lower()
    if cur in whitelist:
        return cur

    if whitelist:
        m = _SIZE_RE.match(cur)
        if not m:
            return whitelist[0]
        target_ratio = int(m.group(1)) / int(m.group(2))
        target_area = int(m.group(1)) * int(m.group(2))

        def score(s: str):
            sm = _SIZE_RE.match(s)
            w, h = int(sm.group(1)), int(sm.group(2))
            # 与后端 normalize_image_size / 前端 normalizeSizeForModel 同口径：
            # 不做四舍五入，保证前后端选出的尺寸逐位一致
            return (abs(w / h - target_ratio), abs(w * h - target_area))

        return min(whitelist, key=score)

    return _snap_to_constraints(cur)


def build_generation_body(
    *,
    model_name: str,
    prompt: str,
    size: str,
    response_format: str = "url",
    output_format: str = "png",
    watermark: bool = False,
    prompt_extend: bool = True,
) -> Dict[str, Any]:
    """文生图请求体（/v1/images/generations，JSON）。

    - `n` 官方仅支持 1，显式传上以免走到服务端默认值
    - `watermark=false` 关闭官方 Logo 水印（文档注明公测免费）
    - `response_format=url` 取 24 小时有效期临时链接，可直接落库到 result_url
    """
    return {
        "model": model_name,
        "prompt": prompt,
        "n": 1,
        "size": size,
        "response_format": response_format or "url",
        "output_format": output_format,
        "watermark": watermark,
        "prompt_extend": prompt_extend,
    }


def build_edits_body(
    *,
    model_name: str,
    prompt: str,
    image_urls: List[str],
    size: str,
    response_format: str = "url",
    output_format: str = "png",
    watermark: bool = False,
) -> Dict[str, Any]:
    """图片编辑/图生图请求体（/v1/images/edits，**JSON**）。

    参考图是 `images: [{"image_url": ...}]` 数组（第 1 张为主编辑图，至多 5 张），
    不是 OpenAI 那种 multipart `image` 文件字段，因此不复用 openai_image 的 edits 组装。
    """
    return {
        "model": model_name,
        "prompt": prompt,
        "images": [{"image_url": url} for url in image_urls[:5]],
        "n": 1,
        "size": size,
        "response_format": response_format or "url",
        "output_format": output_format,
        "watermark": watermark,
    }


def size_is_legal(size: str) -> bool:
    """校验 WxH 是否完全满足官方约束（供测试/自检用）。"""
    m = _SIZE_RE.match((size or "").strip().lower())
    if not m:
        return False
    w, h = int(m.group(1)), int(m.group(2))
    if w % SIZE_MULTIPLE or h % SIZE_MULTIPLE:
        return False
    if not (MIN_SIDE <= w <= MAX_SIDE and MIN_SIDE <= h <= MAX_SIDE):
        return False
    return max(w / h, h / w) <= MAX_RATIO + 1e-9


__all__ = [
    "SENSENOVA_PROVIDERS",
    "DEFAULT_SIZES",
    "is_sensenova_provider",
    "parse_declared_sizes",
    "normalize_sensenova_size",
    "build_generation_body",
    "build_edits_body",
    "size_is_legal",
]
