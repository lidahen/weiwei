"""AI 创作工作台路由：拆解 / 项目（脚本+分镜+渲染）/ 人设 / 本地媒体服务。

【冻结区 — HOBBY】2026-10-15 起停止迭代（docs/ai/AI功能评估-多余与深化-2026-10-15.md）：
拆解/人设/分镜为通用短视频工具，与"社区二手交易"用户画像不匹配；收缩为
"卖货物料生成"（商品文案 /ai/assist/product-copy + 配图 /ai/assist/product-image），
本路由的通用创作台入口保留但不再迭代，仅在缺陷时最小修复。
"""
from __future__ import annotations

import json
import logging
import uuid
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, Depends, File, Request, UploadFile
from fastapi.responses import FileResponse, RedirectResponse
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.ai_creation_project import AiCreationProject
from app.models.ai_storyboard_shot import AiStoryboardShot
from app.models.ai_viral_template import AiViralTemplate
from app.schemas.common import ApiResponse
from app.schemas.creation import (
    DecomposeReq, HookVariantsReq, PersonaGenerateLookReq, PersonaLookSetReq,
    PersonaSaveReq, ProjectCreateReq, PublishPackageReq, RenderReq, ShotRetryReq,
    StoryboardUpdateReq,
)
from app.services.creation import composer, persona as persona_service, storyboard
from app.services.creation.media_store import (
    CreationError, assert_public_http_url, is_local_media_url, local_path_of, work_dir,
)
from app.services.quota_service import (
    QuotaExceededException, check_and_count, log_usage,
    tenant_id_from_request, user_id_from_request,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai/creation", tags=["AI创作工作台"])

# 上传视频硬上限（拆解用源片，500MB 足够 10 分钟 1080p）
MAX_UPLOAD_BYTES = 500 * 1024 * 1024


def _ok(data=None, msg: str = "success"):
    return ApiResponse(data=data, msg=msg)


def _fail(msg: str, code: int = 400):
    return ApiResponse(code=code, msg=msg)


def _quota_denied(e: QuotaExceededException) -> ApiResponse:
    return ApiResponse(code=429, msg=str(e))


# ════════════════════════════════════════════════════════════
# 爆款拆解
# ════════════════════════════════════════════════════════════

@router.post("/decompose")
async def decompose(body: DecomposeReq, request: Request, background: BackgroundTasks,
                    db: AsyncSession = Depends(get_db)):
    """提交爆款拆解任务（后台执行，轮询 GET /template/{id} 获取结果）。"""
    if not is_local_media_url(body.source_url):
        assert_public_http_url(body.source_url, what="拆解来源链接")
    try:
        await check_and_count(db, tenant_id_from_request(request))
    except QuotaExceededException as e:
        return _quota_denied(e)
    create_by = body.create_by or user_id_from_request(request) or None
    row = AiViralTemplate(source_url=body.source_url, status="analyzing",
                          create_by=create_by)
    db.add(row)
    await db.commit()
    await db.refresh(row)
    background.add_task(_run_decompose_safe, row.template_id,
                        tenant_id_from_request(request), user_id_from_request(request))
    return _ok({"template_id": row.template_id, "status": "analyzing"})


@router.post("/decompose-upload")
async def decompose_upload(background: BackgroundTasks, request: Request,
                           file: UploadFile = File(...),
                           db: AsyncSession = Depends(get_db)):
    """手动上传视频拆解（yt-dlp 解析失败时的兜底入口）。流式落盘，限制单文件大小。"""
    suffix = "." + (file.filename or "video.mp4").rsplit(".", 1)[-1].lower()
    if suffix not in (".mp4", ".mov", ".webm", ".m4v"):
        suffix = ".mp4"
    updir = work_dir("uploads")
    local = updir / f"{uuid.uuid4().hex}{suffix}"
    total = 0
    with local.open("wb") as out:
        while chunk := await file.read(1024 * 1024):
            total += len(chunk)
            if total > MAX_UPLOAD_BYTES:
                out.close()
                local.unlink(missing_ok=True)
                return _fail("上传文件超过 500MB 上限，请压缩后重试")
            out.write(chunk)
    if total == 0:
        local.unlink(missing_ok=True)
        return _fail("上传文件为空")
    try:
        await check_and_count(db, tenant_id_from_request(request))
    except QuotaExceededException as e:
        local.unlink(missing_ok=True)
        return _quota_denied(e)
    row = AiViralTemplate(source_url=f"/ai/creation/media/uploads/{local.name}",
                          status="analyzing",
                          create_by=user_id_from_request(request) or None)
    db.add(row)
    await db.commit()
    await db.refresh(row)
    background.add_task(_run_decompose_safe, row.template_id,
                        tenant_id_from_request(request), user_id_from_request(request))
    return _ok({"template_id": row.template_id, "status": "analyzing"})


async def _run_decompose_safe(template_id: int, tenant_id: int = 0, user_id: str = "") -> None:
    from app.services.creation.decomposer import run_decompose

    try:
        await run_decompose(template_id, tenant_id=tenant_id, user_id=user_id)
    except Exception as exc:  # noqa: BLE001 —— run_decompose 内部已落失败态
        logger.warning("拆解任务异常 template=%s: %s", template_id, exc)


@router.get("/template/{template_id}")
async def get_template(template_id: int, db: AsyncSession = Depends(get_db)):
    row = (await db.execute(select(AiViralTemplate).where(
        AiViralTemplate.template_id == template_id, AiViralTemplate.del_flag == 0
    ))).scalar_one_or_none()
    if row is None:
        return _fail("模板不存在", 500)
    try:
        structure = json.loads(row.structure_json or "null")
    except json.JSONDecodeError:
        structure = None
    return _ok({
        "template_id": row.template_id, "title": row.title, "status": row.status,
        "hook_type": row.hook_type, "duration_sec": row.duration_sec,
        "shot_count": row.shot_count, "asr_text": row.asr_text,
        "structure": structure, "source_video_url": row.source_video_url,
        "error_msg": row.error_msg,
    })


@router.get("/templates")
async def list_templates(hook_type: Optional[str] = None,
                         db: AsyncSession = Depends(get_db)):
    stmt = select(AiViralTemplate).where(AiViralTemplate.del_flag == 0)
    if hook_type:
        stmt = stmt.where(AiViralTemplate.hook_type == hook_type)
    rows = (await db.execute(stmt.order_by(AiViralTemplate.template_id.desc()))).scalars().all()
    return _ok({"rows": [{
        "template_id": r.template_id, "title": r.title, "status": r.status,
        "hook_type": r.hook_type, "duration_sec": r.duration_sec,
        "shot_count": r.shot_count, "create_time": str(r.create_time or ""),
    } for r in rows]})


@router.delete("/template/{template_ids}")
async def delete_templates(template_ids: str, db: AsyncSession = Depends(get_db)):
    try:
        ids = [int(i) for i in template_ids.split(",") if i.strip()]
    except ValueError:
        return _fail("模板 ID 格式错误")
    if not ids:
        return _fail("未指定要删除的模板")
    for tid in ids:
        await db.execute(update(AiViralTemplate)
                         .where(AiViralTemplate.template_id == tid).values(del_flag=1))
    await db.commit()
    return _ok(msg="删除成功")


# ════════════════════════════════════════════════════════════
# 作品项目
# ════════════════════════════════════════════════════════════

@router.get("/models")
async def list_creation_models(model_type: Optional[str] = None,
                               db: AsyncSession = Depends(get_db)):
    """创作域可用模型列表（模型管理中启用的模型），供前端按类型选择。

    文本类含 LLM（Agnes 直连）与 TEXT（商汤聚合的第三方模型：glm/deepseek/kimi/qwen 等），
    两者经 build_chat_llm 均可直接用于写稿。
    """
    from app.models.ai_model import AiModel

    # 支持逗号分隔多类型（如 model_type=LLM,TEXT）
    types = ([t.strip() for t in model_type.split(",") if t.strip()]
             if model_type else ["LLM", "TEXT", "IMAGE", "VIDEO"])
    rows = (await db.execute(select(AiModel).where(
        AiModel.model_type.in_(types), AiModel.status == 1, AiModel.del_flag == 0
    ).order_by(AiModel.model_type, AiModel.model_id.desc()))).scalars().all()
    return _ok({"rows": [{
        "model_id": m.model_id, "model_name": m.model_name, "model_type": m.model_type,
        "default_flag": m.default_flag,
    } for m in rows]})


@router.post("/projects")
async def create_project(body: ProjectCreateReq, request: Request,
                         db: AsyncSession = Depends(get_db)):
    """创建项目：口播稿 → 分镜（LLM 同步生成，约 20-60s）。"""
    try:
        await check_and_count(db, tenant_id_from_request(request))
    except QuotaExceededException as e:
        return _quota_denied(e)
    if not body.create_by:
        body.create_by = user_id_from_request(request) or None
    try:
        project = await storyboard.create_project(db, body)
    except CreationError as exc:
        return _fail(str(exc))
    tenant_id = tenant_id_from_request(request)
    await log_usage(tenant_id, user_id=user_id_from_request(request, body),
                    feature="creation-script")
    return _ok(await _project_detail(project, db))


@router.get("/projects")
async def list_projects(db: AsyncSession = Depends(get_db)):
    rows = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.del_flag == 0
    ).order_by(AiCreationProject.project_id.desc()))).scalars().all()
    return _ok({"rows": [{
        "project_id": r.project_id, "title": r.title, "source_type": r.source_type,
        "status": r.status, "cover_url": r.cover_url, "final_video_url": r.final_video_url,
        "persona_id": r.persona_id, "template_id": r.template_id,
        "create_time": str(r.create_time or ""),
    } for r in rows]})


@router.get("/projects/{project_id}")
async def get_project(project_id: int, db: AsyncSession = Depends(get_db)):
    project = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.project_id == project_id, AiCreationProject.del_flag == 0
    ))).scalar_one_or_none()
    if project is None:
        return _fail("项目不存在", 500)
    return _ok(await _project_detail(project, db))


@router.put("/projects/{project_id}/storyboard")
async def save_storyboard(project_id: int, body: StoryboardUpdateReq,
                          request: Request, db: AsyncSession = Depends(get_db)):
    try:
        project = await storyboard.update_storyboard(
            db, project_id, body.storyboard, create_by=user_id_from_request(request) or None)
    except CreationError as exc:
        return _fail(str(exc))
    return _ok(await _project_detail(project, db))


@router.post("/projects/{project_id}/render")
async def render(project_id: int, request: Request, background: BackgroundTasks,
                 body: Optional[RenderReq] = None,
                 db: AsyncSession = Depends(get_db)):
    # 先 SELECT 判存在性，再用原子 UPDATE 抢占（先查后改的 TOCTOU 会让并发 render 双份计费）
    project = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.project_id == project_id, AiCreationProject.del_flag == 0
    ))).scalar_one_or_none()
    if project is None:
        return _fail("项目不存在", 500)
    result = await db.execute(
        update(AiCreationProject)
        .where(AiCreationProject.project_id == project_id,
               AiCreationProject.status.notin_(("generating", "composing")))
        .values(status="generating", error_msg=None))
    await db.commit()
    if result.rowcount == 0:
        return _fail("渲染进行中，请勿重复提交")
    background.add_task(composer.render_project, project_id,
                        tenant_id_from_request(request), user_id_from_request(request),
                        body.image_model_id if body else None,
                        body.video_model_id if body else None)
    return _ok({"project_id": project_id, "status": "generating"})


@router.post("/projects/{project_id}/shots/{shot_id}/retry")
async def retry_shot(project_id: int, shot_id: int, body: ShotRetryReq,
                     request: Request, background: BackgroundTasks):
    async def _run():
        try:
            await composer.retry_shot_stage(project_id, shot_id, body.stage,
                                            tenant_id=tenant_id_from_request(request),
                                            user_id=user_id_from_request(request))
        except Exception as exc:  # noqa: BLE001 —— 错误已落 shot.error_msg
            logger.warning("镜级重试失败 project=%s shot=%s: %s", project_id, shot_id, exc)

    background.add_task(_run)
    return _ok({"project_id": project_id, "shot_id": shot_id, "stage": body.stage})


@router.post("/projects/{project_id}/variants")
async def create_variants(project_id: int, request: Request, body: HookVariantsReq,
                          db: AsyncSession = Depends(get_db)):
    """一键多变体：克隆项目并生成 N 版替换钩子（正文与各镜产物复用，只重跑首镜 TTS）。"""
    project = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.project_id == project_id, AiCreationProject.del_flag == 0
    ))).scalar_one_or_none()
    if project is None:
        return _fail("项目不存在", 500)
    try:
        await check_and_count(db, tenant_id_from_request(request))
    except QuotaExceededException as e:
        return _quota_denied(e)
    try:
        created = await storyboard.create_hook_variants(
            db, project, body.count, create_by=user_id_from_request(request) or None)
    except CreationError as exc:
        return _fail(str(exc))
    await log_usage(tenant_id_from_request(request),
                    user_id=user_id_from_request(request), feature="creation-variant")
    return _ok({"parent_id": project_id, "variants": created})


@router.get("/projects/{project_id}/download")
async def download(project_id: int, db: AsyncSession = Depends(get_db)):
    project = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.project_id == project_id, AiCreationProject.del_flag == 0
    ))).scalar_one_or_none()
    if project is None or not project.final_video_url:
        return _fail("成片尚未生成", 500)
    if is_local_media_url(project.final_video_url):
        return FileResponse(local_path_of(project.final_video_url),
                            filename=f"creation_{project_id}.mp4", media_type="video/mp4")
    return RedirectResponse(project.final_video_url)


@router.post("/projects/{project_id}/publish-package")
async def publish_package(project_id: int, request: Request, body: PublishPackageReq,
                          db: AsyncSession = Depends(get_db)):
    project = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.project_id == project_id, AiCreationProject.del_flag == 0
    ))).scalar_one_or_none()
    if project is None:
        return _fail("项目不存在", 500)
    try:
        await check_and_count(db, tenant_id_from_request(request))
    except QuotaExceededException as e:
        return _quota_denied(e)
    try:
        package = await storyboard.build_publish_package(db, project, body.extra_instructions)
    except CreationError as exc:
        return _fail(str(exc))
    project.publish_package_json = json.dumps(package, ensure_ascii=False)
    await db.commit()
    await log_usage(tenant_id_from_request(request),
                    user_id=user_id_from_request(request), feature="creation-publish")
    return _ok(package)


async def _project_detail(project: AiCreationProject, db: AsyncSession) -> dict:
    shots = (await db.execute(select(AiStoryboardShot).where(
        AiStoryboardShot.project_id == project.project_id, AiStoryboardShot.del_flag == 0
    ).order_by(AiStoryboardShot.shot_index))).scalars().all()
    try:
        sb = json.loads(project.storyboard_json or "null")
    except json.JSONDecodeError:
        sb = None
    try:
        package = json.loads(project.publish_package_json or "null")
    except json.JSONDecodeError:
        package = None
    return {
        "project_id": project.project_id, "title": project.title,
        "source_type": project.source_type, "status": project.status,
        "topic": project.topic, "script_text": project.script_text,
        "storyboard": sb, "persona_id": project.persona_id,
        "template_id": project.template_id,
        "final_video_url": project.final_video_url, "cover_url": project.cover_url,
        "publish_package": package, "error_msg": project.error_msg,
        "create_time": str(project.create_time or ""),
        "shots": [{
            "shot_id": s.shot_id, "index": s.shot_index, "text": s.text,
            "mode": s.mode, "image_prompt": s.image_prompt,
            "video_prompt": s.video_prompt,
            "image_url": s.image_url, "clip_url": s.clip_url,
            "audio_url": s.audio_url, "audio_duration_ms": s.audio_duration_ms,
            "shot_status": s.shot_status, "error_msg": s.error_msg,
        } for s in shots],
    }


# ════════════════════════════════════════════════════════════
# 人设
# ════════════════════════════════════════════════════════════

@router.get("/personas")
async def get_personas(db: AsyncSession = Depends(get_db)):
    rows = await persona_service.list_personas(db)
    return _ok({"rows": [persona_service.to_dict(p) for p in rows]})


@router.post("/personas")
async def save_persona(body: PersonaSaveReq, request: Request,
                       db: AsyncSession = Depends(get_db)):
    """新增/更新人设。声音克隆在 /ai/music/voices 完成后回填 voice_id/voice_name。"""
    from app.models.ai_persona import AiPersona

    if body.persona_id:
        p = await persona_service.get_persona(db, body.persona_id)
        if p is None:
            return _fail("人设不存在", 500)
    else:
        p = AiPersona(create_by=body.create_by or user_id_from_request(request) or None)
        db.add(p)
    p.name = body.name
    p.description = body.description
    p.style_fewshot = body.style_fewshot
    if body.look_image_urls is not None:
        import json as _json

        p.look_image_urls = _json.dumps(body.look_image_urls, ensure_ascii=False)
    p.voice_id = body.voice_id
    p.voice_name = body.voice_name
    p.banned_words = body.banned_words
    p.status = body.status
    await db.commit()
    await db.refresh(p)
    return _ok(persona_service.to_dict(p))


@router.post("/personas/look")
async def generate_look(body: PersonaGenerateLookReq, request: Request,
                        background: BackgroundTasks,
                        db: AsyncSession = Depends(get_db)):
    """后台生成定妆照（生图数张约 1-2 分钟），完成后回写人设 look_image_urls。"""
    try:
        await check_and_count(db, tenant_id_from_request(request))
    except QuotaExceededException as e:
        return _quota_denied(e)
    background.add_task(_run_look_safe, body.persona_id, body.appearance_prompt, body.count,
                        tenant_id_from_request(request), user_id_from_request(request))
    return _ok({"persona_id": body.persona_id, "status": "generating"})


async def _run_look_safe(persona_id: int, prompt: str, count: int,
                         tenant_id: int = 0, user_id: str = "") -> None:
    try:
        await persona_service.generate_look_images(persona_id, prompt, count,
                                                   tenant_id=tenant_id, user_id=user_id)
    except Exception as exc:  # noqa: BLE001
        logger.warning("定妆照生成失败 persona=%s: %s", persona_id, exc)


@router.post("/personas/look/set")
async def set_persona_look(body: PersonaLookSetReq, db: AsyncSession = Depends(get_db)):
    """把一张已有图片（如图片生成页的作品）一键设为人设定妆照。"""
    try:
        looks = await persona_service.add_look_image(
            db, body.persona_id, body.image_url, body.primary)
    except CreationError as exc:
        return _fail(str(exc))
    return _ok({"persona_id": body.persona_id, "look_image_urls": looks})


@router.delete("/personas/{persona_id}")
async def delete_persona(persona_id: int, db: AsyncSession = Depends(get_db)):
    from sqlalchemy import update as _update
    from app.models.ai_persona import AiPersona

    await db.execute(_update(AiPersona).where(
        AiPersona.persona_id == persona_id).values(del_flag=1))
    await db.commit()
    return _ok(msg="删除成功")


# ════════════════════════════════════════════════════════════
# 本地媒体服务（OSS 未配置时的回退访问通道）
# ════════════════════════════════════════════════════════════

@router.get("/media/{file_path:path}")
async def serve_media(file_path: str):
    try:
        path = local_path_of(f"/ai/creation/media/{file_path}")
    except CreationError:
        return _fail("非法路径")
    if not path.is_file():
        return _fail("文件不存在", 500)
    return FileResponse(path)
