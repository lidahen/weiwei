"""AI 图片生成路由。

【冻结区 — HOBBY】2026-10-15 起停止迭代（docs/ai/AI功能评估-多余与深化-2026-10-15.md）：
与二手交易主业弱协同，保留通用生图演示；主业发布配图走 /ai/assist/product-image。
不新增功能，仅在缺陷时最小修复。
"""
import random
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, Depends
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.ai_image_task import AiImageTask
from app.models.ai_model import AiModel
from app.schemas.image import ImageGenerateRequest, ImageTaskResponse
from app.schemas.common import ApiResponse
from app.services.agnes_image import create_image_task_async, resolve_size
from app.services.model_resolver import ModelConfigError
from app.services.prompt_enhancer import enrich_prompt

router = APIRouter(prefix="/ai/image", tags=["AI图片生成"])


def _to_response(task: AiImageTask) -> ImageTaskResponse:
    return ImageTaskResponse(
        task_id=task.task_id,
        model_id=task.model_id,
        prompt=task.prompt,
        status=task.status,
        result_url=task.result_url,
        error_msg=task.error_msg,
    )


async def _resolve_model(
    db: AsyncSession, model_id: Optional[int]
) -> Optional[AiModel]:
    """解析要使用的图片模型（统一走 model_resolver）。

    指纹变化：给 model_id 时会**校验类型必须是 IMAGE**，把 VIDEO/MUSIC 的 id 传进来
    会抛 ModelConfigError，由调用方转 400 并回传原因，而不是拿错模型去调外部 API。
    """
    from app.services.model_resolver import resolve_model

    return await resolve_model(db, "IMAGE", model_id)


@router.post("/generate")
async def generate_image(
    body: ImageGenerateRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Create an image generation task and execute it asynchronously."""
    # Smart prompt enrichment: expand prompt via LLM, also fill negative_prompt/style
    # task_type 用于注入对应的内置增强 Skill（skills/prompt-enhance/SKILL.md）
    enrichment = await enrich_prompt(
        body.prompt, enabled=body.enhance_prompt, task_type="image"
    )
    enhanced_prompt = enrichment.prompt
    # 仅在用户未提供反向提示词且增强产生了结果时才填充
    negative_prompt = body.negative_prompt or enrichment.negative_prompt
    # 仅在用户未提供风格且增强产生了结果时才填充
    style = body.style or enrichment.style

    # Resolve model（用户传了 model_id 时校验类型；配置错误返回 400 并说明原因）
    try:
        model = await _resolve_model(db, body.model_id)
    except ModelConfigError as e:
        return ApiResponse(code=400, msg=str(e))
    if not model:
        return ApiResponse(code=500, msg="未找到可用的图片生成模型")

    # 尺寸归一：档位(1K/2K/...) + 比例 或 精确尺寸 → 官方原生尺寸，同时落库
    final_size = resolve_size(body.size, body.ratio)

    # 响应格式固定 URL：Base64 结果远超 result_url 列(VARCHAR 500)，落库必失败
    response_format = "url"

    # Parse size into width/height
    width: Optional[int] = None
    height: Optional[int] = None
    parts = final_size.lower().split("x")
    if len(parts) == 2:
        try:
            width = int(parts[0])
            height = int(parts[1])
        except ValueError:
            pass

    # Create task record
    seed = body.seed if body.seed is not None else random.randint(0, 2**32 - 1)
    task = AiImageTask(
        model_id=model.model_id,
        prompt=enhanced_prompt,
        negative_prompt=negative_prompt,
        size=final_size,
        gen_type=body.gen_type,
        input_image=body.input_image,
        response_format=response_format,
        width=width,
        height=height,
        seed=seed,
        style=style,
        status="queued",
        del_flag=0,
        create_by=body.create_by,
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)

    # Launch async generation in background
    background_tasks.add_task(create_image_task_async, task.task_id)

    return ApiResponse(data=_to_response(task).model_dump())


@router.get("/status/{task_id}")
async def get_task_status(task_id: int, db: AsyncSession = Depends(get_db)):
    """Query the status of an image generation task."""
    result = await db.execute(
        select(AiImageTask).where(
            AiImageTask.task_id == task_id,
            AiImageTask.del_flag == 0,
        )
    )
    task = result.scalar_one_or_none()
    if not task:
        return ApiResponse(code=500, msg="任务不存在")
    return ApiResponse(data=_to_response(task).model_dump())


@router.get("/list")
async def list_tasks(db: AsyncSession = Depends(get_db)):
    """List all non-deleted image generation tasks."""
    result = await db.execute(
        select(AiImageTask)
        .where(AiImageTask.del_flag == 0)
        .order_by(AiImageTask.create_time.desc())
    )
    tasks = result.scalars().all()
    rows = [_to_response(t).model_dump() for t in tasks]
    return ApiResponse(data={"total": len(rows), "rows": rows})


@router.delete("/{task_ids}")
async def delete_tasks(task_ids: str, db: AsyncSession = Depends(get_db)):
    """Soft-delete image tasks by comma-separated IDs."""
    ids = [int(i) for i in task_ids.split(",")]
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    for tid in ids:
        await db.execute(
            update(AiImageTask)
            .where(AiImageTask.task_id == tid)
            .values(del_flag=1, update_time=now)
        )
    await db.commit()
    return ApiResponse(msg="删除成功")