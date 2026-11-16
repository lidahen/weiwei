"""AI 商品发布辅助：文案生成 + 配图生成。

- /ai/assist/product-copy：model_type=LLM 的默认模型生成标题候选 + 描述
- /ai/assist/product-image：model_type=IMAGE 的默认模型同步生成一张配图
  （复用现有图像任务链路：建 AiImageTask → 同步执行 → 读回 result_url）

权限在 Java 代理层（sa-token）校验，本服务不做登录校验。
用量计费：接入 quota_service——事前检查每日配额（quota_ai_call_day，超额/过期返回
429 友好错误，对齐 Java AiQuotaChecker 语义），成功后事后计量落 ai_usage_log。
租户/用户身份沿用现有惯例：X-Tenant-Id / X-User-Id 请求头（Java 代理层透传），兼容请求体 userId。
"""

import logging
from functools import partial

from fastapi import APIRouter, Depends, Request
from langchain_openai import ChatOpenAI
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import async_session, get_db
from app.models.ai_image_task import AiImageTask
from app.models.ai_model import AiModel
from app.schemas.assist import (
    DailyReportData,
    DailyReportRequest,
    HomeWidgetData,
    HomeWidgetWeather,
    ProductCopyData,
    ProductCopyRequest,
    ProductImageData,
    ProductImageRequest,
    ProductRecognitionData,
    ProductRecognitionRequest,
    ProductValuationData,
    ProductValuationRequest,
    PublishPreviewData,
    PublishPreviewRequest,
)
from app.schemas.common import ApiResponse
from app.services.agnes_image import create_image_task_async, resolve_size
from app.services.daily_report import generate_daily_report
from app.services.home_widget import (
    WEEKDAY_NAMES,
    clean_city,
    generate_quotes,
    get_quotes,
    get_weather,
    now_cn,
    resolve_request_city,
)
from app.services.model_resolver import resolve_default_model, resolve_llm_model
from app.services.ip_geo import detect_city
from app.services.product_copywriting import build_image_prompt, generate_product_copy
from app.services.product_recognition import classify_category, run_recognition
from app.services.quota_service import (
    QuotaExceededException,
    check_and_count,
    log_usage,
    tenant_id_from_request,
    user_id_from_request,
)
from app.services.valuation import (
    L2_MIN_SAMPLES,
    compute_valuation,
    fetch_category_rows,
    fetch_onsale_prices,
    fetch_transactions,
    split_l2_samples,
)

router = APIRouter(prefix="/ai/assist", tags=["AI商品辅助"])

logger = logging.getLogger(__name__)


def _build_chat_llm(model: AiModel) -> ChatOpenAI:
    """文案/日报/激励语用的 LLM：构造统一走 model_resolver（超时与重试取 settings）。

    仅保留业务侧的温度偏好（创意文案 0.7）。
    """
    from app.services.model_resolver import build_chat_llm

    return build_chat_llm(model, temperature=0.7)


@router.post("/product-copy")
async def product_copy(request: Request, body: ProductCopyRequest, db: AsyncSession = Depends(get_db)):
    """根据商品信息生成候选标题与描述文案。"""
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    model = await resolve_llm_model(db)
    if not model:
        return ApiResponse(code=500, msg="未配置可用的AI文案模型，请联系管理员")

    llm = _build_chat_llm(model)
    try:
        titles, description = await generate_product_copy(
            llm,
            body.product_name,
            category=body.category,
            condition=body.condition,
            price=body.price,
            keywords=body.keywords,
        )
    except Exception as e:  # noqa: BLE001 —— 任何上游失败都转为友好错误
        logger.error("product copy generation failed: %s", e)
        return ApiResponse(code=500, msg="AI文案生成失败，请稍后重试")

    if not titles and not description:
        return ApiResponse(code=500, msg="AI文案生成失败，请稍后重试")

    # 事后计量：成功调用落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="product-copy",
                    model_id=model.model_id, model_name=model.model_name, task_type="text")

    data = ProductCopyData(
        titles=titles, description=description,
        model_id=model.model_id, model_name=model.model_name,
    )
    return ApiResponse(data=data.model_dump(by_alias=True))


@router.post("/product-image")
async def product_image(request: Request, body: ProductImageRequest, db: AsyncSession = Depends(get_db)):
    """根据商品信息同步生成一张商品配图，返回图片 URL。"""
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    # 与 /ai/image/generate 同一套解析（统一走 model_resolver，恒带 limit(1)）
    model = await resolve_default_model(db, "IMAGE")
    if not model:
        return ApiResponse(code=500, msg="未配置可用的AI配图模型，请联系管理员")

    prompt = build_image_prompt(
        body.product_name, category=body.category,
        condition=body.condition, keywords=body.keywords,
    )
    final_size = resolve_size(body.size, "1:1")

    task = AiImageTask(
        model_id=model.model_id,
        prompt=prompt,
        size=final_size,
        gen_type="text2img",
        response_format="url",
        status="queued",
        del_flag=0,
        create_by="ai-assist",
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)

    # 同步执行（复用现有任务执行器，含 Agnes/OpenAI images 协议分发与重试）
    try:
        await create_image_task_async(task.task_id)
    except Exception as e:  # noqa: BLE001
        logger.error("product image task %s execution error: %s", task.task_id, e)

    # 用新会话读取任务最终状态（同会话缓存/过期对象在 async 下不可靠）
    from app.database import async_session

    async with async_session() as read_session:
        result = await read_session.execute(
            select(AiImageTask).where(AiImageTask.task_id == task.task_id)
        )
        final_task = result.scalar_one_or_none()
    if not final_task or final_task.status != "completed" or not final_task.result_url:
        msg = (final_task.error_msg if final_task else None) or "AI配图生成失败，请稍后重试"
        return ApiResponse(code=500, msg=msg)

    # 事后计量：成功调用落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="product-image",
                    model_id=model.model_id, model_name=model.model_name, task_type="image")

    data = ProductImageData(
        task_id=final_task.task_id,
        image_url=final_task.result_url,
        model_id=model.model_id,
        model_name=model.model_name,
    )
    return ApiResponse(data=data.model_dump(by_alias=True))


@router.post("/daily-report")
async def daily_report(request: Request, body: DailyReportRequest, db: AsyncSession = Depends(get_db)):
    """根据聚合指标生成社区日报（LLM 文案）。

    调用方是 Java 侧（每日 07:00 定时任务 + 管理员手动触发），携带聚合指标 JSON。
    配额 feature=daily-report 计入 ai_usage_daily 单一账本（每租户每天至多 2 次：
    定时 + 一次手动覆盖，超限返回 429 由 Java 走模板兜底）。
    """
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    model = await resolve_llm_model(db)
    if not model:
        return ApiResponse(code=500, msg="未配置可用的AI文案模型，请联系管理员")

    llm = _build_chat_llm(model)
    try:
        title, content = await generate_daily_report(
            llm, body.stats, tenant_name=body.tenant_name or "", date=body.date or ""
        )
    except Exception as e:  # noqa: BLE001 —— 任何上游失败都转为友好错误，Java 侧兜模板
        logger.error("daily report generation failed: %s", e)
        return ApiResponse(code=500, msg="AI日报生成失败，请稍后重试")

    if not content:
        return ApiResponse(code=500, msg="AI日报生成失败，请稍后重试")

    # 事后计量：成功调用落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="daily-report",
                    model_id=model.model_id, model_name=model.model_name, task_type="text")

    data = DailyReportData(title=title, content=content)
    return ApiResponse(data=data.model_dump(by_alias=True))


def _build_vision_llm(model) -> ChatOpenAI:
    """识图/分类用 LLM：低温度（识别任务要稳定，不要发挥）。"""
    from app.config import settings

    return ChatOpenAI(
        base_url=(model.base_url or "").rstrip("/"),
        api_key=model.api_key or "sk-placeholder",
        model=model.model_name,
        temperature=0.2,
        timeout=settings.ai_llm_timeout,
        max_retries=settings.ai_llm_max_retries,
    )


@router.post("/product-recognition")
async def product_recognition(request: Request, body: ProductRecognitionRequest,
                              db: AsyncSession = Depends(get_db)):
    """AI 识图填单：从商品实拍图识别类目/标题/描述/成色，供发布页一键回填。

    需要 ai_model 配置 model_type='VISION' 的多模态模型；未配置时返回 500。
    """
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    vision_model = await resolve_default_model(db, "VISION")
    if not vision_model:
        return ApiResponse(code=500, msg="管理员未配置视觉识别模型，暂时无法识图填单")

    category_rows = await fetch_category_rows(db)
    llm = _build_vision_llm(vision_model)
    try:
        parsed = await run_recognition(llm, body.images, category_rows, hint=body.hint)
    except Exception as e:  # noqa: BLE001 —— 图片不可达/模型不支持多模态等都转友好错误
        logger.error("product recognition failed: %s", e)
        return ApiResponse(code=500, msg="AI 识图失败，请确认图片可访问后重试")

    if not any([parsed.get("title"), parsed.get("catL1Code"),
                parsed.get("description"), parsed.get("condition")]):
        return ApiResponse(code=500, msg="未能从图片中识别出商品信息，请手动填写")

    # 事后计量：成功调用落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="product-recognition",
                    model_id=vision_model.model_id, model_name=vision_model.model_name,
                    task_type="text")
    data = ProductRecognitionData(**parsed, model_id=vision_model.model_id,
                                  model_name=vision_model.model_name)
    return ApiResponse(data=data.model_dump(by_alias=True))


@router.post("/product-valuation")
async def product_valuation(request: Request, body: ProductValuationRequest,
                            db: AsyncSession = Depends(get_db)):
    """AI 估价：真实成交分位数 × 成色系数，价格数字全部来自业务库统计（不使用 LLM 编价）。

    类目未传时尝试 LLM 按标题分类兜底；分类失败/未配置 LLM 返回 400 引导先选类目。
    """
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    category_rows = await fetch_category_rows(db)
    valid_codes = {r["code"] for r in category_rows if r.get("level") == 1}

    cat_l1 = body.cat_l1 if body.cat_l1 in valid_codes else None
    used_llm_model = None
    if not cat_l1:
        llm_model = await resolve_default_model(db, "LLM")
        if not llm_model:
            return ApiResponse(code=400, msg="请先选择商品品类")
        used_llm_model = llm_model
        try:
            parsed = await classify_category(
                _build_vision_llm(llm_model), body.title, body.description, category_rows
            )
        except Exception as e:  # noqa: BLE001
            logger.warning("valuation category classify failed: %s", e)
            return ApiResponse(code=400, msg="未能识别商品品类，请先手动选择品类")
        cat_l1 = parsed.get("catL1Code")
        if not cat_l1:
            return ApiResponse(code=400, msg="未能识别商品品类，请先手动选择品类")

    if tenant_id and tenant_id > 0:
        rows = await fetch_transactions(db, tenant_id, cat_l1)
        l2_samples, _l1_rest = split_l2_samples(rows, body.cat_l2)
        tx_prices = l2_samples if len(l2_samples) >= L2_MIN_SAMPLES else [
            float(r["price"]) for r in rows
        ]
        ask_prices = await fetch_onsale_prices(db, tenant_id, cat_l1)
    else:
        # 平台视角直连调试（无租户上下文）：无业务数据可算，返回空样本说明
        tx_prices, ask_prices = [], []

    result = compute_valuation(tx_prices, ask_prices, body.condition)

    # 事后计量：成功调用落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="product-valuation",
                    model_id=used_llm_model.model_id if used_llm_model else None,
                    model_name=used_llm_model.model_name if used_llm_model else "stats",
                    task_type="text")
    result["model_name"] = used_llm_model.model_name if used_llm_model else "stats"
    data = ProductValuationData(**result)
    return ApiResponse(data=data.model_dump(by_alias=True))


@router.post("/publish-preview")
async def publish_preview(request: Request, body: PublishPreviewRequest,
                          db: AsyncSession = Depends(get_db)):
    """发布流水线（拍照即发布）：识图填单 → 统计估价 → 文案生成 一次串联。

    - 第 1 步识图：VISION 模型从实拍图识别类目/标题/描述/成色（失败则整单 500，
      与 /product-recognition 同口径）；
    - 第 2 步估价：用识图结果的类目/成色走统计估价（valuation.py 纯统计，无 LLM 编价；
      失败返回 valuation=null，不阻塞发布）；
    - 第 3 步文案：用识图结果的标题/类目/成色生成标题与描述（失败返回 titles=null，
      不阻塞发布；识图描述保留为 description，AI 描述单独放 description_ai）。

    配额：整单计 1 次（feature=publish-preview），三步共用不重复扣。
    """
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request, body)
    try:
        await check_and_count(db, tenant_id)
    except QuotaExceededException as e:
        return ApiResponse(code=429, msg=str(e))

    # ── 第 1 步：识图填单（失败整单失败，与 /product-recognition 同口径）──
    vision_model = await resolve_default_model(db, "VISION")
    if not vision_model:
        return ApiResponse(code=500, msg="管理员未配置视觉识别模型，暂时无法识图填单")

    category_rows = await fetch_category_rows(db)
    llm = _build_vision_llm(vision_model)
    try:
        parsed = await run_recognition(llm, body.images, category_rows, hint=body.hint)
    except Exception as e:  # noqa: BLE001
        logger.error("publish preview recognition failed: %s", e)
        return ApiResponse(code=500, msg="AI 识图失败，请确认图片可访问后重试")

    if not any([parsed.get("title"), parsed.get("catL1Code"),
                parsed.get("description"), parsed.get("condition")]):
        return ApiResponse(code=500, msg="未能从图片中识别出商品信息，请手动填写")

    title = parsed.get("title") or body.hint or ""
    cat_l1_code = parsed.get("catL1Code")
    cat_l2_code = parsed.get("catL2Code")
    condition = parsed.get("condition")

    # ── 第 2 步：统计估价（复用识图的类目/成色；失败降级 None，不阻塞）──
    valuation = None
    if tenant_id and tenant_id > 0 and cat_l1_code:
        try:
            rows = await fetch_transactions(db, tenant_id, cat_l1_code)
            l2_samples, _l1_rest = split_l2_samples(rows, cat_l2_code)
            tx_prices = l2_samples if len(l2_samples) >= L2_MIN_SAMPLES else [
                float(r["price"]) for r in rows
            ]
            ask_prices = await fetch_onsale_prices(db, tenant_id, cat_l1_code)
            valuation = compute_valuation(tx_prices, ask_prices, condition)
            valuation = ProductValuationData(**valuation)
        except Exception as e:  # noqa: BLE001
            logger.warning("publish preview valuation skipped: %s", e)

    # ── 第 3 步：文案生成（复用识图的标题/类目/成色；失败降级 None，不阻塞）──
    titles, description_ai = None, None
    copy_model = await resolve_llm_model(db)
    if copy_model and title:
        try:
            copy_llm = _build_chat_llm(copy_model)
            titles, description_ai = await generate_product_copy(
                copy_llm, title,
                category=parsed.get("catL1Name"),
                condition=condition,
                keywords=body.hint,
            )
        except Exception as e:  # noqa: BLE001
            logger.warning("publish preview copy skipped: %s", e)

    # 事后计量：整单成功落明细账（旁路，失败不影响响应）
    await log_usage(tenant_id, user_id=user_id, feature="publish-preview",
                    model_id=vision_model.model_id, model_name=vision_model.model_name,
                    task_type="text")

    data = PublishPreviewData(
        cat_l1_code=parsed.get("catL1Code"),
        cat_l1_name=parsed.get("catL1Name"),
        cat_l2_code=parsed.get("catL2Code"),
        cat_l2_name=parsed.get("catL2Name"),
        title=parsed.get("title"),
        description=parsed.get("description"),
        condition=condition,
        valuation=valuation,
        titles=titles or None,
        description_ai=description_ai or None,
        model_id=vision_model.model_id,
        model_name=vision_model.model_name,
    )
    return ApiResponse(data=data.model_dump(by_alias=True))


@router.get("/home-widget")
async def home_widget(
    request: Request,
    city: str = "",
    ip: str = "",
    db: AsyncSession = Depends(get_db),
):
    """首页小组件：天气 + 日期 + AI 激励语池。

    - 游客可见（Java 侧 @SaIgnore）：无租户上下文时激励语走内置模板池，不调 LLM；
    - 登录租户：LLM 生成 5 条池并按（租户×北京日期）缓存，配额超限/LLM 故障静默降级模板池，
      本接口永不返回 429（首页体验优先）；
    - 天气走 open-meteo（免费无 key），失败返回 weather=null，前端隐藏天气块；
    - 城市来源：city 参数优先；为空时按浏览器公网 IP 定位（前端经 api.ipify.org 取 IP，
      本地开发 localhost 场景服务端 IP 定位必然失效，故由浏览器侧取）；再失败回落深圳。
    """
    tenant_id = tenant_id_from_request(request)
    user_id = user_id_from_request(request)

    # 激励语 AI 链路仅对有租户上下文的请求开放（游客模板池，零成本）
    llm = None
    if tenant_id > 0:
        model = await resolve_llm_model(db)
        if model:
            llm = _build_chat_llm(model)

    # 城市：显式 city > IP 定位（公网 IP；内网/缺失时用服务端出口 IP 兜底，本地开发即用户城市）
    effective_city = (city or "").strip()
    if not effective_city:
        ip_city = await resolve_request_city(ip)
        if ip_city:
            logger.info("home_widget 城市 IP 定位: ip=%s -> %s", ip or "(出口)", ip_city)
            effective_city = ip_city

    weather = await get_weather(effective_city)

    # 激励语：缓存命中即秒回；未命中则后台生成，本次先返回模板池（source=pending），
    # 前端短暂后重拉即可拿到 AI 版本 —— 首页不为 LLM 等待。
    bg_runner = None
    if llm is not None:
        async def _bg_generate():
            # 请求级 db session 已随响应关闭，后台任务须自带会话
            async with async_session() as bg_db:
                await generate_quotes(
                    tenant_id,
                    llm,
                    weather=weather,
                    quota_check=partial(check_and_count, bg_db, tenant_id),
                    log_usage_fn=partial(log_usage, tenant_id, user_id=user_id),
                )

        bg_runner = _bg_generate

    quotes_result = await get_quotes(
        tenant_id,
        llm=llm,
        weather=weather,
        quota_check=partial(check_and_count, db, tenant_id) if llm else None,
        log_usage_fn=partial(log_usage, tenant_id, user_id=user_id) if llm else None,
        background=True,
        bg_runner=bg_runner,
    )

    today = now_cn()
    data = HomeWidgetData(
        city=(weather or {}).get("city") or clean_city(effective_city),
        date=today.strftime("%Y-%m-%d"),
        weekday=WEEKDAY_NAMES[today.weekday()],
        weather=HomeWidgetWeather(**weather) if weather else None,
        quotes=quotes_result["quotes"],
        source=quotes_result["source"],
    )
    return ApiResponse(data=data.model_dump(by_alias=True))


@router.get("/ip-city")
async def ip_city(request: Request):
    """IP 归属地城市识别（首页定位默认值，游客可见）。

    Java 代理层用 IpUtils.getIpAddr 解析 Caddy 反代链的真实客户端 IP，
    以 X-Client-Ip 头透传；Python 侧私网/查询失败一律返回 city=null（负缓存防重试），
    前端按「手动选择 > IP 定位 > 默认城市」回落 —— 定位失败不影响首页可用性。
    """
    return ApiResponse(data=await detect_city(request))
