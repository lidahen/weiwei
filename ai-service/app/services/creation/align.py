"""WhisperX 词级对齐（可选增强，对应 hypit 的"锚定到词"思路）。

定位：
- 本地增强包：`pip install whisperx`（含 faster-whisper/torch）后才生效；
  未安装或初始化失败一律返回 None，调用方回退到按字数分摊的句级 SRT，链路不断。
- 对齐对象是 TTS 干声（单说话人、无噪声、文本已知），对齐质量远高于带噪原片；
  模型用 base/small 档足够，显存 4G 内可跑（RTX 5060 8G 用 cuda+float16 余量充足）。
- GPU 初始化失败（驱动/sm_120 兼容等）自动降级 CPU int8，再失败回退 None。

线程安全：whisperx 模型非协程安全且显存单进程独占，全局单例 + asyncio.Lock 串行化，
锁只覆盖「加载 + 推理」，多镜并行生成时各镜依次对齐（单镜音频几秒，耗时毫秒级-秒级）。
"""
from __future__ import annotations

import asyncio
import logging
import re
from pathlib import Path
from typing import Optional

from app.config import settings

logger = logging.getLogger(__name__)

# 懒加载缓存：(whisper 模型, 对齐模型, metadata, device)
_aligner: Optional[tuple] = None
_align_lock = asyncio.Lock()
_load_failed = False  # 首次加载失败后不再反复尝试（进程生命周期内一次）

_PUNCT_RE = re.compile(r"[，。！？!?；;、……\s,.]")


def normalize_text(text: str) -> str:
    """去标点/空白，供 ASR 词与台词文本做相似度比对。"""
    return _PUNCT_RE.sub("", (text or "").strip())


def _resolve_device() -> tuple[str, str]:
    """按配置解析 (device, compute_type)；auto→cuda:float16 / cpu:int8。"""
    device = (settings.creation_align_device or "auto").strip().lower()
    compute = (settings.creation_align_compute_type or "auto").strip().lower()
    if device == "auto":
        try:
            import torch

            device = "cuda" if torch.cuda.is_available() else "cpu"
        except Exception:  # noqa: BLE001 —— torch 不可用一律 CPU
            device = "cpu"
    if compute == "auto":
        compute = "float16" if device == "cuda" else "int8"
    return device, compute


async def _load_aligner() -> Optional[tuple]:
    """加载并缓存 whisperx 模型（在锁内调用；失败置全局标记不再重试）。"""
    global _aligner, _load_failed

    def _load() -> tuple:
        import whisperx

        device, compute = _resolve_device()
        logger.info("whisperx 对齐模型加载中 model=%s device=%s compute=%s",
                    settings.creation_align_model, device, compute)
        model = whisperx.load_model(settings.creation_align_model, device, compute_type=compute)
        align_model, metadata = whisperx.load_align_model(
            language_code=settings.creation_align_language, device=device)
        return model, align_model, metadata, device

    try:
        _aligner = await asyncio.to_thread(_load)
        logger.info("whisperx 对齐模型就绪")
        return _aligner
    except Exception as exc:  # noqa: BLE001
        _load_failed = True
        logger.warning("whisperx 初始化失败（词级字幕回退句级分摊，可 pip install whisperx 启用）: %s", exc)
        return None


async def align_words(audio_path: Path) -> Optional[list[dict]]:
    """对单镜 TTS 音频做词级对齐。

    返回 [{word, start, end}]（秒，相对本段音频起点）；未启用/失败返回 None。
    """
    if not settings.creation_align_enabled or _load_failed:
        return None
    async with _align_lock:
        if _aligner is None and not _load_failed:
            if await _load_aligner() is None:
                return None
        model, align_model, metadata, device = _aligner  # type: ignore[misc]

        def _run() -> Optional[list[dict]]:
            import whisperx

            audio = whisperx.load_audio(str(audio_path))
            result = model.transcribe(audio, language=settings.creation_align_language)
            if not result.get("segments"):
                return None
            aligned = whisperx.align(
                result["segments"], align_model, metadata, str(audio_path), device,
                return_char_alignments=False,
            )
            words: list[dict] = []
            for seg in aligned.get("segments") or []:
                for w in seg.get("words") or []:
                    if w.get("start") is None or w.get("end") is None:
                        continue
                    token = (w.get("word") or "").strip()
                    if token:
                        words.append({"word": token, "start": w["start"], "end": w["end"]})
            return words or None

        try:
            return await asyncio.to_thread(_run)
        except Exception as exc:  # noqa: BLE001 —— 单镜对齐失败不阻断渲染
            logger.warning("词级对齐失败（该次回退句级字幕）: %s", exc)
            return None
