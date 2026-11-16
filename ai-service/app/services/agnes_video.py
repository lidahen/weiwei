import asyncio
import json
import logging
from typing import Optional, Dict, Any
from urllib.parse import quote

import httpx
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import async_session
from app.models.ai_video_task import AiVideoTask
from app.models.ai_model import AiModel

logger = logging.getLogger(__name__)

AGNES_VIDEO_PATH = "/v1/videos"
AGNES_VIDEO_QUERY_PATH = "/agnesapi"

GEN_TYPE_TEXT2VID = "text2vid"
GEN_TYPE_IMG2VID = "img2vid"
GEN_TYPE_MULTI2VID = "multi2vid"
GEN_TYPE_KEYFRAMES = "keyframes"

# 注：原 DEFAULT_WIDTH/HEIGHT、MAX_NUM_FRAMES、MIN/MAX_FRAME_RATE 及
# normalize_num_frames / clamp_frame_rate 是旧版 Agnes Video v2.0 的形状所需
# （发 width/height/num_frames/frame_rate）。2.5 Flash 改用
# size="720P" + seconds，这些常量与函数已无引用，随重写一并删除。

RETRYABLE_STATUS_CODES = {502, 503, 504, 429}
MAX_RETRIES = 5
RETRY_BASE_DELAY = 10  # seconds


async def _transfer_result_to_oss(url: str, task_id: int) -> str:
    """Agnes 资源域名（platform-outputs.agnes-ai.space）存在 DNS 污染/可用性风险，
    任务完成即把成品视频转存自有 OSS；失败回退原始 URL（宁可有链接别失败）。"""
    if not url or "aliyuncs.com" in url:
        return url
    if not settings.oss_transfer_enabled or not (settings.oss_access_key or "").strip():
        return url
    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(connect=10.0, read=300.0, write=30.0, pool=30.0)
        ) as client:
            resp = await client.get(url)
            resp.raise_for_status()
        from app.services.creation.media_store import upload_bytes

        stored = await upload_bytes(resp.content, filename=f"video_{task_id}.mp4",
                                    prefix="ai-video/clip")
        logger.info("Task %s 视频已转存 OSS: %s", task_id, stored)
        return stored
    except Exception as exc:  # noqa: BLE001
        logger.warning("Task %s 视频转存 OSS 失败（回退原始 URL）: %s", task_id, exc)
        return url


def normalize_base_url(base_url: Optional[str]) -> str:
    """Normalize base URL by removing trailing slash."""
    if not base_url:
        return base_url or ""
    return base_url.rstrip("/")


# ── Agnes Video 2.5 Flash 参数契约（见《docs/ai/模型调用文档说明.md》）──
# 各家厂商参数不一样，这里只服务 Agnes 协议；
# senseaudio 走 _run_senseaudio_video_task，自己拼 content[]，互不影响。
AGNES_MODE_TEXT = "text"  # 纯文本生成
AGNES_MODE_KEYFRAME = "keyframe"  # 首帧 / 尾帧控制
AGNES_MODE_REFERENCE = "reference"  # 图片 / 音频参考生成
AGNES_VIDEO_SIZE = "720P"  # Flash 仅接受该值，其它值返回 400 size must be 720P
AGNES_SECONDS_MIN = 4
AGNES_SECONDS_MAX = 12
AGNES_SECONDS_DEFAULT = 5
AGNES_MAX_REFERENCE_IMAGES = 5  # Flash 上限
AGNES_MAX_REFERENCE_AUDIOS = 3  # Flash 上限（当前无音频输入字段，仅注释）
# 文档「视频尺寸与画幅」：size 固定 720P，输出尺寸由 aspect_ratio 决定
AGNES_ASPECT_RATIOS = (
    ("21:9", 21 / 9),
    ("16:9", 16 / 9),
    ("4:3", 4 / 3),
    ("1:1", 1.0),
    ("3:4", 3 / 4),
    ("9:16", 9 / 16),
)
# 各画幅对应的 720P 输出像素（前端展示用，与文档一致）
AGNES_ASPECT_RATIO_PIXELS = {
    "21:9": "1680x720",
    "16:9": "1280x704",
    "4:3": "960x720",
    "1:1": "720x720",
    "3:4": "720x960",
    "9:16": "720x1280",
}


def resolve_agnes_mode(gen_type: Optional[str], has_images: bool) -> str:
    """把项目的 gen_type 映射到 Agnes 的 mode。

    映射关系：
      text2vid              → text
      keyframes             → keyframe（用 first_frame / last_frame）
      img2vid / multi2vid   → reference（用 images[]）
    两种媒体模式如果**没有图片**，退化成 text —— 否则 keyframe/reference 缺必需媒体必然被 400。
    """
    if not has_images:
        return AGNES_MODE_TEXT
    if gen_type == GEN_TYPE_KEYFRAMES:
        return AGNES_MODE_KEYFRAME
    if gen_type in (GEN_TYPE_IMG2VID, GEN_TYPE_MULTI2VID):
        return AGNES_MODE_REFERENCE
    return AGNES_MODE_TEXT


def resolve_agnes_seconds(task: AiVideoTask) -> str:
    """时长（字符串，4~12 秒）。优先 duration，其次 num_frames / frame_rate。"""
    seconds: Optional[int] = None
    if task.duration:
        seconds = int(task.duration)
    elif task.num_frames and task.frame_rate:
        seconds = round(task.num_frames / task.frame_rate)
    if not seconds:
        seconds = AGNES_SECONDS_DEFAULT
    return str(max(AGNES_SECONDS_MIN, min(AGNES_SECONDS_MAX, seconds)))


def resolve_agnes_aspect_ratio(task: AiVideoTask) -> str:
    """把 width/height 归到最接近的受支持比例（缺省 16:9）。"""
    width, height = task.width, task.height
    if not width or not height or height <= 0:
        return "16:9"
    target = width / height
    return min(AGNES_ASPECT_RATIOS, key=lambda item: abs(item[1] - target))[0]


def parse_image_urls(input_images: Optional[str]) -> list:
    """input_images 解析为 URL 列表（兼容 JSON 数组与单个 URL 字符串）。"""
    if not input_images:
        return []
    try:
        node = json.loads(input_images)
    except (json.JSONDecodeError, TypeError):
        return [input_images] if isinstance(input_images, str) and input_images else []
    if isinstance(node, list):
        return [u for u in node if isinstance(u, str) and u]
    if isinstance(node, str) and node:
        return [node]
    return []


def build_video_request_body(
    task: AiVideoTask,
    model_name: str,
) -> Dict[str, Any]:
    """构造 Agnes Video 2.5 Flash 的请求体。

    ⚠️ 与旧版 v2.0 形状**不兼容**。2.5 Flash 要求（文档「公共参数」表）：
      model / prompt / mode(必填) / size="720P" / seconds("4"~"12") / aspect_ratio / seed / n
    各模式允许的媒体字段：
      text      → 不允许 first_frame / last_frame / images / audios / videos
      keyframe  → first_frame 与 last_frame 至少一个
      reference → images[]（≤5）或 audios[]（≤3）；Flash **不支持** videos

    因此**不再发送** negative_prompt / num_frames / frame_rate / width / height
    —— 这些是 v2.0 的字段，文档未列入，发了有被 400 的风险。
    （副作用：负向提示词对 Agnes 视频暂不生效。）
    """
    urls = parse_image_urls(task.input_images)
    mode = resolve_agnes_mode(task.gen_type, has_images=bool(urls))

    body: Dict[str, Any] = {
        "model": model_name,
        "prompt": task.prompt or "",
        "mode": mode,
        "size": AGNES_VIDEO_SIZE,
        "seconds": resolve_agnes_seconds(task),
        "aspect_ratio": resolve_agnes_aspect_ratio(task),
    }

    if mode == AGNES_MODE_KEYFRAME:
        body["first_frame"] = urls[0]
        if len(urls) > 1:
            body["last_frame"] = urls[1]
    elif mode == AGNES_MODE_REFERENCE:
        body["images"] = urls[:AGNES_MAX_REFERENCE_IMAGES]

    if task.seed is not None:
        body["seed"] = task.seed

    return body


async def _update_task_status(
    session: AsyncSession,
    task_id: int,
    status: Optional[str] = None,
    progress: Optional[int] = None,
    agnes_task_id: Optional[str] = None,
    agnes_video_id: Optional[str] = None,
    result_url: Optional[str] = None,
    video_seconds: Optional[str] = None,
    video_size: Optional[str] = None,
    error_msg: Optional[str] = None,
) -> None:
    """Update a video task's status fields in the database."""
    values: Dict[str, Any] = {}
    if status is not None:
        values["status"] = status
    if progress is not None:
        values["progress"] = progress
    if agnes_task_id is not None:
        values["agnes_task_id"] = agnes_task_id
    if agnes_video_id is not None:
        values["agnes_video_id"] = agnes_video_id
    if result_url is not None:
        values["result_url"] = result_url
    if video_seconds is not None:
        values["video_seconds"] = video_seconds
    if video_size is not None:
        values["video_size"] = video_size
    if error_msg is not None:
        values["error_msg"] = error_msg

    if values:
        await session.execute(
            update(AiVideoTask).where(AiVideoTask.task_id == task_id).values(**values)
        )
        await session.commit()


async def create_video_task_async(task_id: int) -> None:
    """Execute video generation task asynchronously with retry logic.

    Migrated from Java AgnesVideoService. Uses asyncio for background execution
    and httpx.AsyncClient for HTTP calls to the Agnes API.

    Flow:
    1. Load task and model from DB
    2. Build request body based on gen_type
    3. POST to Agnes /v1/videos with retry on 502/503/504/429
    4. Parse response to extract agnes_task_id, agnes_video_id, status, etc.
    5. Update DB with results
    """
    async with async_session() as session:
        # Load task
        result = await session.execute(
            select(AiVideoTask).where(AiVideoTask.task_id == task_id)
        )
        task: Optional[AiVideoTask] = result.scalar_one_or_none()
        if not task:
            logger.error("Video task %s not found", task_id)
            return

        # Load model configuration
        model_result = await session.execute(
            select(AiModel).where(
                AiModel.model_id == task.model_id,
                AiModel.del_flag == 0,
            )
        )
        model: Optional[AiModel] = model_result.scalar_one_or_none()
        if not model:
            await _update_task_status(
                session, task_id, status="failed", error_msg="模型配置不存在"
            )
            return

        # Normalize base_url and build request
        base_url = normalize_base_url(model.base_url)
        api_key = model.api_key or ""

        # ── 分发:senseaudio 走独立 vendor ──
        model_provider_norm = (model.model_provider or "").strip().lower()
        if model_provider_norm == "senseaudio":
            await _run_senseaudio_video_task(
                session=session,
                task_id=task_id,
                model=model,
            )
            return

        api_url = f"{base_url}{AGNES_VIDEO_PATH}"
        model_name = model.model_alias if model.model_alias else model.model_name

        request_body = build_video_request_body(task, model_name)

        logger.info(
            "Submitting video task %s to Agnes API, model: %s, url: %s",
            task_id,
            model_name,
            api_url,
        )

        # Execute with retry
        last_error_msg: Optional[str] = None
        is_retryable_failure = False

        for attempt in range(1, MAX_RETRIES + 1):
            try:
                headers: Dict[str, str] = {"Content-Type": "application/json"}
                if api_key:
                    headers["Authorization"] = f"Bearer {api_key}"

                async with httpx.AsyncClient(
                    timeout=httpx.Timeout(
                        connect=settings.agnes_connect_timeout,
                        read=settings.agnes_read_timeout,
                        write=30.0,
                        pool=30.0,
                    )
                ) as client:
                    response = await client.post(
                        api_url, json=request_body, headers=headers
                    )

                if response.status_code in RETRYABLE_STATUS_CODES:
                    is_retryable_failure = True
                    last_error_msg = (
                        "服务暂时不可用({}), 正在重试({}/{})...".format(
                            response.status_code, attempt, MAX_RETRIES
                        )
                    )
                    logger.warning(
                        "Video task %s attempt %d/%d: retryable status %d",
                        task_id,
                        attempt,
                        MAX_RETRIES,
                        response.status_code,
                    )
                    if attempt < MAX_RETRIES:
                        await _update_task_status(
                            session, task_id, status="retrying", error_msg=last_error_msg
                        )
                        delay = RETRY_BASE_DELAY * attempt
                        await asyncio.sleep(delay)
                    continue

                if response.status_code != 200:
                    last_error_msg = (
                        "Agnes API returned status {}: {}".format(
                            response.status_code, response.text[:200]
                        )
                    )
                    await _update_task_status(
                        session, task_id, status="failed", error_msg=last_error_msg
                    )
                    return

                # Success - parse response
                response_data = response.json()
                logger.info(
                    "Video task %s API response received: %s",
                    task_id,
                    str(response_data)[:500],
                )

                # Extract fields from response
                update_values: Dict[str, Any] = {}

                if "task_id" in response_data and response_data["task_id"] is not None:
                    update_values["agnes_task_id"] = str(response_data["task_id"])
                if "video_id" in response_data and response_data["video_id"] is not None:
                    update_values["agnes_video_id"] = str(response_data["video_id"])
                if "status" in response_data and response_data["status"] is not None:
                    update_values["status"] = str(response_data["status"])
                else:
                    update_values["status"] = "queued"
                if "seconds" in response_data and response_data["seconds"] is not None:
                    update_values["video_seconds"] = str(response_data["seconds"])
                if "size" in response_data and response_data["size"] is not None:
                    update_values["video_size"] = str(response_data["size"])
                # Agnes POST /v1/videos 立即返回的成品视频地址在 "url" 字段；
                # 提交时就拿到 result_url，避免后续轮询前这段时间为空。
                if "url" in response_data and response_data["url"] is not None:
                    update_values["result_url"] = await _transfer_result_to_oss(
                        str(response_data["url"]), task_id
                    )

                if update_values:
                    await session.execute(
                        update(AiVideoTask)
                        .where(AiVideoTask.task_id == task_id)
                        .values(**update_values)
                    )
                    await session.commit()

                logger.info(
                    "Video task %s created successfully, agnesTaskId: %s, agnesVideoId: %s",
                    task_id,
                    update_values.get("agnes_task_id"),
                    update_values.get("agnes_video_id"),
                )
                return

            except httpx.TimeoutException as exc:
                is_retryable_failure = True
                last_error_msg = "请求超时: {}".format(exc)
                logger.warning(
                    "Video task %s attempt %d/%d timed out: %s",
                    task_id,
                    attempt,
                    MAX_RETRIES,
                    exc,
                )
                if attempt < MAX_RETRIES:
                    await _update_task_status(
                        session, task_id, status="retrying", error_msg=last_error_msg
                    )
                    delay = RETRY_BASE_DELAY * attempt
                    await asyncio.sleep(delay)
                continue

            except httpx.HTTPError as exc:
                last_error_msg = "HTTP请求异常: {}".format(exc)
                logger.warning(
                    "Video task %s attempt %d/%d HTTP error: %s",
                    task_id,
                    attempt,
                    MAX_RETRIES,
                    exc,
                )
                if attempt < MAX_RETRIES:
                    await _update_task_status(
                        session, task_id, status="retrying", error_msg=last_error_msg
                    )
                    delay = RETRY_BASE_DELAY * attempt
                    await asyncio.sleep(delay)
                continue

            except Exception as exc:
                last_error_msg = "异步执行异常: {}".format(exc)
                msg = str(exc)
                if len(msg) > 500:
                    msg = msg[:500] + "..."
                logger.error(
                    "Video task %s unexpected error: %s", task_id, exc, exc_info=True
                )
                await _update_task_status(
                    session, task_id, status="failed", error_msg=last_error_msg
                )
                return

        # All retries exhausted
        if is_retryable_failure:
            final_error = "视频生成服务暂时不可用，请稍后再试（已重试{}次）".format(MAX_RETRIES)
        else:
            final_error = last_error_msg or "重试次数耗尽"
        await _update_task_status(
            session, task_id, status="failed", error_msg=final_error
        )


async def query_video_status(task: AiVideoTask, db: AsyncSession) -> AiVideoTask:
    """Query video generation task status from Agnes API.

    Migrated from Java AgnesVideoService.queryVideoStatus().
    Uses GET {base_url}/agnesapi?video_id={agnes_video_id}

    Updates the task object in-place with latest status, progress,
    result_url, and error_msg from the API response.
    """
    # Load model configuration
    model_result = await db.execute(
        select(AiModel).where(
            AiModel.model_id == task.model_id,
            AiModel.del_flag == 0,
        )
    )
    model: Optional[AiModel] = model_result.scalar_one_or_none()
    if not model:
        raise ValueError("Model not found for modelId: {}".format(task.model_id))

    base_url = normalize_base_url(model.base_url)
    api_key = model.api_key or ""

    # ── 分发:senseaudio 走独立 vendor ──
    model_provider_norm = (model.model_provider or "").strip().lower()
    if model_provider_norm == "senseaudio":
        return await _query_senseaudio_video_status(task, model, db)

    video_id = task.agnes_video_id

    if not video_id:
        raise ValueError("Agnes videoId is required for status query")

    # 文档要求：keyframe / reference 模式查询**必须带 model_name**，仅 text 模式可省。
    # 这里统一带上（对 text 模式无害），取值与创建任务时一致（alias 优先）。
    query_model_name = model.model_alias if model.model_alias else model.model_name
    query_url = "{}{}?video_id={}&model_name={}".format(
        base_url,
        AGNES_VIDEO_QUERY_PATH,
        video_id,
        quote(query_model_name or "", safe=""),
    )

    try:
        headers: Dict[str, str] = {}
        if api_key:
            headers["Authorization"] = "Bearer {}".format(api_key)

        async with httpx.AsyncClient(
            timeout=httpx.Timeout(
                connect=settings.agnes_connect_timeout,
                read=settings.agnes_read_timeout,
                write=30.0,
                pool=30.0,
            )
        ) as client:
            response = await client.get(query_url, headers=headers)

        if response.status_code != 200:
            logger.error(
                "Video status query failed for task %s: status %d, body: %s",
                task.task_id,
                response.status_code,
                response.text[:200],
            )
            return task

        response_data = response.json()

        if "status" in response_data and response_data["status"] is not None:
            task.status = str(response_data["status"])
        if "progress" in response_data and response_data["progress"] is not None:
            task.progress = int(response_data["progress"])
        if "seconds" in response_data and response_data["seconds"] is not None:
            task.video_seconds = str(response_data["seconds"])
        if "size" in response_data and response_data["size"] is not None:
            task.video_size = str(response_data["size"])

        # 官方文档：任务完成后成品视频地址在 metadata.url；兼容顶层 url，
        # "remixed_from_video_id" 仅用于 remix 类任务，兜底使用
        metadata = response_data.get("metadata") if isinstance(response_data.get("metadata"), dict) else {}
        if metadata.get("url"):
            task.result_url = str(metadata["url"])
        elif response_data.get("url"):
            task.result_url = str(response_data["url"])
        elif response_data.get("remixed_from_video_id"):
            task.result_url = str(response_data["remixed_from_video_id"])
        # 完成即转存自有 OSS（Agnes 资源域名存在 DNS 污染，浏览器直载裂图）
        if task.status == "completed" and task.result_url:
            task.result_url = await _transfer_result_to_oss(task.result_url, task.task_id)
        if response_data.get("error"):
            error_val = response_data["error"]
            if isinstance(error_val, str) and error_val.strip():
                task.error_msg = error_val
                task.status = "failed"
            elif isinstance(error_val, dict) and error_val.get("message"):
                task.error_msg = str(error_val["message"])
                task.status = "failed"

        logger.info(
            "Video task %s status queried: status=%s, progress=%s",
            task.task_id,
            task.status,
            task.progress,
        )

    except Exception as exc:
        logger.error(
            "Video task status query failed for task %s: %s", task.task_id, exc
        )
        task.error_msg = str(exc)[:500]

    return task


async def _run_senseaudio_video_task(
    *,
    session: AsyncSession,
    task_id: int,
    model: AiModel,
) -> None:
    """senseaudio 视频走 SenseaudioUniversalVendor, 不用 agnes 协议。

    senseaudio 视频端点:
      POST /v1/video/create
      GET /v1/video/status?id={task_id}
    """
    from app.services.music_vendor.senseaudio_universal import SenseaudioUniversalVendor

    api_key = model.api_key or ""
    if not api_key:
        await _update_task_status(
            session, task_id, status="failed",
            error_msg=f"模型 {model.model_name} 未配置 api_key",
        )
        return

    # 加载 task 提取参数
    task_row = await session.execute(
        select(AiVideoTask).where(AiVideoTask.task_id == task_id)
    )
    task = task_row.scalar_one_or_none()
    if not task:
        return

    # 构造 senseaudio content[]
    # 按 senseaudio 文档:
    #   - text2vid: content = [{type:text, text:...}]   不要带 image
    #   - img2vid / keyframes: content = [{type:text,text},{type:image,url,role:first_frame}]
    #   - multi2vid / ref2vid: content = [{type:text,text},{type:image,url,role:reference} x ≤9]
    content: list = []
    if task.prompt:
        content.append({"type": "text", "text": task.prompt})

    if task.input_images:
        import json as _json
        urls = None
        try:
            urls = _json.loads(task.input_images)
        except (ValueError, TypeError):
            urls = None
        # 全部用 reference 角色(ref2vid 模式支持 1~9 张图, 比 first_frame 更灵活)
        if isinstance(urls, list) and urls:
            for u in urls:
                content.append({"type": "image", "url": u, "role": "reference"})
        elif isinstance(urls, str) and urls:
            content.append({"type": "image", "url": urls, "role": "reference"})
        elif isinstance(task.input_images, str) and task.input_images:
            content.append({"type": "image", "url": task.input_images, "role": "reference"})

    # 解析 ratio
    ratio = "16:9"
    if task.width and task.height:
        # 简单按宽度判断比例
        if task.height > task.width:
            ratio = "9:16"
        elif task.width == task.height:
            ratio = "1:1"

    # 解析 resolution
    resolution = "720p"
    if task.width and task.height:
        if task.width >= 1920 or task.height >= 1920:
            resolution = "1080p"
        elif task.width >= 854 or task.height >= 480:
            resolution = "720p"
        else:
            resolution = "480p"

    # duration 字段 video_seconds / duration 默认 5; 用户传过来是 request.body 的 duration (秒)
    # video_seconds 是 ai-video / agnes 老字段, 默认 5; 优先用 request.body 的 duration
    duration = task.duration or task.video_seconds or 10
    try:
        duration = int(float(duration))
    except (ValueError, TypeError):
        duration = 10
    # senseaudio 视频时长上限 15 秒
    if duration > 15:
        duration = 15
    if duration < 4:
        duration = 4

    vendor = SenseaudioUniversalVendor(
        api_key=api_key,
        base_url=model.base_url or "https://api.senseaudio.cn",
        model_name=model.model_name,
        timeout=180,
    )

    try:
        await _update_task_status(session, task_id, status="processing")

        senseaudio_task_id = await vendor.create_video(
            content=content,
            resolution=resolution,
            ratio=ratio,
            duration=duration,
            watermark=False,
            # doubao-seedance 不支持 provider_specific.generate_audio, 留 None
            provider_specific=None,
        )
        # 存到 agnes_video_id 字段(复用 column)
        await session.execute(
            update(AiVideoTask)
            .where(AiVideoTask.task_id == task_id)
            .values(agnes_video_id=senseaudio_task_id)
        )
        await session.commit()
        logger.info("Video task %s submitted to senseaudio: %s", task_id, senseaudio_task_id)
    except Exception as exc:
        logger.warning("senseaudio video task %s submit failed: %s", task_id, exc)
        await _update_task_status(
            session, task_id, status="failed",
            error_msg=f"senseaudio 提交失败: {exc}",
        )


async def _query_senseaudio_video_status(task: AiVideoTask, model: AiModel, db: AsyncSession) -> AiVideoTask:
    """senseaudio 视频轮询 (GET /v1/video/status?id=...)"""
    from app.services.music_vendor.senseaudio_universal import SenseaudioUniversalVendor

    if not task.agnes_video_id:
        raise ValueError("senseaudio video task_id is empty")

    vendor = SenseaudioUniversalVendor(
        api_key=model.api_key or "",
        base_url=model.base_url or "https://api.senseaudio.cn",
        model_name=model.model_name,
        timeout=60,
    )

    result = await vendor.poll_video(task.agnes_video_id)
    status_map = {"pending": "processing", "processing": "processing", "completed": "completed", "failed": "failed"}
    new_status = status_map.get(result.get("status"), "processing")

    update_values = {
        "status": new_status,
        "progress": 100 if new_status == "completed" else (50 if new_status == "processing" else 0),
    }
    if result.get("video_url"):
        update_values["result_url"] = result["video_url"]
    if result.get("fail_reason"):
        update_values["error_msg"] = result["fail_reason"]

    await db.execute(
        update(AiVideoTask).where(AiVideoTask.task_id == task.task_id).values(**update_values)
    )
    await db.commit()

    # 刷新 task 对象
    await db.refresh(task)
    return task
