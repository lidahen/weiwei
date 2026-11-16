"""人设库服务：CRUD + 定妆照生成（声音克隆走既有 /ai/music/voices 端点，前端直调后回填 voice_id）。"""
from __future__ import annotations

import json
import logging
from typing import List, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.ai_persona import AiPersona
from app.services.creation.media_store import CreationError
from app.services.model_resolver import resolve_model

logger = logging.getLogger(__name__)


async def get_persona(db: AsyncSession, persona_id: int) -> Optional[AiPersona]:
    return (await db.execute(select(AiPersona).where(
        AiPersona.persona_id == persona_id, AiPersona.del_flag == 0
    ))).scalar_one_or_none()


async def list_personas(db: AsyncSession) -> List[AiPersona]:
    return list((await db.execute(select(AiPersona).where(
        AiPersona.del_flag == 0
    ).order_by(AiPersona.persona_id.desc()))).scalars().all())


def to_dict(p: AiPersona) -> dict:
    try:
        looks = json.loads(p.look_image_urls or "[]")
    except json.JSONDecodeError:
        looks = []
    return {
        "persona_id": p.persona_id,
        "name": p.name,
        "description": p.description,
        "style_fewshot": p.style_fewshot,
        "look_image_urls": looks,
        "voice_id": p.voice_id,
        "voice_name": p.voice_name,
        "banned_words": p.banned_words,
        "status": p.status,
        "create_time": str(p.create_time or ""),
    }


async def add_look_image(db: AsyncSession, persona_id: int, image_url: str,
                         primary: bool = True) -> List[str]:
    """把一张已有图片（如图片生成页的作品）设为人设定妆照。

    primary=True 时插到 look_image_urls 首位——渲染取数组第一张作为分镜生图的
    图生图参考图，即"主形象"；上限 5 张，去重。
    """
    persona = await get_persona(db, persona_id)
    if persona is None:
        raise CreationError("人设不存在")
    url = (image_url or "").strip()
    if not url:
        raise CreationError("image_url 必填")
    try:
        existing = json.loads(persona.look_image_urls or "[]")
    except json.JSONDecodeError:
        existing = []
    existing = [u for u in existing if u != url]
    merged = ([url] if primary else []) + existing
    persona.look_image_urls = json.dumps(merged[:5], ensure_ascii=False)
    await db.commit()
    return merged[:5]


async def generate_look_images(persona_id: int, appearance_prompt: str, count: int = 3,
                               tenant_id: int = 0, user_id: str = "") -> List[str]:
    """定妆照：按人设外观描述生成 N 张 9:16 形象图，追加进 look_image_urls（上限 5 张）。

    追加而非覆盖：用户手动上传/已生成的形象图不丢失；超限时保留最新的 5 张。
    """
    from app.database import async_session

    from app.models.ai_image_task import AiImageTask
    from app.services.agnes_image import create_image_task_async, resolve_size
    from app.services.quota_service import check_and_count, log_usage

    async with async_session() as db:
        persona = await get_persona(db, persona_id)
        if persona is None:
            raise CreationError("人设不存在")
        model = await resolve_model(db, "IMAGE")
        if model is None:
            raise CreationError("未配置 IMAGE 类型模型（模型管理中添加）")
        size = resolve_size("2K", "9:16")
        try:
            existing = json.loads(persona.look_image_urls or "[]")
        except json.JSONDecodeError:
            existing = []
        urls: List[str] = []
        failed = 0
        for _ in range(max(1, min(count, 5))):
            await check_and_count(db, tenant_id)
            task = AiImageTask(
                model_id=model.model_id,
                prompt=(f"{appearance_prompt}，统一角色形象定妆照，竖版半身构图，"
                        "简洁背景，柔和布光，真实感摄影风格，高细节"),
                gen_type="text2img",
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
            await create_image_task_async(task.task_id)
            await db.refresh(task)
            if task.status == "completed" and task.result_url:
                urls.append(task.result_url)
                await log_usage(tenant_id, user_id=user_id, feature="creation-look",
                                model_name=getattr(model, "model_name", ""), task_type="image")
            else:
                failed += 1
        if not urls:
            raise CreationError("定妆照生成失败，请检查生图模型配置")
        if failed:
            logger.warning("定妆照部分失败 persona=%s: %s/%s 张未生成", persona_id, failed, count)
        merged = (urls + [u for u in existing if u not in urls])[:5]
        persona.look_image_urls = json.dumps(merged, ensure_ascii=False)
        await db.commit()
        return urls
