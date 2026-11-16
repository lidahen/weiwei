"""creation 域媒体基础设施：工作目录、产物上传（OSS/本地回退）、FFmpeg 封装。

产物存储策略：
- OSS 已配置（oss_access_key 非空）→ 所有生成产物转存阿里云 OSS，落库永久 URL；
- OSS 未配置 → 落本地 creation_work_dir，URL 记为 /ai/creation/media/{relpath}，
  由路由层 FileResponse 提供访问（开发环境兜底，重启不丢、部署可换盘）。
"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import ipaddress
import json
import logging
import shutil
import socket
import subprocess
import time
import uuid
from email.utils import formatdate
from pathlib import Path
from typing import List, Optional
from urllib.parse import urlparse

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class CreationError(Exception):
    """创作域业务错误，message 可直接展示给用户。"""


# ════════════════════════════════════════════════════════════
# 工作目录
# ════════════════════════════════════════════════════════════

def work_dir(*parts) -> Path:
    """creation 域工作子目录（自动创建），如 work_dir("template_3")。

    返回绝对路径：ffmpeg 各步骤混用 cwd 与 str(path) 传参时，相对路径会被
    不同基准（进程 cwd / concat 清单目录）二次拼接，绝对路径一次性消除该类 bug。
    """
    d = Path(settings.creation_work_dir).joinpath(*[str(p) for p in parts])
    d.mkdir(parents=True, exist_ok=True)
    return d.resolve()


def cleanup_work_dir(*parts) -> None:
    d = Path(settings.creation_work_dir).joinpath(*[str(p) for p in parts])
    if d.exists():
        shutil.rmtree(d, ignore_errors=True)


def cleanup_expired_workdirs(ttl_hours: int) -> List[str]:
    """删除 mtime 超过 TTL 的临时工作目录（合成/拆解中间件与上传源文件）。

    只清理 project_* / template_* / uploads——files/ 存放的是 OSS 未配置时的
    本地回退正式产物（URL 已落库），清理会导致成片/分镜图 404，永不触碰。
    """
    base = Path(settings.creation_work_dir)
    if not base.exists() or ttl_hours <= 0:
        return []
    deadline = time.time() - ttl_hours * 3600
    removed: List[str] = []
    for child in base.iterdir():
        if not (child.is_dir() and (child.name.startswith(("project_", "template_")) or child.name == "uploads")):
            continue
        try:
            if child.stat().st_mtime > deadline:
                continue
            shutil.rmtree(child, ignore_errors=True)
            removed.append(child.name)
        except OSError as exc:  # noqa: PERF203 —— 单目录失败不阻断其余清理
            logger.warning("工作目录清理失败 %s: %s", child, exc)
    if removed:
        logger.info("creation 工作目录清理（TTL %sh）: %s", ttl_hours, ", ".join(removed))
    return removed


def start_ttl_cleanup_loop() -> "asyncio.Task[None]":
    """启动周期清理协程（启动时清一次，此后每 6 小时一次），返回可取消的任务句柄。"""

    async def _loop() -> None:
        while True:
            try:
                await asyncio.to_thread(cleanup_expired_workdirs, settings.creation_workdir_ttl_hours)
            except Exception as exc:  # noqa: BLE001 —— 清理失败不影响服务
                logger.warning("creation TTL 清理异常: %s", exc)
            await asyncio.sleep(6 * 3600)

    return asyncio.create_task(_loop(), name="creation-ttl-cleanup")


# ════════════════════════════════════════════════════════════
# 产物上传
# ════════════════════════════════════════════════════════════

_CONTENT_TYPE_BY_EXT = {
    ".mp4": "video/mp4", ".mov": "video/quicktime", ".webm": "video/webm",
    ".mp3": "audio/mpeg", ".wav": "audio/wav", ".m4a": "audio/mp4",
    ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp",
    ".srt": "text/plain", ".json": "application/json",
}


def _oss_sign_put(secret_key: str, content_type: str, date: str, key: str) -> str:
    """OSS V1 签名（与 oss_transfer 同款：public-read 需纳入签名串）。"""
    bucket = settings.oss_bucket
    resource = f"/{bucket}/{key}"
    sts_headers = "x-oss-object-acl:public-read\n"
    string_to_sign = f"PUT\n\n{content_type}\n{date}\n{sts_headers}{resource}"
    digest = hmac.new(secret_key.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha1).digest()
    return base64.b64encode(digest).decode("ascii")


async def upload_bytes(data: bytes, *, filename: str, prefix: str = "ai-creation") -> str:
    """上传产物字节 → OSS（已配置）或本地（回退），返回可访问 URL。"""
    ext = Path(filename).suffix.lower() or ".bin"
    content_type = _CONTENT_TYPE_BY_EXT.get(ext, "application/octet-stream")
    ak = (settings.oss_access_key or "").strip()
    sk = (settings.oss_secret_key or "").strip()
    if settings.oss_transfer_enabled and ak and sk:
        try:
            key = f"{prefix}/{uuid.uuid4().hex}{ext}"
            date = formatdate(usegmt=True)
            signature = _oss_sign_put(sk, content_type, date, key)
            host = f"{settings.oss_bucket}.{urlparse(settings.oss_endpoint).netloc}"
            async with httpx.AsyncClient(timeout=httpx.Timeout(settings.oss_transfer_timeout)) as client:
                resp = await client.put(
                    f"https://{host}/{key}",
                    content=data,
                    headers={
                        "Date": date,
                        "Content-Type": content_type,
                        "x-oss-object-acl": "public-read",
                        "Authorization": f"OSS {ak}:{signature}",
                    },
                )
            if resp.status_code == 200:
                return f"https://{host}/{key}"
            logger.warning("creation OSS 上传失败 status=%s body=%s", resp.status_code, resp.text[:200])
        except Exception as exc:  # noqa: BLE001 —— 上传失败回退本地
            logger.warning("creation OSS 上传异常（回退本地）: %s", exc)

    # 本地回退：data/creation/files/xxx.ext
    local = work_dir("files")
    name = f"{uuid.uuid4().hex}{ext}"
    await asyncio.to_thread((local / name).write_bytes, data)
    return f"/ai/creation/media/files/{name}"


async def upload_file(path: Path, *, prefix: str = "ai-creation") -> str:
    """上传本地文件产物，同 upload_bytes（文件读取放线程池，避免大文件阻塞事件循环）。"""
    data = await asyncio.to_thread(path.read_bytes)
    return await upload_bytes(data, filename=path.name, prefix=prefix)


def is_local_media_url(url: Optional[str]) -> bool:
    return bool(url) and url.startswith("/ai/creation/media/")


def local_path_of(url: str) -> Path:
    """本地媒体 URL → 磁盘路径（仅限 creation_work_dir 内，防穿越）。

    用 resolve + is_relative_to 判断包含关系：前缀字符串匹配会被
    `../creation-evil/x` 这类 resolve 后以 base 前缀开头的兄弟目录绕过。
    """
    rel = url.removeprefix("/ai/creation/media/")
    base = Path(settings.creation_work_dir).resolve()
    target = (base / rel).resolve()
    if not target.is_relative_to(base):
        raise CreationError("非法媒体路径")
    return target


def assert_public_http_url(url: str, *, what: str = "链接") -> None:
    """外链下载前的 SSRF 防护：仅允许 http/https 且解析结果全部为公网地址。

    用于用户可控 URL（拆解 source_url / 分镜 BGM 直链），阻止内网探测与
    169.254.169.254 一类元数据端点访问。DNS 重绑定不在本 MVP 防护范围。
    """
    try:
        parsed = urlparse(url)
    except ValueError as exc:
        raise CreationError(f"{what}格式非法") from exc
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise CreationError(f"{what}仅支持 http/https 地址")
    try:
        infos = socket.getaddrinfo(parsed.hostname, None)
    except socket.gaierror as exc:
        raise CreationError(f"{what}域名无法解析: {parsed.hostname}") from exc
    for info in infos:
        addr = ipaddress.ip_address(info[4][0])
        if not addr.is_global or addr.is_reserved or addr.is_multicast:
            raise CreationError(f"{what}不允许指向内网/保留地址")


# ════════════════════════════════════════════════════════════
# FFmpeg / FFprobe
# ════════════════════════════════════════════════════════════

def _run(cmd: list[str], *, timeout: int = 600, cwd: Optional[Path] = None) -> str:
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace", cwd=str(cwd) if cwd else None)
    if proc.returncode != 0:
        tail = (proc.stderr or "")[-800:]
        raise CreationError(f"FFmpeg 执行失败: {tail}")
    return proc.stdout


def run_ffmpeg(args: list[str], *, timeout: int = 1800, cwd: Optional[Path] = None) -> None:
    """执行 ffmpeg（args 为 ffmpeg 参数，不含程序名）；cwd 用于相对路径场景。"""
    _run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *args], timeout=timeout, cwd=cwd)


def probe_duration(path: Path) -> float:
    """ffprobe 取媒体时长（秒），失败抛 CreationError。"""
    out = _run([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "json", str(path),
    ], timeout=60)
    try:
        return float(json.loads(out)["format"]["duration"])
    except (KeyError, ValueError, TypeError) as exc:
        raise CreationError(f"无法读取媒体时长: {path.name}") from exc


async def probe_duration_async(path: Path) -> float:
    return await asyncio.to_thread(probe_duration, path)


def extract_frame(path: Path, at_sec: float, out: Path) -> Path:
    """抽取指定时刻帧为 jpg（封面用）。"""
    run_ffmpeg([
        "-ss", f"{max(0.0, at_sec):.2f}", "-i", str(path),
        "-frames:v", "1", "-q:v", "2", str(out),
    ], timeout=120)
    return out
