"""AI 知识库管理接口（/ai/knowledge）。"""
import csv
import io
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Body, Depends
from fastapi.responses import StreamingResponse
from sqlalchemy import delete, func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.ai_doc import AiDoc
from app.models.ai_doc_slice import AiDocSlice
from app.models.ai_knowledge import AiKnowledge
from app.schemas.common import ApiResponse
from app.schemas.knowledge import KnowledgeCreate, KnowledgeResponse, KnowledgeUpdate
from app.services.rag_service import delete_index

router = APIRouter(prefix="/ai/knowledge", tags=["AI知识库"])


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _to_response(kb: AiKnowledge) -> dict:
    def _s(v):
        return str(v) if v is not None else None

    return KnowledgeResponse(
        knowledge_id=kb.knowledge_id,
        knowledge_name=kb.knowledge_name,
        knowledge_desc=kb.knowledge_desc,
        welcome_msg=kb.welcome_msg,
        multi_round=kb.multi_round,
        top_k=kb.top_k,
        score=kb.score,
        slice_size=kb.slice_size,
        knowledge_sort=kb.knowledge_sort,
        del_flag=kb.del_flag,
        create_by=kb.create_by,
        create_time=_s(kb.create_time),
        update_by=kb.update_by,
        update_time=_s(kb.update_time),
    ).model_dump(by_alias=True)


@router.get("/pageQuery")
async def page_query(
    knowledgeName: Optional[str] = None,
    pageNum: int = 1,
    pageSize: int = 10,
    current: Optional[int] = None,
    db: AsyncSession = Depends(get_db),
):
    page = current or pageNum or 1
    size = max(1, min(pageSize or 10, 100))
    stmt = select(AiKnowledge).where(AiKnowledge.del_flag == 0)
    if knowledgeName:
        stmt = stmt.where(AiKnowledge.knowledge_name.like(f"%{knowledgeName}%"))
    total = await db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = (
        (
            await db.execute(
                stmt.order_by(
                    AiKnowledge.knowledge_sort.asc(), AiKnowledge.create_time.desc()
                )
                .offset((page - 1) * size)
                .limit(size)
            )
        )
        .scalars()
        .all()
    )
    return ApiResponse(
        data={"records": [_to_response(k) for k in rows], "total": total or 0}
    )


@router.get("/list")
async def list_all(db: AsyncSession = Depends(get_db)):
    """知识库下拉选项（文档/聊天页面使用）。"""
    rows = (
        (
            await db.execute(
                select(AiKnowledge)
                .where(AiKnowledge.del_flag == 0)
                .order_by(AiKnowledge.knowledge_sort.asc())
            )
        )
        .scalars()
        .all()
    )
    return ApiResponse(
        data=[
            {"knowledgeId": k.knowledge_id, "knowledgeName": k.knowledge_name}
            for k in rows
        ]
    )


@router.get("/{knowledge_id}")
async def get_knowledge(knowledge_id: int, db: AsyncSession = Depends(get_db)):
    kb = await db.get(AiKnowledge, knowledge_id)
    if not kb or kb.del_flag != 0:
        return ApiResponse(code=500, msg="知识库不存在")
    return ApiResponse(data=_to_response(kb))


@router.post("")
async def create_knowledge(body: KnowledgeCreate, db: AsyncSession = Depends(get_db)):
    kb = AiKnowledge(
        knowledge_name=body.knowledge_name,
        knowledge_desc=body.knowledge_desc,
        welcome_msg=body.welcome_msg,
        multi_round=body.multi_round,
        top_k=body.top_k,
        score=body.score,
        slice_size=body.slice_size,
        knowledge_sort=body.knowledge_sort,
        create_by=body.create_by,
        create_time=now_str(),
    )
    db.add(kb)
    await db.commit()
    await db.refresh(kb)
    return ApiResponse(data=_to_response(kb))


@router.put("")
async def update_knowledge(body: KnowledgeUpdate, db: AsyncSession = Depends(get_db)):
    kb = await db.get(AiKnowledge, body.knowledge_id)
    if not kb or kb.del_flag != 0:
        return ApiResponse(code=500, msg="知识库不存在")
    data = body.model_dump(exclude_unset=True, exclude={"knowledge_id"})
    for key, value in data.items():
        setattr(kb, key, value)
    kb.update_time = now_str()
    await db.commit()
    return ApiResponse(msg="修改成功")


@router.delete("/{knowledge_ids}")
async def delete_knowledge(knowledge_ids: str, db: AsyncSession = Depends(get_db)):
    """删除知识库（级联删除其文档与切片，并移除向量索引）。"""
    ids = [int(i) for i in knowledge_ids.split(",") if i.strip()]
    for kid in ids:
        kb = await db.get(AiKnowledge, kid)
        if not kb:
            continue
        # 物理删除切片
        await db.execute(delete(AiDocSlice).where(AiDocSlice.knowledge_id == kid))
        # 软删除文档
        await db.execute(update(AiDoc).where(AiDoc.knowledge_id == kid).values(del_flag=1))
        # 软删除知识库
        await db.execute(update(AiKnowledge).where(AiKnowledge.knowledge_id == kid).values(del_flag=1))
        # 移除向量索引
        await delete_index(kid)
    await db.commit()
    return ApiResponse(msg="删除成功")


@router.post("/export")
async def export_knowledge(
    body: dict = Body(default={}), db: AsyncSession = Depends(get_db)
):
    stmt = select(AiKnowledge).where(AiKnowledge.del_flag == 0)
    if body.get("knowledgeName"):
        stmt = stmt.where(AiKnowledge.knowledge_name.like(f"%{body.get('knowledgeName')}%"))
    rows = (await db.execute(stmt)).scalars().all()

    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(["knowledgeId", "knowledgeName", "knowledgeDesc", "knowledgeSort", "createTime"])
    for r in rows:
        writer.writerow(
            [r.knowledge_id, r.knowledge_name, r.knowledge_desc, r.knowledge_sort, r.create_time]
        )
    csv_data = buf.getvalue().encode("utf-8-sig")
    return StreamingResponse(
        iter([csv_data]),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=ai_knowledge.csv"},
    )
