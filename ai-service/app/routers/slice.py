"""AI 文档切片管理接口（/ai/slice）。"""
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.ai_doc_slice import AiDocSlice
from app.schemas.common import ApiResponse
from app.schemas.slice import SliceResponse, SliceUpdate
from app.services.rag_service import rebuild_index

router = APIRouter(prefix="/ai/slice", tags=["AI文档切片"])


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _to_response(s: AiDocSlice) -> dict:
    return SliceResponse(
        slice_id=s.slice_id,
        doc_id=s.doc_id,
        knowledge_id=s.knowledge_id,
        slice_index=s.slice_index,
        content=s.content,
        char_count=s.char_count,
        vector_status=s.vector_status,
        create_time=s.create_time,
    ).model_dump(by_alias=True)


@router.get("/pageQuery")
async def page_query(
    docId: Optional[int] = None,
    pageNum: int = 1,
    pageSize: int = 10,
    current: Optional[int] = None,
    db: AsyncSession = Depends(get_db),
):
    page = current or pageNum or 1
    size = max(1, min(pageSize or 10, 100))
    stmt = select(AiDocSlice)
    if docId:
        stmt = stmt.where(AiDocSlice.doc_id == docId)
    total = await db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = (
        (
            await db.execute(
                stmt.order_by(AiDocSlice.slice_index.asc())
                .offset((page - 1) * size)
                .limit(size)
            )
        )
        .scalars()
        .all()
    )
    return ApiResponse(
        data={"records": [_to_response(s) for s in rows], "total": total or 0}
    )


@router.get("/{slice_id}")
async def get_slice(slice_id: int, db: AsyncSession = Depends(get_db)):
    s = await db.get(AiDocSlice, slice_id)
    if not s:
        return ApiResponse(code=500, msg="切片不存在")
    return ApiResponse(data=_to_response(s))


@router.put("")
async def update_slice(body: SliceUpdate, db: AsyncSession = Depends(get_db)):
    s = await db.get(AiDocSlice, body.slice_id)
    if not s:
        return ApiResponse(code=500, msg="切片不存在")
    data = body.model_dump(exclude_unset=True, exclude={"slice_id"})
    content_changed = False
    for key, value in data.items():
        setattr(s, key, value)
        if key == "content":
            content_changed = True
    if content_changed:
        s.char_count = len(s.content or "")
        s.vector_status = "pending"
        await db.commit()
        # 重新向量化该知识库
        await rebuild_index(db, s.knowledge_id)
    else:
        await db.commit()
    return ApiResponse(msg="修改成功")
