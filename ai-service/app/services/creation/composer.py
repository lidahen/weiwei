"""合成引擎：逐镜 TTS/生图/生视频（复用既有任务表与 vendor 分发）→ FFmpeg 合成成片。

时间轴以"音频时长"为锚（对应 hypit 的以词为锚思路的镜级简化版）：
镜长 = max(音频时长+0.3s 尾气口, 视频时长, 2.5s)；视频偏短末帧定格补齐，偏长截断。
"""
from __future__ import annotations

import asyncio
import difflib
import json
import logging
import re
import shutil
from pathlib import Path
from typing import List, Optional, Tuple

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.ai_creation_project import AiCreationProject
from app.models.ai_image_task import AiImageTask
from app.models.ai_persona import AiPersona
from app.models.ai_storyboard_shot import AiStoryboardShot
from app.services.agnes_image import create_image_task_async, resolve_size
from app.services.agnes_video import create_video_task_async, query_video_status
from app.services.creation.media_store import (
    CreationError, assert_public_http_url, extract_frame, is_local_media_url,
    probe_duration_async, upload_file, work_dir,
)
from app.services.creation import video_ingest
from app.services.model_resolver import ModelConfigError, resolve_creation_candidates, resolve_model
from app.services.voice_clone_service import synthesize_with_voice

logger = logging.getLogger(__name__)

# 计费上下文：(tenant_id, user_id)，渲染/重试入口从请求头捕获后透传进后台任务
Billing = Tuple[int, str]


async def _bill(db: AsyncSession, billing: Billing, feature: str, *,
                model_name: str = "", task_type: str = "text") -> None:
    """配额检查 + 用量明细落账（对齐 assist/chat 惯例：计数先于上游调用）。"""
    from app.services.quota_service import check_and_count, log_usage

    tenant_id, user_id = billing
    await check_and_count(db, tenant_id)
    await log_usage(tenant_id, user_id=user_id, feature=feature,
                    model_name=model_name, task_type=task_type)


async def _ffmpeg(args: list, *, timeout: int = 900, cwd: Optional[Path] = None) -> None:
    """线程池执行 ffmpeg（subprocess 同步阻塞，禁止直接跑在事件循环里）。"""
    from app.services.creation.media_store import run_ffmpeg

    await asyncio.to_thread(run_ffmpeg, args, timeout=timeout, cwd=cwd)


async def render_project(project_id: int, tenant_id: int = 0, user_id: str = "",
                         image_model_id: Optional[int] = None,
                         video_model_id: Optional[int] = None) -> None:
    """后台渲染入口：失败落项目 failed 态，单镜错误保留其余镜产物。

    image_model_id/video_model_id：渲染时指定的生图/生视频模型（Agnes 限流或不可用时
    切换商汤聚合模型），落库到 storyboard.meta 供单镜重试沿用。
    """
    from app.database import async_session

    wdir = work_dir(f"project_{project_id}")
    async with async_session() as db:
        project = (await db.execute(select(AiCreationProject).where(
            AiCreationProject.project_id == project_id
        ))).scalar_one_or_none()
        if project is None:
            return
        try:
            if image_model_id or video_model_id:
                shell = json.loads(project.storyboard_json or "{}") or {}
                meta = shell.setdefault("meta", {})
                if image_model_id:
                    meta["image_model_id"] = image_model_id
                if video_model_id:
                    meta["video_model_id"] = video_model_id
                project.storyboard_json = json.dumps(shell, ensure_ascii=False)
            await _render(project, db, wdir, (tenant_id, user_id),
                          image_model_id, video_model_id)
        except ModelConfigError as exc:
            # 所选渲染模型不可用：未产生任何 vendor 消耗，直接落失败并给出明确原因
            logger.error("渲染模型不可用 project=%s: %s", project_id, exc)
            await db.rollback()
            project.error_msg = f"渲染模型不可用: {exc}"[:2000]
            project.status = "failed"
            await db.commit()
        except Exception as exc:  # noqa: BLE001
            logger.error("渲染失败 project=%s: %s", project_id, exc, exc_info=True)
            await db.rollback()
            project.error_msg = str(exc)[:2000]
            project.status = "failed"
            await db.commit()


# ════════════════════════════════════════════════════════════
# 逐镜生成
# ════════════════════════════════════════════════════════════

async def _sensenova_tts(db: AsyncSession, text: str, voice_id: str = "") -> bytes:
    """商汤 TTS（voice_id 空 → 创作默认系统音色），走商汤 key 额度。"""
    from app.services.vendor_router import get_vendor

    vendor = await get_vendor(db, "TTS")
    if vendor is None:
        raise CreationError("未配置 TTS 模型（模型管理中添加 TTS 类型并设默认）")
    return await vendor.synthesize_tts(
        text=text, voice_id=voice_id or settings.creation_default_voice)


# 镜间一致性锚定：生图有参考图（人设定妆照）时追加到 prompt 末尾，
# 压制每镜独立生成导致的人物外貌/服装漂移
_CONSISTENCY_ANCHOR = "。画面中人物的外貌、发型、服装必须与参考图保持完全一致"


async def _ensure_audio(shot, persona: Optional[AiPersona], db: AsyncSession, wdir: Path,
                        billing: Billing = (0, "")) -> None:
    if shot.audio_url and shot.audio_duration_ms:
        return
    text = (shot.text or "").strip()
    if not text:
        raise CreationError(f"镜 {shot.shot_index} 缺少台词，无法配音")
    voice_id = (persona.voice_id or "").strip() if persona else ""
    await _bill(db, billing, "creation-tts", task_type="audio")
    try:
        if voice_id.startswith("speech:"):
            try:
                audio = await synthesize_with_voice(text=text, voice_id=voice_id,
                                                    response_format="mp3")
            except Exception as exc:  # noqa: BLE001 —— 克隆链路不可用（余额/模型禁用/key）自动回落商汤
                logger.warning("镜 %s 硅基流动克隆 TTS 失败（%s），回落商汤默认音色 %s",
                               shot.shot_index, str(exc)[:80], settings.creation_default_voice)
                audio = await _sensenova_tts(db, text)
        else:
            # 商汤 TTS：voice_id 为空 → 创作默认系统音色；否则视为商汤音色 ID（如 female_0006_a）
            audio = await _sensenova_tts(db, text, voice_id)
    except Exception as exc:  # noqa: BLE001
        raise CreationError(f"镜 {shot.shot_index} 配音失败: {exc}") from exc
    path = wdir / f"shot_{shot.shot_index:02d}.mp3"
    path.write_bytes(audio)
    duration_ms = int(await probe_duration_async(path) * 1000)
    shot.audio_url = await upload_file(path, prefix="ai-creation/audio")
    shot.audio_duration_ms = duration_ms
    await db.commit()


async def _ensure_image(shot, persona: Optional[AiPersona], db: AsyncSession,
                        billing: Billing = (0, ""), model=None) -> None:
    if shot.image_url:
        return
    # 孤儿任务恢复：上次执行可能已在 vendor 侧成功但结果未落库（如跨会话快照问题），
    # 先按 image_task_id 采纳已完成任务，避免重复计费
    if shot.image_task_id:
        prev = (await db.execute(select(AiImageTask).where(
            AiImageTask.task_id == shot.image_task_id))).scalar_one_or_none()
        if prev is not None and prev.status == "completed" and prev.result_url:
            shot.image_url = prev.result_url
            await db.commit()
            return
    refs = json.loads(shot.reference_images or "[]")
    if persona and persona.look_image_urls:
        try:
            refs = json.loads(persona.look_image_urls) + refs
        except json.JSONDecodeError:
            pass
    # 模型候选链：显式指定（meta 沿用/前端选择）只用该模型；否则
    # 创作首选（Seedream 等高质量档）→ 默认 → 其余启用模型，失败自动降级
    if model is not None:
        candidates = [model]
    else:
        candidates = await resolve_creation_candidates(db, "IMAGE")
        if not candidates:
            raise CreationError("未配置 IMAGE 类型模型（模型管理中添加）")
    # 分镜图为竖屏成片主体，按配置档位出图（2K 起步）；vendor 侧会按模型
    # sizes 白名单就近归一（senseaudio/SenseNova 路径内置），无需在此适配
    size = resolve_size(settings.creation_image_size_tier, "9:16")
    # 计费一次（计数先于上游调用），候选链降级不重复计费
    await _bill(db, billing, "creation-image",
                model_name=getattr(candidates[0], "model_name", ""), task_type="image")
    prompt = (shot.image_prompt or shot.text or "").strip()
    if refs and _CONSISTENCY_ANCHOR not in prompt:
        prompt += _CONSISTENCY_ANCHOR

    last_error = ""
    for idx, cand in enumerate(candidates):
        task = AiImageTask(
            model_id=cand.model_id,
            prompt=prompt,
            gen_type="img2img" if refs else "text2img",
            input_image=refs[0] if refs else None,
            size=size,
            width=int(size.split("x")[0]),
            height=int(size.split("x")[1]),
            response_format="url",
            status="queued",
            create_by="creation",
        )
        db.add(task)
        await db.commit()
        await db.refresh(task)
        # task_id 先行落库：无论后续结果读取是否异常，重试都能找回该任务并采纳成果
        shot.image_task_id = task.task_id
        await db.commit()

        await create_image_task_async(task.task_id)
        # create_image_task_async 在独立会话里执行并提交终态（重试可达百秒级）；
        # 本会话的 REPEATABLE READ 快照停在下方首次 refresh，直接再 refresh 读到的是
        # 旧值（status 停留 queued），必须先 rollback 结束本事务再读
        await db.rollback()
        await db.refresh(task)
        if task.status == "completed" and task.result_url:
            shot.image_url = task.result_url
            await db.commit()
            return
        last_error = task.error_msg or task.status or ""
        # 内容审核被拒/服务不可用等：降级到候选链下一个模型（换厂商常可绕开单点故障）
        logger.warning("镜 %s 生图失败（model=%s）：%s，%s",
                       shot.shot_index, cand.model_name, last_error,
                       "尝试下一个候选模型" if idx < len(candidates) - 1 else "无更多候选")
    raise CreationError(f"镜 {shot.shot_index} 生图失败: {last_error}")


async def _ensure_video(shot, persona: Optional[AiPersona], db: AsyncSession,
                        billing: Billing = (0, ""), model=None) -> None:
    if shot.clip_url:
        return
    if not shot.image_url:
        raise CreationError(f"镜 {shot.shot_index} 缺少分镜图，请先重试生图阶段")
    if is_local_media_url(shot.image_url):
        raise CreationError(
            "OSS 未配置时生视频 vendor 无法读取本地分镜图，请配置 OSS 后重试")

    audio_sec = (shot.audio_duration_ms or 6000) / 1000.0
    seconds = str(max(4, min(12, int(audio_sec + 0.5) or 5)))

    # 模型候选链：显式指定只用该模型；否则创作首选（Seedance 等高质量档）
    # → 默认 → 其余启用模型。Seedance 内容审核被拒 / Agnes 服务不可用等都降级重试
    if model is not None:
        candidates = [model]
    else:
        candidates = await resolve_creation_candidates(db, "VIDEO")
        if not candidates:
            raise CreationError("未配置 VIDEO 类型模型（模型管理中添加）")
    # 计费口径：复用非终态任务不重复计费（创建时已计）；候选链降级新建任务也不二次计费
    billed = False

    from app.models.ai_video_task import AiVideoTask

    last_error = ""
    for idx, cand in enumerate(candidates):
        # 之前轮询超时/中断遗留的任务：已完成的直接采纳结果（不重复计费），
        # 非终态的续轮询；失败任务换下一个候选模型重建
        task: Optional[AiVideoTask] = None
        if shot.video_task_id:
            existing = (await db.execute(select(AiVideoTask).where(
                AiVideoTask.task_id == shot.video_task_id
            ))).scalar_one_or_none()
            if existing is not None:
                if existing.status == "completed" and existing.result_url:
                    shot.clip_url = existing.result_url
                    await db.commit()
                    return
                if existing.status not in ("failed", "completed"):
                    task = existing
                    await db.refresh(task)
        if task is None:
            if not billed:
                await _bill(db, billing, "creation-video",
                            model_name=getattr(cand, "model_name", ""), task_type="video")
                billed = True
            task = AiVideoTask(
                model_id=cand.model_id,
                prompt=shot.video_prompt or shot.image_prompt or shot.text,
                gen_type="img2vid",
                input_images=json.dumps([shot.image_url], ensure_ascii=False),
                duration=int(seconds),
                width=720,
                height=1280,
                status="queued",
                create_by="creation",
            )
            db.add(task)
            await db.commit()
            await db.refresh(task)
            shot.video_task_id = task.task_id
            await db.commit()
            await create_video_task_async(task.task_id)
        # 复用的非终态任务已提交过 vendor，直接进入轮询

        # 轮询至完成（query_video_status 内部调 vendor 查询并更新行）
        waited = 0
        failed_or_timeout = False
        while True:
            await asyncio.sleep(settings.creation_video_poll_interval)
            waited += settings.creation_video_poll_interval
            await db.refresh(task)
            if task.status == "completed":
                break
            if task.status == "failed":
                failed_or_timeout = True
                last_error = task.error_msg or "未知错误"
                break
            if waited >= settings.creation_video_poll_timeout:
                failed_or_timeout = True
                last_error = f"生视频超时（{waited}s）"
                break
            try:
                task = await query_video_status(task, db)
            except ValueError as exc:  # noqa: PERF203 —— vendor 侧任务缺失等，包成可展示的业务错误
                failed_or_timeout = True
                last_error = f"生视频任务异常: {exc}"
                break
            await db.commit()

        if not failed_or_timeout:
            if not task.result_url:
                raise CreationError(f"镜 {shot.shot_index} 生视频完成但无产物 URL")
            shot.clip_url = task.result_url
            await db.commit()
            return
        logger.warning("镜 %s 生视频失败（model=%s）：%s，%s",
                       shot.shot_index, cand.model_name, last_error,
                       "尝试下一个候选模型" if idx < len(candidates) - 1 else "无更多候选")
    raise CreationError(f"镜 {shot.shot_index} 生视频失败: {last_error}")


async def retry_shot_stage(project_id: int, shot_id: int, stage: str,
                           tenant_id: int = 0, user_id: str = "") -> None:
    """镜级重试：清空指定阶段产物后重跑该镜（image→video 依赖链自动级联清理）。

    不改 project.status：重试只修复单镜，项目级渲染状态保持原值，
    否则重试后项目永远卡在 generating，无法再次发起整片渲染。
    """
    from app.database import async_session

    async with async_session() as db:
        shot = (await db.execute(select(AiStoryboardShot).where(
            AiStoryboardShot.shot_id == shot_id, AiStoryboardShot.project_id == project_id
        ))).scalar_one_or_none()
        if shot is None:
            raise CreationError("分镜不存在")
        if stage == "audio":
            shot.audio_url = None
            shot.audio_duration_ms = None
        elif stage == "image":
            shot.image_url = None
            shot.image_task_id = None
            shot.clip_url = None
            shot.video_task_id = None
        elif stage == "video":
            shot.clip_url = None
            shot.video_task_id = None
        shot.shot_status = "pending"
        shot.error_msg = None
        await db.commit()

    wdir = work_dir(f"project_{project_id}")
    billing: Billing = (tenant_id, user_id)
    async with async_session() as db:
        project = (await db.execute(select(AiCreationProject).where(
            AiCreationProject.project_id == project_id
        ))).scalar_one_or_none()
        shot = (await db.execute(select(AiStoryboardShot).where(
            AiStoryboardShot.shot_id == shot_id
        ))).scalar_one_or_none()
        persona = None
        if project and project.persona_id:
            persona = (await db.execute(select(AiPersona).where(
                AiPersona.persona_id == project.persona_id
            ))).scalar_one_or_none()
        if project is None or shot is None:
            raise CreationError("项目或分镜不存在")
        # 沿用渲染时选择的模型（storyboard.meta 覆盖），单镜重试与整片渲染口径一致
        image_model = video_model = None
        try:
            meta = (json.loads(project.storyboard_json or "{}").get("meta") or {})
            if meta.get("image_model_id"):
                image_model = await resolve_model(db, "IMAGE", meta["image_model_id"])
            if meta.get("video_model_id"):
                video_model = await resolve_model(db, "VIDEO", meta["video_model_id"])
        except ModelConfigError as exc:
            raise CreationError(f"渲染模型不可用: {exc}") from exc
        try:
            if stage == "audio":
                await _ensure_audio(shot, persona, db, wdir, billing)
            elif stage == "image":
                await _ensure_audio(shot, persona, db, wdir, billing)
                await _ensure_image(shot, persona, db, billing, image_model)
            else:
                await _ensure_audio(shot, persona, db, wdir, billing)
                await _ensure_image(shot, persona, db, billing, image_model)
                await _ensure_video(shot, persona, db, billing, video_model)
            shot.shot_status = "ready"
            await db.commit()
        except Exception as exc:  # noqa: BLE001
            await db.rollback()
            shot.error_msg = str(exc)[:1000]
            shot.shot_status = "failed"
            await db.commit()
            raise


# ════════════════════════════════════════════════════════════
# 合成
# ════════════════════════════════════════════════════════════

async def _prepare_shot(persona_id: Optional[int], shot_id: int, wdir: Path,
                        billing: Billing, image_model=None, video_model=None) -> None:
    """单镜三阶段生成（独立会话：并行执行时 AsyncSession 不可跨协程共享）。

    image_model/video_model：渲染入口解析好的模型行（None 则 _ensure_* 内部走默认）。
    """
    from app.database import async_session

    async with async_session() as db:
        shot = (await db.execute(select(AiStoryboardShot).where(
            AiStoryboardShot.shot_id == shot_id
        ))).scalar_one_or_none()
        if shot is None:
            raise CreationError(f"分镜 {shot_id} 不存在")
        persona = None
        if persona_id:
            persona = (await db.execute(select(AiPersona).where(
                AiPersona.persona_id == persona_id, AiPersona.del_flag == 0
            ))).scalar_one_or_none()
        try:
            await _ensure_audio(shot, persona, db, wdir, billing)
            await _ensure_image(shot, persona, db, billing, image_model)
            await _ensure_video(shot, persona, db, billing, video_model)
            shot.shot_status = "ready"
            shot.error_msg = None  # 清掉历史失败的提示，避免成功镜上残留旧错误
            await db.commit()
        except Exception as exc:  # noqa: BLE001 —— 镜级失败隔离：落错误态，不拖垮其余镜
            await db.rollback()
            shot.error_msg = str(exc)[:1000]
            shot.shot_status = "failed"
            await db.commit()
            raise CreationError(f"镜 {shot.shot_index}: {shot.error_msg}") from exc


async def _prepare_all_shots(persona_id: Optional[int], shot_ids: List[int], wdir: Path,
                             billing: Billing,
                             image_model=None, video_model=None) -> List[str]:
    """并行生成各镜产物（信号量限流），返回失败镜描述列表。"""
    sem = asyncio.Semaphore(max(1, settings.creation_shot_concurrency))

    async def _bounded(shot_id: int) -> None:
        async with sem:
            await _prepare_shot(persona_id, shot_id, wdir, billing, image_model, video_model)

    results = await asyncio.gather(*[_bounded(sid) for sid in shot_ids],
                                   return_exceptions=True)
    return [str(r) for r in results if isinstance(r, BaseException)]


async def _render(project: AiCreationProject, db: AsyncSession, wdir: Path,
                  billing: Billing,
                  image_model_id: Optional[int] = None,
                  video_model_id: Optional[int] = None) -> None:
    shots = (await db.execute(select(AiStoryboardShot).where(
        AiStoryboardShot.project_id == project.project_id, AiStoryboardShot.del_flag == 0
    ).order_by(AiStoryboardShot.shot_index))).scalars().all()
    if not shots:
        raise CreationError("项目无分镜，无法渲染")

    # 渲染模型：请求级覆盖 → 上次渲染保存的 meta 覆盖 → 模型管理默认；解析失败快速失败不进计费
    try:
        meta = (json.loads(project.storyboard_json or "{}").get("meta") or {})
        effective_image_id = image_model_id or meta.get("image_model_id")
        effective_video_id = video_model_id or meta.get("video_model_id")
        image_model = (await resolve_model(db, "IMAGE", effective_image_id)
                       if effective_image_id else await resolve_model(db, "IMAGE"))
        video_model = (await resolve_model(db, "VIDEO", effective_video_id)
                       if effective_video_id else await resolve_model(db, "VIDEO"))
    except ModelConfigError as exc:
        raise CreationError(f"渲染模型不可用: {exc}") from exc
    if image_model is None:
        raise CreationError("未配置 IMAGE 类型模型（模型管理中添加）")
    if video_model is None:
        raise CreationError("未配置 VIDEO 类型模型（模型管理中添加）")
    logger.info("渲染模型 image=%s video=%s", image_model.model_name, video_model.model_name)

    project.status = "generating"
    project.error_msg = None
    await db.commit()

    # 1. 逐镜产物齐备（并行度 creation_shot_concurrency，各镜独立会话隔离失败）
    failures = await _prepare_all_shots(project.persona_id,
                                        [s.shot_id for s in shots], wdir, billing,
                                        image_model, video_model)
    if failures:
        raise CreationError("；".join(failures[:3]) +
                            (f"（共 {len(failures)} 镜失败，可单镜重试）" if len(failures) > 3 else ""))

    # 并行阶段各镜在独立会话提交，重新拉取最新产物状态
    shots = (await db.execute(select(AiStoryboardShot).where(
        AiStoryboardShot.project_id == project.project_id, AiStoryboardShot.del_flag == 0
    ).order_by(AiStoryboardShot.shot_index))).scalars().all()
    missing = [s.shot_index for s in shots if not s.clip_url]
    if missing:
        raise CreationError(f"镜 {missing} 缺少视频产物，无法合成")

    project.status = "composing"
    await db.commit()

    # 2. 时间轴 + 字幕
    timeline = []
    for shot in shots:
        audio_dur = (shot.audio_duration_ms or 5000) / 1000.0
        dur = max(audio_dur + 0.3, 2.5)
        timeline.append({"shot": shot, "duration": round(dur, 2)})
    total = sum(t["duration"] for t in timeline)

    # 字幕：优先 WhisperX 词级对齐 → ASS 卡拉OK（"锚定到词"的落点）；未安装/失败回退句级 SRT
    subs_name, is_ass = await _build_subtitle_file(timeline, wdir, project)

    # 3. 逐镜归一化（720x1280@30fps，无声；短则定格补齐，长则截断）
    seg_paths: List[Path] = []
    for i, t in enumerate(timeline):
        clip_path = await _fetch_media(t["shot"].clip_url, wdir / f"clip_{i:02d}.mp4")
        clip_dur = await probe_duration_async(clip_path)
        pad = max(0.0, t["duration"] - clip_dur)
        seg = wdir / f"seg_{i:02d}.mp4"
        vf = ("scale=720:1280:force_original_aspect_ratio=increase,"
              "crop=720:1280,setsar=1,fps=30")
        if pad > 0.05:
            vf += f",tpad=stop_mode=clone:stop_duration={pad:.2f}"
        await _ffmpeg(["-i", str(clip_path), "-vf", vf, "-t", f"{t['duration']:.2f}",
                       "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                       "-pix_fmt", "yuv420p", str(seg)], timeout=600)
        seg_paths.append(seg)

    # 4. 拼接 + 音轨 + 字幕 + 封面
    concat_list = wdir / "concat.txt"
    # concat demuxer 把清单内相对条目按「清单文件所在目录」解析，
    # 写纯文件名并配合 cwd=wdir，避免相对工作目录被二次拼接
    concat_list.write_text("".join(f"file '{p.name}'\n" for p in seg_paths), encoding="utf-8")
    concat_mp4 = wdir / "concat.mp4"
    await _ffmpeg(["-f", "concat", "-safe", "0", "-i", "concat.txt",
                   "-c", "copy", str(concat_mp4)], timeout=600, cwd=wdir)

    voice_files: List[Path] = []
    for i, t in enumerate(timeline):
        va = wdir / f"va_{i:02d}.wav"
        # 按 shot_index 定位音频（编辑器删镜后 index 有空洞，与 enumerate 位置会错位）；
        # 本地缺失时 _fetch_media 会从 audio_url（OSS）拉回，容器重启后也能合成
        if not t["shot"].audio_url:
            raise CreationError(f"镜 {t['shot'].shot_index} 缺少配音音频")
        va_src = await _fetch_media(t["shot"].audio_url,
                                    wdir / f"shot_{t['shot'].shot_index:02d}.mp3")
        await _ffmpeg(["-i", str(va_src),
                       "-af", f"apad=whole_dur={t['duration']:.2f}",
                       "-ar", "44100", "-ac", "2", "-t", f"{t['duration']:.2f}", str(va)],
                      timeout=120)
        voice_files.append(va)

    voice_full = wdir / "voice_full.wav"
    vinputs: List[str] = []
    for vf in voice_files:
        vinputs += ["-i", str(vf)]
    await _ffmpeg([*vinputs, "-filter_complex",
                   f"concat=n={len(voice_files)}:v=0:a=1[aout]",
                   "-map", "[aout]", str(voice_full)], timeout=300)

    final_raw = wdir / "final_nosub.mp4"
    bgm_url = _bgm_url(project)
    if bgm_url:
        if not is_local_media_url(bgm_url):
            assert_public_http_url(bgm_url, what="BGM 链接")
        bgm_path = await _fetch_media(bgm_url, wdir / "bgm.mp3")
        await _ffmpeg(["-i", str(concat_mp4), "-i", str(voice_full), "-i", str(bgm_path),
                       "-filter_complex",
                       "[2:a]volume=0.15[b];[1:a][b]amix=inputs=2:duration=first:dropout_transition=0[aout]",
                       "-map", "0:v", "-map", "[aout]",
                       "-c:v", "copy", "-c:a", "aac", "-shortest", str(final_raw)], timeout=900)
    else:
        await _ffmpeg(["-i", str(concat_mp4), "-i", str(voice_full),
                       "-map", "0:v", "-map", "1:a",
                       "-c:v", "copy", "-c:a", "aac", "-shortest", str(final_raw)], timeout=900)

    final_mp4 = wdir / "final.mp4"
    # ASS（词级卡拉OK）自带样式不传 force_style（会被覆盖）；SRT 走 force_style 兜底样式。
    # cwd=wdir + 相对路径：Windows 盘符冒号在 filtergraph 里需转义，相对路径天然无冒号
    if is_ass:
        vf = f"subtitles={subs_name}"
    else:
        font = (settings.creation_subtitle_font or "").replace(":", "\\:")
        vf = (f"subtitles={subs_name}:force_style='FontName={font},Fontsize=14,"
              f"Bold=1,Outline=1,Shadow=0,MarginV=25'")
    await _ffmpeg(["-i", str(final_raw), "-vf", vf,
                   "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
                   "-c:a", "copy", str(final_mp4)], timeout=1800, cwd=wdir)

    cover_jpg = wdir / "cover.jpg"
    await asyncio.to_thread(extract_frame, final_mp4, min(0.5, total / 2), cover_jpg)

    # 5. 转储与落库
    project.final_video_url = await upload_file(final_mp4, prefix="ai-creation/final")
    project.cover_url = await upload_file(cover_jpg, prefix="ai-creation/cover")
    project.status = "done"
    await db.commit()
    logger.info("渲染完成 project=%s duration=%.1fs", project.project_id, total)


def _bgm_url(project: AiCreationProject) -> Optional[str]:
    """P0 支持 storyboard.audio.bgm.url 直链背景乐；Senseaudio 生成歌曲为 P1。"""
    try:
        sb = json.loads(project.storyboard_json or "{}")
        bgm = (sb.get("audio") or {}).get("bgm") or {}
        url = (bgm.get("url") or "").strip()
        return url or None
    except json.JSONDecodeError:
        return None


async def _fetch_media(url: str, target: Path) -> Path:
    """媒体产物拉取到本地（http 直链下载 / 本地媒体 URL 拷贝）。"""
    if target.exists():
        return target
    from app.services.creation.media_store import is_local_media_url, local_path_of

    if is_local_media_url(url):
        src = local_path_of(url)
        if not src.exists():
            raise CreationError(f"本地媒体缺失: {url}")
        await asyncio.to_thread(shutil.copyfile, src, target)
        return target
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(300), follow_redirects=True) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            await asyncio.to_thread(target.write_bytes, resp.content)
    except Exception as exc:  # noqa: BLE001
        raise CreationError(f"媒体下载失败: {url[:120]}") from exc
    return target


def _srt_ts(sec: float) -> str:
    ms = int(round(sec * 1000))
    h, rem = divmod(ms, 3600000)
    m, rem = divmod(rem, 60000)
    s, ms = divmod(rem, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


# ════════════════════════════════════════════════════════════
# 字幕：词级卡拉OK（WhisperX 对齐）→ 句级 SRT 回退
# ════════════════════════════════════════════════════════════

# 竖屏单行字幕的舒适字数上限（超出按词切行）
_ASS_MAX_LINE_CHARS = 14


def _ass_ts(sec: float) -> str:
    cs = max(0, int(round(sec * 100)))
    h, rem = divmod(cs, 360000)
    m, rem = divmod(rem, 6000)
    s, cs = divmod(rem, 100)
    return f"{h}:{m:02d}:{s:02d}.{cs:02d}"


def _ass_color(hex_color: str, default: str = "FFE24A") -> str:
    """#RRGGBB → ASS 的 &H00BBGGRR（BGR 序）。"""
    m = re.fullmatch(r"#?([0-9a-fA-F]{6})", (hex_color or "").strip())
    rrggbb = m.group(1).upper() if m else default
    return f"&H00{rrggbb[4:6]}{rrggbb[2:4]}{rrggbb[0:2]}"


async def _build_subtitle_file(timeline: List[dict], wdir: Path,
                               project: AiCreationProject) -> Tuple[str, bool]:
    """生成字幕文件。返回 (相对文件名, 是否 ASS)。

    词级路径：逐镜拉取 TTS 音频 → WhisperX 对齐出词时间戳 → ASS 卡拉OK（逐词高亮填充）。
    任一镜未启用/失败/ASR 与台词相似度过低（误听不可信）→ 整体回退句级 SRT，链路不断。
    """
    from app.services.creation import align

    def _srt_fallback(reason: str) -> Tuple[str, bool]:
        logger.info("字幕回退句级 SRT：%s", reason)
        (wdir / "subs.srt").write_text(_build_srt(timeline), encoding="utf-8")
        return "subs.srt", False

    events: List[dict] = []
    cursor = 0.0
    for t in timeline:
        shot = t["shot"]
        audio_local = wdir / f"shot_{shot.shot_index:02d}.mp3"
        if not audio_local.exists() and shot.audio_url:
            await _fetch_media(shot.audio_url, audio_local)
        if not audio_local.exists():
            return _srt_fallback(f"镜 {shot.shot_index} 音频缺失")
        words = await align.align_words(audio_local)
        if not words:
            return _srt_fallback(f"镜 {shot.shot_index} 词级对齐不可用")
        shot_norm = align.normalize_text(shot.text or "")
        asr_norm = align.normalize_text("".join(w["word"] for w in words))
        if shot_norm and asr_norm:
            ratio = difflib.SequenceMatcher(None, shot_norm, asr_norm).ratio()
            if ratio < 0.5:
                return _srt_fallback(
                    f"镜 {shot.shot_index} ASR 与台词相似度 {ratio:.2f} 过低")
        events.append({"start": cursor, "words": words})
        cursor += t["duration"]

    ass_path = wdir / "subs.ass"
    ass_path.write_text(_build_ass(events, project), encoding="utf-8")
    logger.info("词级卡拉OK字幕构建成功（%s 镜）", len(events))
    return "subs.ass", True


def _build_ass(events: List[dict], project: AiCreationProject) -> str:
    """词级卡拉OK ASS：{\\k 厘秒} 逐词填充——未读词为 SecondaryColour（白），已读渐变为
    PrimaryColour（高亮色，取自 storyboard.subtitles.highlight），即抖音常见的逐字变黄效果。"""
    try:
        sb = json.loads(project.storyboard_json or "{}")
        sub_cfg = sb.get("subtitles") or {}
    except json.JSONDecodeError:
        sub_cfg = {}
    highlight = _ass_color(str(sub_cfg.get("highlight") or "#FFE24A"))
    font = settings.creation_subtitle_font or "Noto Sans CJK SC"
    font_size = max(40, min(80, int(sub_cfg.get("font_size") or 56)))

    header = (
        "[Script Info]\n"
        "; 词级卡拉OK字幕（WhisperX 对齐驱动，hypit 词锚思路的镜内落地）\n"
        "ScriptType: v4.00+\n"
        "PlayResX: 720\n"
        "PlayResY: 1280\n"
        "WrapStyle: 2\n"
        "ScaledBorderAndShadow: yes\n\n"
        "[V4+ Styles]\n"
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, "
        "BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, "
        "BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
        f"Style: Douyin,{font},{font_size},{highlight},&H00FFFFFF,&H00000000,&H96000000,"
        "1,0,0,0,100,100,0,0,1,3,1,2,40,40,220,1\n\n"
        "[Events]\n"
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
    )

    lines: List[str] = []
    for ev in events:
        base = ev["start"]

        def _flush(line_words: List[dict], origin: float = base) -> None:
            if not line_words:
                return
            start = origin + line_words[0]["start"]
            end = origin + line_words[-1]["end"]
            parts: List[str] = []
            prev_end = line_words[0]["start"]
            for w in line_words:
                # 词间停顿并入前一个词的 \k 时长，保证进度条连续推进
                dur_cs = max(1, int(round((w["end"] - prev_end) * 100)))
                parts.append(f"{{\\k{dur_cs}}}{w['word']}")
                prev_end = w["end"]
            text = "".join(parts).replace("\n", " ")
            lines.append(f"Dialogue: 0,{_ass_ts(start)},{_ass_ts(end)},Douyin,,0,0,0,,{text}")

        line: List[dict] = []
        char_count = 0
        for w in ev["words"]:
            if line and char_count + len(w["word"]) > _ASS_MAX_LINE_CHARS:
                _flush(line)
                line, char_count = [], 0
            line.append(w)
            char_count += len(w["word"])
        _flush(line)
    return header + "\n".join(lines) + "\n"


def _build_srt(timeline: List[dict]) -> str:
    """句级字幕：镜内按字数比例分摊时间轴。"""
    entries: List[tuple] = []
    cursor = 0.0
    for t in timeline:
        shot = t["shot"]
        dur = t["duration"]
        sentences = video_ingest.split_sentences(shot.text or "")
        if not sentences:
            cursor += dur
            continue
        total_chars = sum(len(s) for s in sentences)
        start = cursor
        for s in sentences:
            span = dur * len(s) / total_chars
            entries.append((start, start + span, s))
            start += span
        cursor += dur
    return "".join(
        f"{i}\n{_srt_ts(a)} --> {_srt_ts(b)}\n{t}\n\n"
        for i, (a, b, t) in enumerate(entries, 1)
    )
