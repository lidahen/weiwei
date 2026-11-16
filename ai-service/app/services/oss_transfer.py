"""临时图片 URL → 阿里云 OSS 转存。

背景：SenseNova（sensenova-* 模型）的 /v1/images/generations|edits 固定返回
response_format=url 的 24 小时有效期预签名 URL（aoss.cn-sh-01.sensecoreapi-oss.cn，
带 X-Amz-Algorithm 签名参数），直接落库 ai_image_task.result_url 后 24h 必然 403 裂图。
（2026-09-25 实测爆发：task 64/72 两条已失效。）

方案：任务完成时下载图片字节，用 V1 签名直传阿里云 OSS（virtual-hosted-style），
落库永久公开读 URL。Agnes（platform-outputs.agnes-ai.space）与 senseaudio/doubao
（dynamic.senseaudio.cn）链接实测永久有效，不触发转存。

实现说明：
- 纯标准库签名（hmac-sha1），不引入 oss2 依赖；
- V1 签名的 CanonicalizedResource 必须带 /bucket/object 全路径，
  x-oss-* 头必须纳入 CanonicalizedOSSHeaders，否则 SignatureDoesNotMatch；
- 转存失败返回 None 并由调用方回退落库原始 URL（宁可有 24h 链接也别让任务失败）。
"""
import base64
import hashlib
import hmac
import logging
import uuid
from email.utils import formatdate
from typing import Optional
from urllib.parse import urlparse

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


def _sign_v1(secret_key: str, verb: str, content_type: str, date: str, resource: str) -> str:
    """OSS V1 签名：VERB\\n\\nContent-Type\\nDate\\nCanonicalizedResource。"""
    string_to_sign = f"{verb}\n\n{content_type}\n{date}\n{resource}"
    digest = hmac.new(
        secret_key.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha1
    ).digest()
    return base64.b64encode(digest).decode("ascii")


def _guess_ext(content_type: str, url_path: str) -> str:
    """按响应 Content-Type / URL 后缀推断扩展名，默认 png。"""
    ct = (content_type or "").lower()
    if "jpeg" in ct or "jpg" in ct:
        return "jpg"
    if "webp" in ct:
        return "webp"
    if "gif" in ct:
        return "gif"
    tail = url_path.rsplit(".", 1)
    if len(tail) == 2 and tail[1].lower() in {"png", "jpg", "jpeg", "webp", "gif"}:
        return "jpg" if tail[1].lower() == "jpeg" else tail[1].lower()
    return "png"


async def transfer_url_to_oss(url: str, *, prefix: str = "ai-image") -> Optional[str]:
    """下载 url 指向的图片并上传到阿里云 OSS，返回永久公开读 URL；失败返回 None。"""
    ak = (settings.oss_access_key or "").strip()
    sk = (settings.oss_secret_key or "").strip()
    bucket = (settings.oss_bucket or "").strip()
    endpoint = (settings.oss_endpoint or "").strip().rstrip("/")
    if not (ak and sk and bucket and endpoint):
        logger.warning("OSS 转存配置不完整（oss_access_key/secret/bucket/endpoint），跳过转存")
        return None

    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(settings.oss_transfer_timeout)) as client:
            # 1. 下载源图片
            resp = await client.get(url)
            resp.raise_for_status()
            content = resp.content
            if not content:
                logger.warning("OSS 转存：源 URL 返回空内容，跳过")
                return None
            ext = _guess_ext(resp.headers.get("content-type", ""), urlparse(url).path)
            content_type = f"image/{ext}"

            # 2. V1 签名直传（x-oss-object-acl 需同时出现在请求头与签名串里）
            key = f"{prefix}/{uuid.uuid4().hex}.{ext}"
            host = f"{bucket}.{urlparse(endpoint).netloc}"
            resource = f"/{bucket}/{key}"
            date = formatdate(usegmt=True)
            sts_headers = "x-oss-object-acl:public-read\n"
            string_to_sign = f"PUT\n\n{content_type}\n{date}\n{sts_headers}{resource}"
            signature = base64.b64encode(
                hmac.new(sk.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha1).digest()
            ).decode("ascii")

            put_resp = await client.put(
                f"https://{host}/{key}",
                content=content,
                headers={
                    "Date": date,
                    "Content-Type": content_type,
                    "x-oss-object-acl": "public-read",
                    "Authorization": f"OSS {ak}:{signature}",
                },
            )
            if put_resp.status_code != 200:
                logger.warning(
                    "OSS 转存上传失败 status=%s body=%s", put_resp.status_code, put_resp.text[:200]
                )
                return None

            return f"https://{host}/{key}"
    except Exception as exc:  # noqa: BLE001 —— 转存失败不允许影响任务主流程
        logger.warning("OSS 转存异常（回退落库原始 URL）: %s", exc)
        return None


__all__ = ["transfer_url_to_oss"]
