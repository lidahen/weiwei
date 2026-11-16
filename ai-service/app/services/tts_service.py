"""TTS 语音合成服务。

走 OpenAI 兼容的 POST /audio/speech 协议（默认 SiliconFlow CosyVoice2），
返回音频二进制（mp3）。key 未配置时抛出 TTSConfigError，由路由层转成业务错误响应。
"""
import logging
from typing import Optional

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class TTSError(Exception):
    """TTS 合成失败（上游报错/网络异常），message 可直接展示给用户。"""


def _resolve_api_key() -> str:
    return (settings.tts_api_key or settings.embedding_api_key or "").strip()


def _build_speech_url() -> str:
    return f"{settings.tts_base_url.rstrip('/')}/audio/speech"


async def synthesize(
    text: str,
    voice: Optional[str] = None,
    speed: Optional[float] = None,
) -> bytes:
    """合成语音，返回音频二进制。

    :param text: 待合成文本（路由层已做非空与长度截断）
    :param voice: 发音人（默认 settings.tts_voice）
    :param speed: 语速 0.25~4.0（OpenAI 兼容语义）
    """
    api_key = _resolve_api_key()
    if not api_key:
        raise TTSError("TTS 未配置：请在 .env 中设置 TTS_API_KEY（或 EMBEDDING_API_KEY）")

    payload: dict = {
        "model": settings.tts_model,
        "input": text,
        "voice": voice or settings.tts_voice,
        "response_format": settings.tts_response_format,
    }
    if speed is not None:
        try:
            s = float(speed)
            if 0.25 <= s <= 4.0:
                payload["speed"] = s
        except (TypeError, ValueError):
            pass

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(
                connect=10.0,
                read=settings.tts_timeout,
                write=10.0,
                pool=10.0,
            )
        ) as client:
            response = await client.post(_build_speech_url(), json=payload, headers=headers)
    except httpx.TimeoutException as exc:
        logger.warning("TTS request timeout: %s", exc)
        raise TTSError("语音合成超时，请稍后重试") from exc
    except httpx.HTTPError as exc:
        logger.warning("TTS request failed: %s", exc)
        raise TTSError("语音合成服务不可用，请稍后重试") from exc

    if response.status_code != 200:
        logger.error(
            "TTS upstream error: status=%d body=%s",
            response.status_code,
            response.text[:200],
        )
        raise TTSError(f"语音合成失败（上游 {response.status_code}），请稍后重试")

    audio = response.content
    if not audio:
        raise TTSError("语音合成返回空音频，请稍后重试")
    return audio
