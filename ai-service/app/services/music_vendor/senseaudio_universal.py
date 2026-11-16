"""商汤 senseaudio 通用 vendor —— 单一类覆盖全部 35 个模型能力。

按 model_name 子串路由到对应端点:

  含 image        → /v1/image/sync          (image_generate)
  含 music        → /v2/music/song/create   (submit_song / poll_song)
                    /v1/music/song/pending
                    /v1/music/lyrics/create (draft_lyrics)
                    /v1/music/lyrics/revise (revise_lyrics)
                    /v1/get_voice           (list_platform_voices)
  含 seedance     → /v1/video/create        (create_video / poll_video)
  含 tts          → /v1/t2a_v2              (synthesize_tts, 已实装 2026-10-03)
  含 asr          → /v1/audio/transcriptions (transcribe, 待实现)
  含 voice-isolation → /v1/isolation/sync   (isolate_vocals, 待实现)
  含 livetranslate  → /ws/v1/audio/simulat-interpreting (待实现)
  含 sfx          → /v1/sound-effects/generations (generate_sfx, 待实现)
  含 realtime     → /v1/realtime/invoke     (待实现)
  senseaudio-a1   → /v1/audio/generate      (数字人, 待实现)

未实现的能力调对应方法会抛 NotImplementedError, 错误信息含端点路径便于后续实现。
"""
from __future__ import annotations

import asyncio
import json
import logging
import re
from typing import Any, Optional

import httpx

from app.services.music_vendor.base import (
    MusicVendorError,
    VendorNotConfiguredError,
)

logger = logging.getLogger(__name__)


class SenseaudioRejectedError(MusicVendorError):
    """上游拒绝本次生成（多为内容安全审核不通过），重试不可解，需改输入或换模型。"""


# ── 图片尺寸归一化 ────────────────────────────────────────────
# /v1/image/sync 只接受模型声明的官方档位（ai_model.ext_param.sizes），
# 传其它尺寸（如 Agnes 档位表里的 4:3 → 2304x1728）属于未定义行为，可能被上游拒。
_DEFAULT_IMAGE_SIZES = ["1024x1024", "1536x864", "864x1536", "2048x1152", "3840x2160"]
_SIZE_RE = re.compile(r"^(\d{2,5})x(\d{2,5})$", re.IGNORECASE)
# 上游以 400 + 该 code 表示「输入内容/参数被拒」，其中绝大多数是内容安全审核
_REJECT_CODE = "upstream_invalid_request"
_REJECT_REF = "400100"

REJECTED_HINT = (
    "商汤上游拒绝了本次生成：通常是提示词或参考图未通过内容安全审核"
    "（也可能是不支持的尺寸等参数）。请修改描述后重试，或改用其它图片模型。"
)


def parse_supported_sizes(ext_param: Any) -> list:
    """从 ai_model.ext_param（JSON 字符串或 dict）解析图片支持尺寸；无声明回落官方档位。"""
    if isinstance(ext_param, str):
        try:
            ext_param = json.loads(ext_param)
        except (ValueError, TypeError):
            ext_param = None
    raw = ext_param.get("sizes") if isinstance(ext_param, dict) else None
    if isinstance(raw, list):
        valid = [str(s).strip().lower() for s in raw if _SIZE_RE.match(str(s).strip())]
        if valid:
            return valid
    return list(_DEFAULT_IMAGE_SIZES)


def normalize_image_size(size: str, supported: Optional[list] = None) -> str:
    """把任意 WxH 归一化到商汤支持的档位。

    策略：优先「宽高比最接近」，并列时取「面积最接近」—— 保构图比保分辨率更重要。
    认不出的尺寸回落到列表首项。
    """
    sizes = supported or list(_DEFAULT_IMAGE_SIZES)
    cur = (size or "").strip().lower()
    if cur in sizes:
        return cur
    m = _SIZE_RE.match(cur)
    if not m:
        return sizes[0]
    target_ratio = int(m.group(1)) / int(m.group(2))
    target_area = int(m.group(1)) * int(m.group(2))

    def score(s: str):
        sm = _SIZE_RE.match(s)
        w, h = int(sm.group(1)), int(sm.group(2))
        # 不 round：与前端 normalizeSizeForModel 保持逐位一致的比较口径，
        # 否则「比例差四舍五入后并列」时前后端可能选出不同尺寸
        return (abs(w / h - target_ratio), abs(w * h - target_area))

    return min(sizes, key=score)


# ── 图片 reference 预处理 ──
# senseaudio 的 reference 字段只接受 https URL 或 data URL。
# 私有 OSS 链接可能签名过期/被拒, 我们服务端中转下载并转 base64 data URL。
# 注意: doubao-seedance 限制单张图片 ≤ 30MB, 大文件请勿用 Base64 编码。
# 所以超过 _MAX_INLINE_IMAGE_BYTES 的图保留原 URL 让 senseaudio 自己 fetch。
_DOWNLOAD_TIMEOUT = 30.0
_MAX_INLINE_IMAGE_BYTES = 2 * 1024 * 1024  # 2MB, base64 后 ~2.7MB, 安全范围内


def _guess_image_mime(url: str, content_type: Optional[str] = None) -> str:
    """根据 URL 后缀或 Content-Type 推断 MIME, 默认为 image/jpeg。"""
    if content_type:
        ct = content_type.split(";")[0].strip().lower()
        if ct.startswith("image/"):
            return ct
    lower = url.lower().split("?")[0]
    for ext, mime in (
        (".png", "image/png"),
        (".jpg", "image/jpeg"),
        (".jpeg", "image/jpeg"),
        (".webp", "image/webp"),
        (".bmp", "image/bmp"),
    ):
        if lower.endswith(ext):
            return mime
    return "image/jpeg"


async def _prepare_reference(url_or_data: str) -> str:
    """如果是 data URL 直接返回; 下载 + 压缩 + 转 base64 data URL。

    senseaudio 对 doubao-seedance 限制:
      - 单图 ≤ 30 MB (data URL 形式, base64 编码会再大 33%)
      - "大文件请勿使用 Base64 编码"
      - 宽高比 0.4-2.5
      - 宽高 300-6000 px
    所以:
      - ≤ 2MB 原图 → 直接转 base64 (快速, 无损)
      - > 2MB 原图 → PIL 压缩到长边 ≤ 2048 px + JPEG 85% 质量, 再 base64
      - 压缩后仍 > 8MB → 保留原 URL (让 senseaudio 自己 fetch)
    """
    if not url_or_data:
        return url_or_data
    # 已经是 data URL 直接用
    if url_or_data.startswith("data:"):
        return url_or_data
    # http(s) URL: 下载 + 压缩 + 转 base64
    if url_or_data.startswith("http://") or url_or_data.startswith("https://"):
        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(
                    connect=10.0, read=_DOWNLOAD_TIMEOUT, write=10.0, pool=10.0
                ),
                follow_redirects=True,
            ) as client:
                resp = await client.get(url_or_data)
                if resp.status_code != 200:
                    logger.warning(
                        "下载 reference 图失败: url=%s status=%d, 退回原始 URL",
                        url_or_data[:120], resp.status_code,
                    )
                    return url_or_data
                content = resp.content
                mime = _guess_image_mime(url_or_data, resp.headers.get("content-type"))
                # 大图: PIL 压缩
                if len(content) > _MAX_INLINE_IMAGE_BYTES:
                    content = await asyncio.to_thread(_compress_image, content, mime)
                    mime = "image/jpeg"  # 压缩后统一 JPEG
                    if len(content) > 8 * 1024 * 1024:
                        # 压缩后仍太大, 保留原 URL
                        logger.warning(
                            "reference 图压缩后仍过大 (%d bytes), 退回原始 URL",
                            len(content),
                        )
                        return url_or_data
                import base64
                data_url = f"data:{mime};base64,{base64.b64encode(content).decode()}"
                logger.info(
                    "reference 图转 data URL: src=%s size=%d mime=%s",
                    url_or_data[:80], len(content), mime,
                )
                return data_url
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning(
                "下载 reference 图异常: url=%s err=%s, 退回原始 URL",
                url_or_data[:120], exc,
            )
            return url_or_data
    return url_or_data


def _compress_image(content: bytes, mime: str) -> bytes:
    """PIL 压缩图到长边 ≤ 2048 px, JPEG 质量 85。"""
    try:
        from PIL import Image
        import io
        img = Image.open(io.BytesIO(content))
        # 保持宽高比, 长边 ≤ 2048
        max_side = 2048
        w, h = img.size
        if max(w, h) > max_side:
            if w >= h:
                new_w = max_side
                new_h = int(h * max_side / w)
            else:
                new_h = max_side
                new_w = int(w * max_side / h)
            img = img.resize((new_w, new_h), Image.LANCZOS)
        # 统一转 RGB (避免 RGBA 编码失败)
        if img.mode in ("RGBA", "P"):
            img = img.convert("RGB")
        out = io.BytesIO()
        img.save(out, format="JPEG", quality=85, optimize=True)
        return out.getvalue()
    except Exception as exc:
        logger.warning("PIL 压缩失败, 返回原图: %s", exc)
        return content


class SenseaudioUniversalVendor:
    """统一 vendor, 由 vendor_router 按 model_type+provider 创建实例并注入 model_name."""

    name = "senseaudio"

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str = "https://api.senseaudio.cn",
        model_name: str = "",
        timeout: int = 120,  # 图片/视频生成可能 30-90s, 默认 120s 留余量
    ):
        self.api_key = api_key.strip()
        self.base_url = base_url.rstrip("/")
        self.model_name = model_name
        self.timeout = timeout
        if not self.api_key:
            raise VendorNotConfiguredError("senseaudio api_key 为空")

    def override_model(self, model_name: str) -> "SenseaudioUniversalVendor":
        """临时覆盖 model_name(单次调用),不修改 self."""
        new = SenseaudioUniversalVendor.__new__(SenseaudioUniversalVendor)
        new.api_key = self.api_key
        new.base_url = self.base_url
        new.model_name = model_name
        new.timeout = self.timeout
        new.name = self.name
        return new

    # ── 路由判定 ──

    def _route(self) -> str:
        n = self.model_name.lower()
        if "image" in n:
            return "image"
        if "music" in n:
            return "music"
        if "seedance" in n or "video" in n:
            return "video"
        if "tts" in n:
            return "tts"
        if "asr" in n:
            return "asr"
        if "voice-isolation" in n or "isolation" in n:
            return "isolate"
        if "livetranslate" in n:
            return "translate"
        if "sfx" in n:
            return "sfx"
        if "realtime" in n:
            return "realtime"
        if "a1" in n:
            return "audio_gen"
        # 文本/视觉对话走 OpenAI 兼容 /v1/chat/completions
        return "chat"

    def _headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

    async def _request(self, method: str, path: str, **kwargs) -> dict:
        url = f"{self.base_url}{path}"
        # DEBUG: log 请求体(去掉 base64 data URL 太长部分)
        try:
            import json as _json
            if "json" in kwargs:
                _dbg = dict(kwargs["json"])
                if "content" in _dbg:
                    _dbg["content"] = [
                        {**c, "url": (c.get("url", "")[:80] + "..." if c.get("url", "").startswith("data:") and len(c.get("url", "")) > 80 else c.get("url", ""))}
                        for c in _dbg["content"]
                    ]
                if "reference" in _dbg and isinstance(_dbg.get("reference"), str) and _dbg["reference"].startswith("data:"):
                    _dbg["reference"] = _dbg["reference"][:80] + "..."
                print(f"[DEBUG senseaudio] {method} {path} payload: {_json.dumps(_dbg, ensure_ascii=False)}", flush=True)
        except Exception as e:
            print(f"[DEBUG senseaudio] log error: {e}", flush=True)

        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(connect=10.0, read=self.timeout, write=30.0, pool=10.0)
            ) as client:
                resp = await client.request(method, url, headers=self._headers(), **kwargs)
        except httpx.TimeoutException as exc:
            raise MusicVendorError(f"商汤上游超时: {exc}") from exc
        except httpx.HTTPError as exc:
            raise MusicVendorError(f"商汤上游不可用: {exc}") from exc
        if resp.status_code != 200:
            logger.warning("senseaudio %s %s failed: %d %s", method, path, resp.status_code, resp.text[:200])
            body = resp.text[:200]
            # 400 + upstream_invalid_request/400100：内容审核拦截或参数不受支持，
            # 抛可识别的专用异常并给出可操作提示，避免把上游报文原文丢给用户。
            if resp.status_code == 400 and (_REJECT_CODE in body or _REJECT_REF in body):
                raise SenseaudioRejectedError(REJECTED_HINT)
            raise MusicVendorError(f"商汤调用失败({method} {path} status={resp.status_code}): {body}")
        return resp.json()

    # ════════════════════════════════════════════════════════════
    # IMAGE
    # ════════════════════════════════════════════════════════════

    async def image_generate(self, *, prompt: str, size: str = "1536x864", reference: Optional[str] = None, seed: Optional[int] = None) -> dict:
        payload: dict[str, Any] = {"model": self.model_name, "prompt": prompt, "size": size}
        if reference:
            payload["reference"] = await _prepare_reference(reference)
        if seed is not None:
            payload["seed"] = seed
        data = await self._request("POST", "/v1/image/sync", json=payload)
        return {"url": data.get("url")}

    # ════════════════════════════════════════════════════════════
    # MUSIC
    # ════════════════════════════════════════════════════════════

    async def list_platform_voices(self, voice_type: str = "all") -> list[dict]:
        data = await self._request("POST", "/v1/get_voice", json={"voice_type": voice_type})
        if data.get("base_resp", {}).get("status_code") not in (0, None):
            raise MusicVendorError(f"查询音色失败: {data.get('base_resp', {}).get('status_msg')}")
        results: list[dict] = []
        category_map = {"system_voice": "system", "voice_cloning": "voice_clone", "voice_generation": "voice_generation"}
        for raw_key, category in category_map.items():
            for item in data.get(raw_key, []) or []:
                results.append({
                    "voice_id": item.get("voice_id"),
                    "voice_name": item.get("voice_name"),
                    "description": item.get("description") or [],
                    "created_time": item.get("created_time"),
                    "category": category,
                    "vendor": self.name,
                })
        return results

    async def submit_song(
        self,
        *,
        prompt: Optional[str] = None,
        style: Optional[str] = None,
        lyrics: Optional[str] = None,
        prebuilt_lyrics: Optional[dict] = None,
        voice_id: Optional[str] = None,
        gender: str = "any",
        mode: str = "sing",
        bpm: Optional[int] = None,
        key_scale: Optional[str] = None,
        time_signature: Optional[str] = None,
        audio_format: str = "mp3",
        expect_duration: Optional[int] = None,
        language: str = "zh",
    ) -> str:
        payload: dict[str, Any] = {
            "model": self.model_name,
            "mode": mode,
            "audio_settings": {"format": audio_format},
            "vocal_settings": {"gender": gender},
            "cover_settings": {"generate": False, "ratio": "1:1"},
        }
        if prompt:
            payload["prompt"] = prompt
        if style:
            payload["style"] = style
        if prebuilt_lyrics:
            payload["prebuilt_lyrics"] = prebuilt_lyrics
        elif lyrics:
            payload["lyrics"] = lyrics
        if voice_id:
            payload["vocal_settings"]["voice_id"] = voice_id
        music_settings: dict[str, Any] = {}
        if bpm is not None:
            music_settings["bpm"] = bpm
        if key_scale is not None:
            payload["music_settings"] = {**music_settings, "key": key_scale}
        if time_signature is not None:
            payload["music_settings"] = {**payload.get("music_settings", {}), "time_signature": time_signature}
        if music_settings and "music_settings" not in payload:
            payload["music_settings"] = music_settings
        if expect_duration is not None:
            payload["audio_settings"]["duration"] = expect_duration
            payload["lyrics_settings"] = {"language": language, "expect_duration": expect_duration}
        else:
            payload["lyrics_settings"] = {"language": language}
        data = await self._request("POST", "/v2/music/song/create", json=payload)
        task_id = data.get("task_id")
        if not task_id:
            raise MusicVendorError("商汤未返回 task_id")
        return task_id

    async def poll_song(self, task_id: str) -> dict:
        data = await self._request("GET", f"/v1/music/song/pending/{task_id}")
        raw = data.get("status")
        if raw == "PENDING":
            status = "PENDING"
        elif raw == "SUCCESS":
            status = "SUCCESS"
        elif raw == "FAILED":
            status = "FAILED"
        else:
            raise MusicVendorError(f"商汤返回未知 status: {raw}")
        songs: list[dict] = []
        if status == "SUCCESS":
            for item in (data.get("response") or {}).get("data") or []:
                songs.append({
                    "id": item.get("id"),
                    "title": item.get("title"),
                    "audio_url": item.get("audio_url"),
                    "cover_url": item.get("cover_url"),
                    "lyrics": item.get("lyrics"),
                    "duration_ms": int(item.get("duration") or 0) * 1000,
                    "derived_audio": item.get("derived_audio") or {},
                })
        return {"task_id": data.get("task_id") or task_id, "status": status, "fail_reason": data.get("fail_reason"), "songs": songs}

    async def draft_lyrics(
        self,
        *,
        prompt: str,
        style: Optional[str] = None,
        rhyme: Optional[str] = None,
        language: str = "zh",
        expect_duration: Optional[int] = None,
        additional_prompt: Optional[str] = None,
    ) -> list[dict]:
        payload: dict[str, Any] = {"model": self.model_name, "prompt": prompt, "language": language, "enhance_theme": True}
        if style: payload["style"] = style
        if rhyme: payload["rhyme"] = rhyme
        if expect_duration is not None: payload["expect_duration"] = expect_duration
        if additional_prompt: payload["additional_prompt"] = additional_prompt
        data = await self._request("POST", "/v1/music/lyrics/create", json=payload)
        return data.get("data") or []

    async def revise_lyrics(self, *, original_lyrics: str, instruction: str) -> list[dict]:
        # 商汤文档: POST /v1/music/lyrics/revise 必填 {model, lyrics, feedback}
        # 注意字段名是 feedback 而不是 instruction（写 instruction 会 400 {"code":"invalid"}）
        # 对外 API 仍沿用 instruction 语义，此处映射到上游 feedback
        payload = {"model": self.model_name, "lyrics": original_lyrics, "feedback": instruction}
        data = await self._request("POST", "/v1/music/lyrics/revise", json=payload)
        return data.get("data") or []

    # ════════════════════════════════════════════════════════════
    # VIDEO
    # ════════════════════════════════════════════════════════════

    async def create_video(
        self,
        *,
        content: list[dict],
        resolution: str = "720p",
        ratio: str = "16:9",
        duration: int = 15,
        watermark: bool = False,
        provider_specific: Optional[dict] = None,
    ) -> str:
        # 把 content 里的 image url 都转成 data URL (解决 OSS 签名过期问题)
        # 同时清理: text 元素不应带 url 字段, 空 url 删掉
        normalized_content: list[dict] = []
        for item in content:
            new_item = dict(item)
            item_type = new_item.get("type")
            if item_type == "text":
                # text 元素: 只要 text 字段, 删 url/audio_url/video_url
                new_item.pop("url", None)
                new_item.pop("audio_url", None)
                new_item.pop("video_url", None)
                new_item.pop("role", None)
            elif item_type == "image":
                if new_item.get("url"):
                    new_item["url"] = await _prepare_reference(new_item["url"])
            normalized_content.append(new_item)

        # doubao-seedance 不识别 num_frames/frame_rate 字段(senseaudio 严格 schema)
        # 不发送这些字段, 只发 doubao-seedance 认识的字段
        payload: dict[str, Any] = {
            "model": self.model_name,
            "content": normalized_content,
            "resolution": resolution,
            "ratio": ratio,
            "duration": duration,
            "watermark": watermark,
        }
        if provider_specific:
            payload["provider_specific"] = provider_specific
        data = await self._request("POST", "/v1/video/create", json=payload)
        task_id = data.get("task_id")
        if not task_id:
            raise MusicVendorError("商汤未返回 video task_id")
        return task_id

    async def poll_video(self, task_id: str) -> dict:
        data = await self._request("GET", "/v1/video/status", params={"id": task_id})
        raw = data.get("status")
        return {
            "task_id": data.get("task_id") or task_id,
            "status": raw,
            "video_url": data.get("video_url"),
            "duration": data.get("duration"),
            "fail_reason": data.get("error_message"),
        }

    # ════════════════════════════════════════════════════════════
    # 未实现能力 (NotImplementedError 占位)
    # ════════════════════════════════════════════════════════════

    async def synthesize_tts(self, **kwargs) -> bytes:
        """商汤 TTS 合成（POST /v1/t2a_v2），2026-10-03 实测可用。

        kwargs: text, voice_id（商汤系统音色 ID，如 female_0006_a）,
                response_format="mp3", speed=1.0（可选 0.5-2）
        响应 data.audio 为 hex 编码音频，解码后返回二进制。
        """
        route = self._route()
        if route != "tts":
            raise MusicVendorError(f"model_name={self.model_name} 不是 TTS 类模型")
        text = str(kwargs.get("text") or "").strip()
        voice_id = str(kwargs.get("voice_id") or "").strip()
        if not text:
            raise MusicVendorError("text 必填")
        if not voice_id:
            raise MusicVendorError("voice_id 必填（商汤系统音色 ID，如 female_0006_a）")
        payload: dict[str, Any] = {
            "model": self.model_name,
            "text": text,
            "voice_setting": {"voice_id": voice_id},
            "audio_setting": {"format": kwargs.get("response_format") or "mp3"},
        }
        speed = kwargs.get("speed")
        try:
            if speed and abs(float(speed) - 1.0) > 1e-6:
                payload["voice_setting"]["speed"] = max(0.5, min(2.0, float(speed)))
        except (TypeError, ValueError):
            pass
        data = await self._request("POST", "/v1/t2a_v2", json=payload)
        base_resp = data.get("base_resp") or {}
        if base_resp.get("status_code") not in (0, None):
            raise MusicVendorError(f"商汤 TTS 失败: {base_resp.get('status_msg') or data}")
        audio_hex = (data.get("data") or {}).get("audio")
        if not audio_hex:
            raise MusicVendorError(f"商汤 TTS 未返回音频: {str(data)[:200]}")
        try:
            return bytes.fromhex(audio_hex)
        except ValueError as exc:
            raise MusicVendorError("商汤 TTS 音频解码失败（data.audio 非 hex）") from exc

    async def transcribe(self, **_kwargs):
        raise NotImplementedError(
            "senseaudio transcribe 待实现: POST /v1/audio/transcriptions"
        )

    async def isolate_vocals(self, **_kwargs):
        raise NotImplementedError(
            "senseaudio isolate_vocals 待实现: POST /v1/isolation/sync"
        )

    async def start_translation(self, **_kwargs):
        raise NotImplementedError(
            "senseaudio start_translation 待实现: WS /ws/v1/audio/simulat-interpreting"
        )

    async def generate_sfx(self, **_kwargs):
        raise NotImplementedError(
            "senseaudio generate_sfx 待实现: POST /v1/sound-effects/generations"
        )

    async def start_realtime(self, **_kwargs):
        raise NotImplementedError(
            "senseaudio start_realtime 待实现: POST /v1/realtime/invoke"
        )

    async def generate_audio(self, **_kwargs):
        raise NotImplementedError(
            "senseaudio generate_audio (数字人 A1) 待实现: POST /v1/audio/generate"
        )
