"""AI 知识库文档管理接口（/ai/doc）。"""
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
from app.schemas.doc import DocCreate, DocResponse, DocUpdate
from app.services.doc_parser import fetch_and_extract
from app.services.rag_service import rebuild_index, split_text

router = APIRouter(prefix="/ai/doc", tags=["AI知识库文档"])


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _to_response(doc: AiDoc) -> dict:
    def _s(v):
        return str(v) if v is not None else None

    return DocResponse(
        doc_id=doc.doc_id,
        knowledge_id=doc.knowledge_id,
        doc_name=doc.doc_name,
        source_type=doc.source_type,
        content=doc.content,
        file_url=doc.file_url,
        file_name=doc.file_name,
        slice_count=doc.slice_count,
        char_count=doc.char_count,
        status=doc.status,
        del_flag=doc.del_flag,
        create_by=doc.create_by,
        create_time=_s(doc.create_time),
        update_time=_s(doc.update_time),
    ).model_dump(by_alias=True)


async def _extract_doc_content(body: DocCreate) -> tuple:
    """根据来源类型提取文档原始文本，返回 (raw_text, file_url, file_name)。"""
    file_url, file_name = body.file_url, body.file_name
    if body.source_type == "UPLOAD":
        files = body.file_list or []
        texts = []
        for f in files:
            if not f.file_url:
                continue
            try:
                text = await fetch_and_extract(f.file_url, f.file_name or "")
            except Exception as e:
                return None, None, None, f"文件解析失败: {e}"
            if text:
                texts.append(text)
            if not file_url:
                file_url = f.file_url
            if not file_name:
                file_name = f.file_name
        return "\n\n".join(texts), file_url, file_name, None
    return (body.content or ""), file_url, file_name, None


async def _re_slice(db: AsyncSession, doc: AiDoc, raw_text: str, slice_size: int) -> int:
    """删除旧切片并按新内容重新切片，返回切片数量。"""
    await db.execute(delete(AiDocSlice).where(AiDocSlice.doc_id == doc.doc_id))
    chunks = split_text(raw_text, slice_size)
    for i, chunk in enumerate(chunks, start=1):
        db.add(
            AiDocSlice(
                doc_id=doc.doc_id,
                knowledge_id=doc.knowledge_id,
                slice_index=i,
                content=chunk,
                char_count=len(chunk),
                vector_status="pending",
                create_time=now_str(),
            )
        )
    return len(chunks)


@router.get("/pageQuery")
async def page_query(
    knowledgeId: Optional[int] = None,
    docName: Optional[str] = None,
    pageNum: int = 1,
    pageSize: int = 10,
    current: Optional[int] = None,
    db: AsyncSession = Depends(get_db),
):
    page = current or pageNum or 1
    size = max(1, min(pageSize or 10, 100))
    stmt = select(AiDoc).where(AiDoc.del_flag == 0)
    if knowledgeId:
        stmt = stmt.where(AiDoc.knowledge_id == knowledgeId)
    if docName:
        stmt = stmt.where(AiDoc.doc_name.like(f"%{docName}%"))
    total = await db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = (
        (
            await db.execute(
                stmt.order_by(AiDoc.create_time.desc())
                .offset((page - 1) * size)
                .limit(size)
            )
        )
        .scalars()
        .all()
    )
    return ApiResponse(
        data={"records": [_to_response(d) for d in rows], "total": total or 0}
    )


@router.get("/{doc_id}")
async def get_doc(doc_id: int, db: AsyncSession = Depends(get_db)):
    doc = await db.get(AiDoc, doc_id)
    if not doc or doc.del_flag != 0:
        return ApiResponse(code=500, msg="文档不存在")
    return ApiResponse(data=_to_response(doc))


@router.post("")
async def create_doc(body: DocCreate, db: AsyncSession = Depends(get_db)):
    kb = await db.get(AiKnowledge, body.knowledge_id)
    if not kb or kb.del_flag != 0:
        return ApiResponse(code=500, msg="知识库不存在")

    raw_text, file_url, file_name, err = await _extract_doc_content(body)
    if err:
        return ApiResponse(code=500, msg=err)

    doc_name = body.doc_name or file_name or "未命名文档"
    doc = AiDoc(
        knowledge_id=body.knowledge_id,
        doc_name=doc_name,
        source_type=body.source_type,
        content=raw_text,
        file_url=file_url,
        file_name=file_name,
        slice_count=0,
        char_count=len(raw_text),
        status="processing",
        create_by=body.create_by,
        create_time=now_str(),
    )
    db.add(doc)
    await db.flush()

    slice_size = (kb.slice_size or 3000) if kb.slice_size else 3000
    doc.slice_count = await _re_slice(db, doc, raw_text, slice_size)
    doc.status = "completed"
    await db.commit()

    # 重建知识库向量索引
    await rebuild_index(db, body.knowledge_id)
    return ApiResponse(msg="新增成功", data=_to_response(doc))


@router.put("")
async def update_doc(body: DocUpdate, db: AsyncSession = Depends(get_db)):
    doc = await db.get(AiDoc, body.doc_id)
    if not doc or doc.del_flag != 0:
        return ApiResponse(code=500, msg="文档不存在")

    data = body.model_dump(exclude_unset=True, exclude={"doc_id"})
    content_changed = "content" in data and data["content"] is not None

    for key, value in data.items():
        setattr(doc, key, value)

    if content_changed:
        kb = await db.get(AiKnowledge, doc.knowledge_id)
        slice_size = (kb.slice_size or 3000) if kb else 3000
        doc.slice_count = await _re_slice(db, doc, doc.content or "", slice_size)
        doc.char_count = len(doc.content or "")
        doc.status = "completed"
        await db.commit()
        await rebuild_index(db, doc.knowledge_id)
    else:
        doc.update_time = now_str()
        await db.commit()
    return ApiResponse(msg="修改成功")


@router.delete("/{doc_ids}")
async def delete_docs(doc_ids: str, db: AsyncSession = Depends(get_db)):
    """删除文档（级联删除切片并重建知识库索引）。"""
    ids = [int(i) for i in doc_ids.split(",") if i.strip()]
    kb_ids = set()
    for did in ids:
        doc = await db.get(AiDoc, did)
        if not doc:
            continue
        kb_ids.add(doc.knowledge_id)
        await db.execute(delete(AiDocSlice).where(AiDocSlice.doc_id == did))
        await db.execute(update(AiDoc).where(AiDoc.doc_id == did).values(del_flag=1))
    await db.commit()
    for kid in kb_ids:
        await rebuild_index(db, kid)
    return ApiResponse(msg="删除成功")


@router.post("/export")
async def export_docs(body: dict = Body(default={}), db: AsyncSession = Depends(get_db)):
    stmt = select(AiDoc).where(AiDoc.del_flag == 0)
    if body.get("knowledgeId"):
        stmt = stmt.where(AiDoc.knowledge_id == int(body["knowledgeId"]))
    rows = (await db.execute(stmt)).scalars().all()

    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(
        ["docId", "knowledgeId", "docName", "sourceType", "sliceCount", "charCount", "status", "createTime"]
    )
    for r in rows:
        writer.writerow(
            [r.doc_id, r.knowledge_id, r.doc_name, r.source_type, r.slice_count, r.char_count, r.status, r.create_time]
        )
    csv_data = buf.getvalue().encode("utf-8-sig")
    return StreamingResponse(
        iter([csv_data]),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=ai_doc.csv"},
    )
