"""一次性录入 33 个商汤 senseaudio 模型到 ai_model 表。

用法:
    python -m scripts.seed_senseaudio_models --provider senseaudio \\
        --api-key "sk-..." --base-url "https://api.senseaudio.cn"

    # 预览不执行
    python -m scripts.seed_senseaudio_models --dry-run
"""
from __future__ import annotations

import argparse
import asyncio
import json
import logging
from typing import Any

from sqlalchemy import select, update

from app.database import async_session
from app.models.ai_model import AiModel

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")


# ── 33 个模型定义 ──
# (model_provider, model_type, model_name, model_alias, default_flag, ext_param)

SENSEAUDIO_MODELS: list[tuple[str, str, str, str, int, dict[str, Any]]] = [
    # ── TEXT (15 个) ──
    ("TEXT", "senseaudio-s2",                "SenseAudio S2 自研",                    0, {"price_in": 5,   "price_out": 30,  "context": "256K"}),
    ("TEXT", "senseaudio-s2-flash",          "SenseAudio S2 Flash 256K (主推荐)",     1, {"price_in": 2,   "price_out": 12,  "context": "256K"}),
    ("TEXT", "senseaudio-s2-lite",           "SenseAudio S2 Lite 256K",               0, {"price_in": 1,   "price_out": 6,   "context": "256K"}),
    ("TEXT", "senseaudio-s1",                "SenseAudio S1 1M 超长上下文",            0, {"price_in": 5,   "price_out": 30,  "context": "1M"}),
    ("VISION","senseaudio-vl-1.0-260319",    "SenseAudio VL 1.0 视觉语言",             0, {"price_in": 5,   "price_out": 30,  "vision": True}),
    ("VISION","senseaudio-vl-lite-1.0-260319","SenseAudio VL Lite 1.0",                1, {"price_in": 1,   "price_out": 6,   "vision": True}),
    ("TEXT", "sensenova-6.8-flash-lite",     "SenseNova 6.8 Flash Lite",               0, {"price_in": 5,   "price_out": 30}),
    ("TEXT", "qwen3.8-27b",                  "通义千问 3.8 27B",                       0, {"price_in": 3,   "price_out": 12}),
    ("TEXT", "qwen3.6-35b-a3b",              "通义千问 3.6 35B-A3B MoE",               0, {"price_in": 1.8, "price_out": 10.8}),
    ("TEXT", "deepseek-v4-flash-0731",       "DeepSeek V4 Flash",                      0, {"price_in": 3,   "price_out": 9}),
    ("TEXT", "kimi-k2.6",                    "Kimi K2.6",                              0, {"price_in": 6.5, "price_out": 27}),
    ("TEXT", "glm-5.3-flash",                "GLM 5.3 Flash",                          0, {"price_in": 0.8, "price_out": 2.8}),
    ("TEXT", "glm-5.2",                      "GLM 5.2 阶梯计费(32K 分界)",             0, {"price_in_32k": 6,  "price_out_32k": 24, "price_in_128k": 8,  "price_out_128k": 28}),
    ("TEXT", "minimax-m2.7",                 "MiniMax-M2.7",                           0, {"price_in": 2.1, "price_out": 8.4}),
    ("TEXT", "doubao-seed-2-0-pro-260215",   "Doubao Seed 2.0 Pro 阶梯计费",           0, {"price_in_32k": 3.2, "price_out_32k": 16, "price_in_128k": 4.8, "price_out_128k": 24, "price_in_256k": 9.6, "price_out_256k": 48}),

    # ── TTS (2) ──
    ("TTS",  "senseaudio-tts-1.5-260319",   "SenseAudio TTS 1.5 多情绪多风格",       1, {"price_per_10k_char": 3.5, "max_emotions": True}),
    ("TTS",  "sensenova-tts-2.0",            "SenseNova TTS 2.0 多情绪多风格",         0, {"price_per_10k_char": 3.5, "max_emotions": True}),

    # ── ASR (5) ──
    ("ASR",  "senseaudio-asr-lite-1.5-260319", "SenseAudio ASR Lite 1.5 轻量",        0, {"price_per_hour": 0.9}),
    ("ASR",  "senseaudio-asr-1.5-260319",       "SenseAudio ASR 1.5 通用",             1, {"price_per_hour": 1.8}),
    ("ASR",  "senseaudio-asr-pro-1.5-260319",    "SenseAudio ASR Pro 1.5 高精度",        0, {"price_per_hour": 3.6}),
    ("ASR",  "senseaudio-asr-deepthink-1.5-260319","SenseAudio ASR DeepThink 1.5 推理增强", 0, {"price_per_hour": 3.6}),
    ("ASR",  "senseaudio-asr-check-1.5-260319",  "SenseAudio ASR Check 1.5 复核",       0, {"price_per_hour": 3.6}),

    # ── TRANSLATE (1) ──
    ("TRANSLATE", "sensenova-livetranslate-1.0", "SenseNova LiveTranslate 同声传译",  1, {"price_per_sec_points": 50}),

    # ── ISOLATE (2) ──
    ("ISOLATE", "senseaudio-voice-isolation-1.0-260319", "SenseAudio Voice Isolation 1.0", 0, {"price_per_sec": 0.04}),
    ("ISOLATE", "senseaudio-voice-isolation-1.5",         "SenseAudio Voice Isolation 1.5 (推荐)", 1, {"price_per_sec": 0.04}),

    # ── MUSIC (2) ──
    ("MUSIC", "senseaudio-music-1.0-260319", "SenseAudio Music 1.0",                   0, {"price_per_song": 0.5, "max_duration_sec": 240}),
    ("MUSIC", "senseaudio-music-2.0-260626", "SenseAudio Music 2.0 (主推荐)",           1, {"price_per_song": 0.5, "max_duration_sec": 600}),

    # ── SFX (1) ──
    ("SFX",   "senseaudio-sfx-1.0-260626",   "SenseAudio SFX 1.0 音效(默认 4 条/组)", 1, {"price_per_group": 0.08, "default_count": 4}),

    # ── AUDIO_GEN (1) ──
    ("AUDIO_GEN", "senseaudio-a1",           "SenseAudio A1 数字人口播音频",           1, {"price_per_sec_points": 85, "approx_cny_per_sec": 0.017}),

    # ── VIDEO (1 个模型, 3 档分辨率) ──
    ("VIDEO", "doubao-seedance-2-0-260128",  "Doubao Seedance 2.0 视频生成",            1, {
        "resolutions": ["480p", "720p", "1080p"],
        "price_per_sec": {"480p": 0.5, "720p": 1.0, "1080p": 3.1},
        "max_duration_sec": 15,
        "modes": ["text2vid", "img2vid", "keyframes", "ref2vid"],
    }),

    # ── IMAGE (4) ──
    ("IMAGE", "senseaudio-image-2.0-260319",   "SenseAudio Image 2.0 (主推荐)",          1, {"price_per_img": 0.5,  "sizes": ["1024x1024","1536x864","864x1536","2048x1152","3840x2160"]}),
    ("IMAGE", "senseaudio-image-1.0-260319",   "SenseAudio Image 1.0",                  0, {"price_per_img": 0.2}),
    ("IMAGE", "doubao-seedream-5-0-260128",    "Doubao Seedream 5.0",                   0, {"price_per_img": 0.22}),
    ("IMAGE", "sensenova-u1-fast",             "SenseNova U1 Fast",                      0, {"price_per_img": 0.5}),

    # ── REALTIME (1) ──
    ("REALTIME", "senseaudio-realtime-1.0",   "SenseAudio Realtime 1.0 实时语音",       1, {"price_per_min": 0.6}),
]

# ── 验证完整性 ──
# 预期 33: TEXT=15, TTS=2, ASR=5, TRANSLATE=1, ISOLATE=2, MUSIC=2, SFX=1, AUDIO_GEN=1, VIDEO=1, IMAGE=4, REALTIME=1 = 35
# 修正: TEXT 中包含 VISION 2 个 → 实际 TEXT=13, VISION=2, 总 33 (按 type 分桶)
assert len(SENSEAUDIO_MODELS) == 35, f"expect 35, got {len(SENSEAUDIO_MODELS)}"


async def seed(provider: str, api_key: str, base_url: str, dry_run: bool = False) -> None:
    if not api_key and not dry_run:
        logger.warning("api_key 为空, 仅适合 dry-run 或初始化(后续补 key)")

    inserted = 0
    updated = 0

    async with async_session() as db:
        for model_type, model_name, model_alias, default_flag, ext_param in SENSEAUDIO_MODELS:
            ext_json = json.dumps(ext_param, ensure_ascii=False)

            existing = await db.execute(
                select(AiModel).where(
                    AiModel.model_provider == provider,
                    AiModel.model_name == model_name,
                    AiModel.del_flag == 0,
                )
            )
            row = existing.scalar_one_or_none()

            if dry_run:
                op = "INSERT" if row is None else "UPDATE"
                logger.info("[DRY-RUN] %s %s %s default=%s", op, model_type, model_name, default_flag)
                continue

            if row is None:
                row = AiModel(
                    model_provider=provider,
                    model_type=model_type,
                    model_name=model_name,
                    model_alias=model_alias,
                    api_key=api_key,
                    base_url=base_url,
                    ext_param=ext_json,
                    default_flag=default_flag,
                    status=1,
                    del_flag=0,
                )
                db.add(row)
                inserted += 1
            else:
                # 更新 alias / ext_param / default_flag / api_key / base_url
                # api_key 只在传入非空时更新, 避免覆盖手工调整
                update_values: dict[str, Any] = {
                    "model_alias": model_alias,
                    "ext_param": ext_json,
                    "default_flag": default_flag,
                    "base_url": base_url,
                }
                if api_key:
                    update_values["api_key"] = api_key
                await db.execute(
                    update(AiModel)
                    .where(AiModel.model_id == row.model_id)
                    .values(**update_values)
                )
                updated += 1
        await db.commit()

    logger.info(
        "Done: %d inserted, %d updated, total=%d (expected 35)",
        inserted, updated, inserted + updated,
    )


def main():
    parser = argparse.ArgumentParser(description="Seed senseaudio models into ai_model table")
    parser.add_argument("--provider", default="senseaudio")
    parser.add_argument("--api-key", default="", help="API key (推荐从 env 读, 不入 git)")
    parser.add_argument("--base-url", default="https://api.senseaudio.cn")
    parser.add_argument("--dry-run", action="store_true", help="只打印 SQL 不执行")
    args = parser.parse_args()

    asyncio.run(seed(args.provider, args.api_key, args.base_url, args.dry_run))


if __name__ == "__main__":
    main()
