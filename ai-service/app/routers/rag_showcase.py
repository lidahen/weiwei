"""平台规则 RAG 调试接口（/ai/rag-showcase）。

面向本地演示与评测：返回答案、引用和完整检索 trace。
前端如要展示，必须经 Java BFF 转发，不直连本服务（架构不变量）。
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

from fastapi import APIRouter, Request

from app.config import settings
from app.schemas.common import ApiResponse
from app.services.rag_showcase.service import get_service

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/ai/rag-showcase", tags=["RAG 规则问答"])

_EVAL_REPORT = Path(__file__).resolve().parents[3] / "docs" / "rag-showcase" / "eval-report.json"


async def _ensure_ready(service) -> None:
    ensure = getattr(service, "ensure_ready", None)
    if ensure is not None:
        await ensure()


@router.post("/query")
async def query_endpoint(body: dict):
    query = str(body.get("query") or "").strip()
    if not query:
        return ApiResponse(code=400, msg="query 不能为空")
    include_superseded = body.get("include_superseded", body.get("includeSuperseded"))
    try:
        service = get_service()
        await _ensure_ready(service)
        data = await service.query(
            query,
            include_superseded=include_superseded,
            debug=bool(body.get("debug")),
        )
        return ApiResponse(data=data)
    except FileNotFoundError as e:
        return ApiResponse(code=503, msg=f"索引或语料不可用: {e}")
    except Exception as e:  # noqa: BLE001 —— 路由层统一转友好错误
        logger.error("rag showcase query failed: %s", e)
        return ApiResponse(code=500, msg="RAG 服务暂时不可用")


@router.post("/rebuild")
async def rebuild_endpoint(request: Request, body: dict | None = None):
    if settings.internal_api_token and request.headers.get("X-Internal-Token") != settings.internal_api_token:
        return ApiResponse(code=401, msg="unauthorized")
    body = body or {}
    try:
        service = get_service()
        manifest = await service.rebuild(force=bool(body.get("force")))
        return ApiResponse(data=manifest)
    except Exception as e:  # noqa: BLE001
        logger.error("rag showcase rebuild failed: %s", e)
        return ApiResponse(code=500, msg="索引重建失败")


@router.get("/corpus")
async def corpus_endpoint():
    try:
        service = get_service()
        await _ensure_ready(service)
        return ApiResponse(data=service.corpus())
    except Exception as e:  # noqa: BLE001
        logger.error("rag showcase corpus failed: %s", e)
        return ApiResponse(code=500, msg="语料读取失败")


@router.get("/traces")
async def traces_endpoint(limit: int = 50):
    limit = max(1, min(limit, 200))
    try:
        service = get_service()
        return ApiResponse(data=service.trace_store.recent(limit))
    except Exception as e:  # noqa: BLE001
        logger.error("rag showcase traces failed: %s", e)
        return ApiResponse(code=500, msg="trace 读取失败")


@router.get("/eval/latest")
async def eval_latest_endpoint():
    if not _EVAL_REPORT.exists():
        return ApiResponse(code=404, msg="暂无评测报告")
    try:
        return ApiResponse(data=json.loads(_EVAL_REPORT.read_text(encoding="utf-8")))
    except Exception as e:  # noqa: BLE001
        logger.error("rag showcase eval report failed: %s", e)
        return ApiResponse(code=500, msg="评测报告读取失败")
