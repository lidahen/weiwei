"""creation 域视频摄取：yt-dlp 下载、ASR 转写、场景切镜、抽帧。

yt-dlp / scenedetect 为重量级可选依赖，均在函数内懒加载；
未安装或解析失败抛 CreationError（拆解入口另有"手动上传视频"兜底）。
"""
from __future__ import annotations

import asyncio
import logging
import re
from pathlib import Path
from typing import List, Tuple

import httpx

from app.config import settings
from app.services.creation.media_store import CreationError

logger = logging.getLogger(__name__)


# ════════════════════════════════════════════════════════════
# 下载
# ════════════════════════════════════════════════════════════

def _download_sync(source_url: str, out_dir: Path) -> Tuple[Path, str]:
    """同步下载（线程池中执行）。返回 (视频文件路径, 标题)。"""
    import yt_dlp  # 懒加载：未安装时报错可读

    outtmpl = str(out_dir / "source.%(ext)s")
    opts = {
        "outtmpl": outtmpl,
        # 竖屏优先：限制 1080p 以内，优先 mp4 便于 ffmpeg 处理
        "format": "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*+ba/b",
        "merge_output_format": "mp4",
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 30,
        "retries": 2,
        # 大小/时长兜底：拆解只需结构模板，长视频/大文件直接拒绝（防磁盘被占满）
        "max_filesize": 500 * 1024 * 1024,
        "match_filter": yt_dlp.utils.match_filter_func("duration<=1800"),
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(source_url, download=True)
        title = (info or {}).get("title") or "未命名视频"
        filepath = (info or {}).get("requested_downloads", [{}])[0].get("filepath")
    if not filepath or not Path(filepath).exists():
        # 兜底：扫描输出目录（部分版本 requested_downloads 缺失）
        candidates = sorted(out_dir.glob("source.*"),
                            key=lambda p: p.stat().st_size, reverse=True)
        if not candidates:
            raise CreationError("视频下载失败：未产出文件")
        filepath = candidates[0]
    return Path(filepath), title


async def download_video(source_url: str, target_dir: Path) -> Tuple[Path, str]:
    """下载分享链接/直链视频到 target_dir，返回 (路径, 标题)；总时长 10 分钟兜底。"""
    try:
        return await asyncio.wait_for(
            asyncio.to_thread(_download_sync, source_url, target_dir), timeout=600)
    except asyncio.TimeoutError as exc:
        logger.warning("yt-dlp 下载超时 url=%s", source_url)
        raise CreationError("视频下载超时（10 分钟），请改用「上传视频」方式拆解") from exc
    except CreationError:
        raise
    except Exception as exc:
        logger.warning("yt-dlp 下载失败 url=%s: %s", source_url, exc)
        raise CreationError(
            "视频下载失败：链接可能受平台风控限制，请改用「上传视频」方式拆解"
        ) from exc


# ════════════════════════════════════════════════════════════
# ASR（SiliconFlow，OpenAI 兼容 /audio/transcriptions）
# ════════════════════════════════════════════════════════════

def _extract_audio_sync(video: Path, out_dir: Path) -> Path:
    """抽 16k 单声道 mp3（ASR 输入），控制上传体积。"""
    out = out_dir / "asr_input.mp3"
    import subprocess

    proc = subprocess.run(
        ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
         "-i", str(video), "-vn", "-ac", "1", "-ar", "16000", "-b:a", "48k", str(out)],
        capture_output=True, text=True, timeout=300,
    )
    if proc.returncode != 0 or not out.exists():
        raise CreationError("音频提取失败（原片可能无声）")
    return out


def _asr_api_key() -> str:
    return (settings.asr_api_key or settings.tts_api_key or settings.embedding_api_key or "").strip()


async def transcribe(video: Path, target_dir: Path) -> str:
    """转写视频语音为文本；无声/未配置 key 返回空串（拆解退化为纯视觉分析）。"""
    api_key = _asr_api_key()
    if not api_key:
        logger.warning("ASR 未配置（asr_api_key/tts_api_key/embedding_api_key 均为空），跳过转写")
        return ""
    audio = await asyncio.to_thread(_extract_audio_sync, video, target_dir)
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(settings.asr_timeout)) as client:
            with open(audio, "rb") as f:
                resp = await client.post(
                    f"{settings.asr_base_url.rstrip('/')}/audio/transcriptions",
                    headers={"Authorization": f"Bearer {api_key}"},
                    files={"file": (audio.name, f, "audio/mpeg")},
                    data={"model": settings.asr_model},
                )
        if resp.status_code != 200:
            logger.warning("ASR 失败 status=%s body=%s", resp.status_code, resp.text[:300])
            return ""
        data = resp.json()
        return (data.get("text") or "").strip()
    except Exception as exc:  # noqa: BLE001 —— 转写失败不阻断拆解
        logger.warning("ASR 异常（跳过转写）: %s", exc)
        return ""


# ════════════════════════════════════════════════════════════
# 场景切镜 / 抽帧
# ════════════════════════════════════════════════════════════

def _detect_scenes_sync(video: Path) -> List[Tuple[float, float]]:
    """PySceneDetect 内容检测，返回 [(start_s, end_s)]；无场景变化时返回单段全片。"""
    try:
        from scenedetect import ContentDetector, detect  # 懒加载
    except ImportError:
        logger.warning("scenedetect 未安装，退化为单段全片")
        return []

    try:
        scenes = detect(str(video), ContentDetector(threshold=27.0))
        return [(s[0].get_seconds(), s[1].get_seconds()) for s in scenes]
    except Exception as exc:  # noqa: BLE001
        logger.warning("切镜失败（退化为单段全片）: %s", exc)
        return []


async def detect_scenes(video: Path) -> List[dict]:
    """返回切镜列表 [{index, start, end, duration}]；检测失败时为空列表。"""
    raw = await asyncio.to_thread(_detect_scenes_sync, video)
    return [
        {"index": i + 1, "start": round(s, 2), "end": round(e, 2), "duration": round(e - s, 2)}
        for i, (s, e) in enumerate(raw)
    ]


async def extract_frames(video: Path, target_dir: Path, *, max_frames: int = 8) -> List[Path]:
    """均匀抽帧（供 LLM 视觉分析），返回本地 jpg 路径列表。"""
    import subprocess

    from app.services.creation.media_store import probe_duration_async

    duration = await probe_duration_async(video)
    if duration <= 0:
        return []
    count = min(max_frames, max(1, int(duration // 2)))
    step = duration / (count + 1)
    paths: List[Path] = []
    for i in range(1, count + 1):
        out = target_dir / f"frame_{i:02d}.jpg"
        proc = await asyncio.to_thread(
            subprocess.run,
            ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
             "-ss", f"{step * i:.2f}", "-i", str(video),
             "-frames:v", "1", "-q:v", "3", "-vf", "scale=720:-2", str(out)],
            capture_output=True, text=True, timeout=120,
        )
        if proc.returncode == 0 and out.exists():
            paths.append(out)
    return paths


def split_sentences(text: str) -> List[str]:
    """口播稿 → 句级字幕句（按中英文终止符切分，过短句并入前句）。"""
    parts = re.split(r"(?<=[。！？!?；;……])\s*", text.strip())
    sentences = [p.strip() for p in parts if p and p.strip()]
    merged: List[str] = []
    for s in sentences:
        if merged and (len(s) < 4 or len(merged[-1]) + len(s) <= 18):
            merged[-1] += s
        else:
            merged.append(s)
    return merged
