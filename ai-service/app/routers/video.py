"""AI 视频生成路由。

【冻结区 — HOBBY】2026-10-15 起停止迭代（docs/ai/AI功能评估-多余与深化-2026-10-15.md）：
与二手交易主业零协同，保留技术演示；不新增功能，仅在缺陷时最小修复。
"""
import logging
import random

from fastapi import APIRouter, Depends, BackgroundTasks
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update

from app.database import get_db
from app.models.ai_video_task import AiVideoTask
from app.schemas.video import VideoGenerateRequest, VideoTaskResponse
from app.schemas.common import ApiResponse
from app.services.agnes_video import create_video_task_async, query_video_status
from app.services.model_resolver import ModelConfigError, resolve_model
from app.services.prompt_enhancer import enrich_prompt

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai/video", tags=["AI视频生成"])


def _to_response(task: AiVideoTask) -> dict:
    return VideoTaskResponse(
        task_id=task.task_id,
        model_id=task.model_id,
        agnes_task_id=task.agnes_task_id,
        agnes_video_id=task.agnes_video_id,
        prompt=task.prompt,
        status=task.status,
        progress=task.progress,
        result_url=task.result_url,
        video_seconds=task.video_seconds,
        video_size=task.video_size,
        error_msg=task.error_msg,
    ).model_dump()


@router.post("/generate")
async def generate_video(
    body: VideoGenerateRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Create a video generation task."""
    # Smart prompt enrichment: expand prompt via LLM, also fill negative_prompt
    # task_type 用于注入对应的内置增强 Skill（skills/prompt-enhance/SKILL.md）
    enrichment = await enrich_prompt(
        body.prompt, enabled=body.enhance_prompt, task_type="video"
    )
    enhanced_prompt = enrichment.prompt
    # 仅在用户未提供反向提示词且增强产生了结果时才填充
    negative_prompt = body.negative_prompt or enrichment.negative_prompt

    # 解析 VIDEO 模型：用户传了 model_id 则校验其类型必须是 VIDEO（此前完全不校验，
    # 传 IMAGE/MUSIC 的 id 会被静默接受并落库）；未传则取该类型默认。
    try:
        video_model = await resolve_model(db, "VIDEO", body.model_id)
    except ModelConfigError as e:
        return ApiResponse(code=400, msg=str(e))
    if not video_model:
        return ApiResponse(code=500, msg="未配置视频生成模型，请在模型管理中添加VIDEO类型模型")
    model_id = video_model.model_id

    # 仅在用户未提供随机种子时自动生成一个
    seed = body.seed if body.seed is not None else random.randint(0, 2**32 - 1)

    # Create task record
    # 计算 duration (秒): 用户传 numFrames/frameRate, 或显式传 duration
    duration_sec = None
    if getattr(body, "duration", None):
        duration_sec = int(body.duration)
    elif body.num_frames and body.frame_rate:
        duration_sec = round(body.num_frames / body.frame_rate)

    # doubao-seedance 端点不识别 num_frames/frame_rate 字段(会导致 400 invalid),
    # 但 DB schema 保留这两个字段用于其他 vendor (如 agnes)。
    # 落库时存 num_frames, 但调用 senseaudio 时 vendor 自己用 duration 即可。
    task = AiVideoTask(
        model_id=model_id,
        prompt=enhanced_prompt,
        gen_type=body.gen_type or "text2vid",
        input_images=body.input_images,
        width=body.width,
        height=body.height,
        num_frames=body.num_frames,
        frame_rate=body.frame_rate,
        duration=duration_sec,
        negative_prompt=negative_prompt,
        seed=seed,
        status="queued",
        progress=0,
        create_by=body.create_by,
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)

    # Launch async generation in background
    background_tasks.add_task(create_video_task_async, task.task_id)

    return ApiResponse(data=_to_response(task))


@router.get("/status/{task_id}")
async def get_video_status(
    task_id: int,
    db: AsyncSession = Depends(get_db),
):
    """Query video generation task status."""
    result = await db.execute(
        select(AiVideoTask).where(
            AiVideoTask.task_id == task_id,
            AiVideoTask.del_flag == 0,
        )
    )
    task = result.scalar_one_or_none()
    if not task:
        return ApiResponse(code=500, msg="任务不存在")

    if task.agnes_video_id:
        try:
            task = await query_video_status(task, db)
            await db.commit()
        except Exception as exc:
            logger.error("Failed to query Agnes status for task %s: %s", task_id, exc)

    return ApiResponse(data=_to_response(task))


@router.get("/list")
async def list_video_tasks(db: AsyncSession = Depends(get_db)):
    """List all non-deleted video generation tasks."""
    # task_id 自增单调，按它排序避免历史行 create_time 为 NULL 时沉底/置顶错乱
    result = await db.execute(
        select(AiVideoTask)
        .where(AiVideoTask.del_flag == 0)
        .order_by(AiVideoTask.task_id.desc())
    )
    tasks = result.scalars().all()
    rows = [_to_response(t) for t in tasks]
    return ApiResponse(data={"total": len(rows), "rows": rows})


@router.delete("/{task_ids}")
async def delete_video_tasks(
    task_ids: str,
    db: AsyncSession = Depends(get_db),
):
    """Soft-delete video tasks by comma-separated IDs."""
    ids = [int(i) for i in task_ids.split(",")]
    for tid in ids:
        await db.execute(
            update(AiVideoTask).where(AiVideoTask.task_id == tid).values(del_flag=1)
        )
    await db.commit()
    return ApiResponse(msg="删除成功")