"""换物双向向量匹配路由：索引重建 / 需求侧推荐 / 供给侧反向召回。

内部服务接口（Java exchange-server 经 WebClient 调用，docs/交换广场二期-智能匹配与信用设计.md 三章）：
- POST /exchange-match/reindex  增量同步换物双索引（body 可指定 tenant_id，缺省同步全部租户；
                                Job 124 每 30 分钟调用，也可管理端手动触发）
- POST /exchange-match/for-me   我的需求文本 ↔ 供给索引（Square 推荐位，want_texts 均值检索）
- GET  /exchange-match/wanters  物品供给向量 ↔ 需求索引（详情页「可能想换的人」+ Job 125 推送扫描）

租户：for-me/wanters 走 X-Tenant-Id 头（Java 透传，惯例同 recommend）；reindex 显式传参。
降级：embedding 未配置或索引未构建时返回 code=503，Java 侧降级为空列表/隐藏区块/跳过推送。
不接入 ai_usage_daily 配额：本组接口是服务端内部批任务与展示依赖，非用户直接 AI 调用。
"""

import logging
from typing import List, Optional

from fastapi import APIRouter, Request
from pydantic import BaseModel

from app.schemas.common import ApiResponse
from app.services import exchange_vector as ev
from app.services.quota_service import tenant_id_from_request

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/exchange-match", tags=["exchange-match"])


class ReindexRequest(BaseModel):
    tenant_id: Optional[int] = None


class ForMeRequest(BaseModel):
    want_texts: List[str]
    limit: int = 8


def _unavailable(e: Exception) -> ApiResponse:
    logger.warning("换物向量匹配不可用: %s", e)
    return ApiResponse(code=503, msg=f"换物向量匹配不可用: {e}", data=None)


@router.post("/reindex")
async def reindex(body: Optional[ReindexRequest] = None) -> ApiResponse:
    """增量同步换物双索引。body.tenant_id 指定单租户；缺省同步全部租户。"""
    try:
        if body is not None and body.tenant_id:
            count = await ev.service.reindex(body.tenant_id)
        else:
            count = await ev.service.reindex_all()
        return ApiResponse(data={"indexed": count})
    except ev.ExchangeVectorUnavailable as e:
        return _unavailable(e)


@router.post("/for-me")
async def for_me(body: ForMeRequest, request: Request) -> ApiResponse:
    """我的需求文本 ↔ 供给索引：want_texts 均值向量检索，[{itemId, score}]。"""
    tenant_id = tenant_id_from_request(request)
    if tenant_id <= 0:
        return ApiResponse(code=400, msg="缺少租户上下文", data=None)
    if not body.want_texts:
        return ApiResponse(data={"hits": []})
    try:
        hits = await ev.service.match_for_demand(tenant_id, body.want_texts, body.limit)
        return ApiResponse(data={"hits": hits})
    except ev.ExchangeVectorUnavailable as e:
        return _unavailable(e)


@router.get("/wanters")
async def wanters(request: Request, item_id: int, limit: int = 6) -> ApiResponse:
    """物品 ↔ 需求索引：返回需求侧 [{itemId, score}]（Java 侧反查命中物品的 owner）。"""
    tenant_id = tenant_id_from_request(request)
    if tenant_id <= 0:
        return ApiResponse(code=400, msg="缺少租户上下文", data=None)
    try:
        hits = await ev.service.match_wanters(tenant_id, item_id, limit)
        return ApiResponse(data={"hits": hits})
    except ev.ExchangeVectorUnavailable as e:
        return _unavailable(e)
