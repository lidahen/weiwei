import json
import re

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from app.database import get_db
from app.models.ai_model import AiModel
from app.schemas.model_config import (
    AiModelCreate, AiModelUpdate, AiModelResponse, ChangeStatusRequest,
)
from app.schemas.common import ApiResponse
from app.schemas.model_config import MODEL_TYPES

router = APIRouter(prefix="/ai/model", tags=["AI模型管理"])


@router.get("/types")
async def list_model_types():
    """暴露 model_type 枚举给前端下拉框。"""
    return ApiResponse(data={"types": MODEL_TYPES})


def _to_response(model: AiModel) -> dict:
    def _str(v):
        return str(v) if v is not None else None
    return AiModelResponse(
        model_id=model.model_id,
        model_provider=model.model_provider,
        model_type=model.model_type,
        model_name=model.model_name,
        model_alias=model.model_alias,
        api_key=model.api_key,
        base_url=model.base_url,
        temperature=_str(model.temperature),
        top_p=_str(model.top_p),
        ext_param=model.ext_param,
        default_flag=model.default_flag,
        status=model.status,
        del_flag=model.del_flag,
        create_by=model.create_by,
        create_time=_str(model.create_time),
        update_time=_str(model.update_time),
    ).model_dump(by_alias=True)


@router.get("/list")
async def list_models(db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(AiModel)
        .where(AiModel.del_flag == 0)
        .order_by(AiModel.default_flag.desc(), AiModel.create_time.desc())
    )
    models = result.scalars().all()
    rows = [_to_response(m) for m in models]
    return ApiResponse(data={"total": len(rows), "rows": rows})


_SIZE_RE = re.compile(r"^\d{2,5}x\d{2,5}$", re.IGNORECASE)


def _declared_sizes(ext_param) -> list:
    """模型显式声明的图片输出尺寸白名单。

    与 home_widget/senseaudio 的「无声明回落默认档位」不同：这里**无声明即返回空**，
    表示该模型不做尺寸限制（前端放开全部宽高比），避免误伤 Agnes 等未声明尺寸的模型。
    """
    if isinstance(ext_param, str):
        try:
            ext_param = json.loads(ext_param)
        except (ValueError, TypeError):
            return []
    raw = ext_param.get("sizes") if isinstance(ext_param, dict) else None
    if not isinstance(raw, list):
        return []
    return [
        s for s in (str(x).strip().lower() for x in raw) if _SIZE_RE.match(s)
    ]


@router.get("/by-type/{model_type}")
async def list_models_by_type(model_type: str, db: AsyncSession = Depends(get_db)):
    """供前端生成表单用的精简列表(只返回前端需要的字段)。

    按 model_type + status=1 过滤, default_flag=1 排前。
    IMAGE 类型附带 sizes（模型声明的输出尺寸白名单），前端据此收敛宽高比选项；
    空数组表示不限制。
    """
    result = await db.execute(
        select(AiModel)
        .where(
            AiModel.model_type == model_type,
            AiModel.status == 1,
            AiModel.del_flag == 0,
        )
        .order_by(AiModel.default_flag.desc(), AiModel.model_id.asc())
    )
    models = result.scalars().all()
    is_image = (model_type or "").upper() == "IMAGE"
    rows = [
        {
            "model_id": m.model_id,
            "model_name": m.model_name,
            "model_alias": m.model_alias,
            "model_provider": m.model_provider,
            "is_default": (m.default_flag == 1),
            "sizes": _declared_sizes(m.ext_param) if is_image else [],
        }
        for m in models
    ]
    return ApiResponse(data={"total": len(rows), "rows": rows})


@router.get("/{model_id}")
async def get_model(model_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(AiModel).where(AiModel.model_id == model_id, AiModel.del_flag == 0)
    )
    model = result.scalar_one_or_none()
    if not model:
        return ApiResponse(code=500, msg="模型不存在")
    return ApiResponse(data=_to_response(model))


@router.post("")
async def create_model(body: AiModelCreate, db: AsyncSession = Depends(get_db)):
    # 只写用户显式传入的字段：显式 NULL 会覆盖 DB 默认值，
    # 且 model_alias/api_key/base_url 等列为 NOT NULL，写 NULL 会直接 500
    values = {k: v for k, v in body.model_dump().items() if v is not None}
    model = AiModel(**values)
    db.add(model)
    await db.commit()
    await db.refresh(model)
    return ApiResponse(data=_to_response(model))


@router.put("")
async def update_model(body: AiModelUpdate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(AiModel).where(AiModel.model_id == body.model_id, AiModel.del_flag == 0)
    )
    model = result.scalar_one_or_none()
    if not model:
        return ApiResponse(code=500, msg="模型不存在")
    for key, value in body.model_dump(exclude_unset=True, exclude={"model_id"}).items():
        setattr(model, key, value)
    await db.commit()
    return ApiResponse(msg="修改成功")


@router.delete("/{model_ids}")
async def delete_models(model_ids: str, db: AsyncSession = Depends(get_db)):
    ids = [int(i) for i in model_ids.split(",")]
    for mid in ids:
        await db.execute(
            update(AiModel).where(AiModel.model_id == mid).values(del_flag=1)
        )
    await db.commit()
    return ApiResponse(msg="删除成功")


@router.put("/status")
async def change_status(body: ChangeStatusRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(AiModel).where(AiModel.model_id == body.model_id, AiModel.del_flag == 0)
    )
    model = result.scalar_one_or_none()
    if not model:
        return ApiResponse(code=500, msg="模型不存在")
    if body.default_flag == 1:
        await db.execute(
            update(AiModel)
            .where(AiModel.model_type == model.model_type, AiModel.del_flag == 0)
            .values(default_flag=0)
        )
    model.default_flag = body.default_flag
    await db.commit()
    return ApiResponse(msg="操作成功")
