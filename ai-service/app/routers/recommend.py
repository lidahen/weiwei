"""商品向量推荐路由：索引重建 / 详情页相似商品 / 首页向量召回候选。

内部服务接口（Java product-server 经 WebClient 调用，docs/二手市场首页推荐设计.md 二期）：
- POST /recommend/reindex     增量同步商品向量索引（body 可指定 tenant_id，缺省同步全部租户；
                              Quartz 每日 04:30 调用，也可管理端手动触发）
- GET  /recommend/similar     详情页相似商品（productId + limit）
- POST /recommend/candidates  首页向量召回候选（seed = 用户近期交互商品 ID）

租户：similar 走 X-Tenant-Id 头（Java 透传，惯例同 assist）；reindex/candidates 显式传参。
降级：embedding 未配置或索引未构建时返回 code=503，Java 侧降级为同类目热门/规则通道。
不接入 ai_usage_daily 配额：本组接口是服务端内部批任务与展示依赖，非用户直接 AI 调用。
"""

import logging
from typing import List, Optional

from fastapi import APIRouter, Request
from pydantic import BaseModel

from app.schemas.common import ApiResponse
from app.services import product_vector as pv
from app.services.quota_service import tenant_id_from_request

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/recommend", tags=["recommend"])


class ReindexRequest(BaseModel):
    tenant_id: Optional[int] = None


class CandidatesRequest(BaseModel):
    product_ids: List[int]
    limit: int = 20


def _unavailable(e: Exception) -> ApiResponse:
    logger.warning("向量推荐不可用: %s", e)
    return ApiResponse(code=503, msg=f"向量推荐不可用: {e}", data=None)


@router.post("/reindex")
async def reindex(body: Optional[ReindexRequest] = None) -> ApiResponse:
    """增量同步商品向量索引。body.tenant_id 指定单租户；缺省同步全部租户。"""
    try:
        if body is not None and body.tenant_id:
            count = await pv.service.reindex(body.tenant_id)
        else:
            count = await pv.service.reindex_all()
        return ApiResponse(data={"indexed": count})
    except pv.ProductVectorUnavailable as e:
        return _unavailable(e)


@router.get("/similar")
async def similar(request: Request, product_id: int, limit: int = 6) -> ApiResponse:
    """详情页相似商品：[{productId, score}]。"""
    tenant_id = tenant_id_from_request(request)
    if tenant_id <= 0:
        return ApiResponse(code=400, msg="缺少租户上下文", data=None)
    try:
        hits = await pv.service.similar(tenant_id, product_id, limit)
        return ApiResponse(data={"hits": hits})
    except pv.ProductVectorUnavailable as e:
        return _unavailable(e)


@router.post("/candidates")
async def candidates(body: CandidatesRequest, request: Request) -> ApiResponse:
    """首页向量召回候选：seed 商品向量的均值检索，排除 seed 自身。"""
    tenant_id = tenant_id_from_request(request)
    if tenant_id <= 0:
        return ApiResponse(code=400, msg="缺少租户上下文", data=None)
    if not body.product_ids:
        return ApiResponse(data={"hits": []})
    try:
        hits = await pv.service.candidates(tenant_id, body.product_ids, body.limit)
        return ApiResponse(data={"hits": [{"productId": h["productId"], "score": h["score"]} for h in hits]})
    except pv.ProductVectorUnavailable as e:
        return _unavailable(e)
