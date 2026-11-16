import asyncio
import json
import logging
import re
from typing import Optional, Dict, Any

import httpx
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import async_session
from app.models.ai_image_task import AiImageTask
from app.models.ai_model import AiModel
from app.services.openai_image import (
    API_FORMAT_AGNES,
    API_FORMAT_OPENAI_IMAGES,
    build_edit_form,
    build_generation_body,
    build_multipart_files,
    download_input_images,
    extract_openai_image_result,
    map_size_for_gpt_image,
    resolve_api_format,
)
from app.services.sensenova_image import (
    DEFAULT_SIZES as SENSENOVA_DEFAULT_SIZES,
    build_edits_body as build_sensenova_edits_body,
    build_generation_body as build_sensenova_generation_body,
    is_sensenova_provider,
    normalize_sensenova_size,
    parse_declared_sizes as parse_sensenova_declared_sizes,
)

logger = logging.getLogger(__name__)

RETRYABLE_STATUS_CODES = {502, 503, 504, 429, 520}
MAX_RETRIES = 5
RETRY_BASE_DELAY = 10

# 官方档位 × 宽高比 → 原生输出尺寸（docs/ai/模型调用文档说明.md 的输出尺寸参考表）。
# 用精确档位尺寸请求可避免 Agnes 二次归一化（如 1920x1080 被映射成 1312x736）。
SIZE_TABLE: Dict[str, Dict[str, str]] = {
    "1K": {
        "1:1": "1024x1024", "3:4": "864x1152", "4:3": "1152x864",
        "16:9": "1312x736", "9:16": "736x1312", "2:3": "832x1248",
        "3:2": "1248x832", "21:9": "1568x672",
    },
    "2K": {
        "1:1": "2048x2048", "3:4": "1728x2304", "4:3": "2304x1728",
        "16:9": "2624x1472", "9:16": "1472x2624", "2:3": "1664x2496",
        "3:2": "2496x1664", "21:9": "3136x1344",
    },
    "3K": {
        "1:1": "3072x3072", "3:4": "2592x3456", "4:3": "3456x2592",
        "16:9": "3936x2208", "9:16": "2208x3936", "2:3": "2496x3744",
        "3:2": "3744x2496", "21:9": "4704x2016",
    },
    "4K": {
        "1:1": "4096x4096", "3:4": "3456x4608", "4:3": "4608x3456",
        "16:9": "5248x2944", "9:16": "2944x5248", "2:3": "3328x4992",
        "3:2": "4992x3328", "21:9": "6272x2688",
    },
}
SIZE_TIERS = tuple(SIZE_TABLE.keys())
DEFAULT_RATIO = "1:1"
_EXACT_SIZE_RE = re.compile(r"^(\d{2,5})x(\d{2,5})$", re.IGNORECASE)


def resolve_size(size: Optional[str], ratio: Optional[str] = None) -> str:
    """把请求里的 size（档位 1K/2K 或精确 WxH）+ ratio 解析为发给 Agnes 的最终尺寸。

    - "2K" + "16:9" → "2624x1472"（官方档位表原生尺寸）
    - "1024x768" → 原样（历史精确尺寸写法，官方兼容）
    - 无法识别 → 默认 "1024x1024"
    """
    s = (size or "").strip().upper()
    if s in SIZE_TABLE:
        tier = SIZE_TABLE[s]
        r = (ratio or DEFAULT_RATIO).strip()
        return tier.get(r, tier[DEFAULT_RATIO])
    m = _EXACT_SIZE_RE.match(s)
    if m and int(m.group(1)) >= 64 and int(m.group(2)) >= 64:
        return f"{m.group(1)}x{m.group(2)}"
    return "1024x1024"


def build_request_body(
    model_name: str,
    prompt: str,
    size: str,
    gen_type: str,
    input_image: Optional[str],
    response_format: str,
) -> Dict[str, Any]:
    """Build the request body for the Agnes image generation API.

    Migrated from Java AgnesImageService.buildRequestBody().

    - text2img: 纯文生图
    - img2img: 单张参考图（extra_body.image）
    - multi2img: 多图合成（extra_body.image 数组，input_image 为 JSON 数组字符串或单 URL）
    """
    body: Dict[str, Any] = {
        "model": model_name,
        "prompt": prompt,
        "size": size,
    }

    if gen_type == "img2img":
        body["extra_body"] = _image_extra_body(response_format, [input_image] if input_image else [])
    elif gen_type == "multi2img":
        images = _parse_image_list(input_image)
        if images:
            body["extra_body"] = _image_extra_body(response_format, images)

    return body


def _image_extra_body(response_format: str, images: list) -> Dict[str, Any]:
    """extra_body 组装：URL 输出用 response_format，Base64 输出用 return_base64 语义。

    官方要求 response_format 必须放在 extra_body 内，不能放请求体顶层。
    """
    if response_format == "b64_json":
        return {"response_format": "b64_json", "image": images}
    return {"response_format": "url", "image": images}


def _parse_image_list(input_image: Optional[str]) -> list:
    """input_image 兼容单 URL 与 JSON 数组字符串两种形态。"""
    if not input_image:
        return []
    trimmed = input_image.strip()
    if trimmed.startswith("["):
        try:
            node = json.loads(trimmed)
            if isinstance(node, list):
                return [str(u) for u in node if u]
        except json.JSONDecodeError:
            logger.warning("multi2img input_image JSON 解析失败，按单图处理")
    return [trimmed]


def extract_image_result(response_data: Dict[str, Any], response_format: str) -> Optional[str]:
    """Extract image URL or base64 data from the Agnes API response.

    Migrated from Java AgnesImageService.extractImageResult().
    """
    try:
        data_list = response_data.get("data", [])
        if not data_list:
            return None
        first_item = data_list[0]
        if response_format == "url":
            return first_item.get("url")
        else:
            return first_item.get("b64_json")
    except (IndexError, KeyError, TypeError):
        return None


async def _update_task_status(
    session: AsyncSession,
    task_id: int,
    status: str,
    result_url: Optional[str] = None,
    error_msg: Optional[str] = None,
) -> None:
    """Update an image task's status fields in the database."""
    values: Dict[str, Any] = {"status": status}
    if result_url is not None:
        values["result_url"] = result_url
    if error_msg is not None:
        values["error_msg"] = error_msg
    await session.execute(
        update(AiImageTask).where(AiImageTask.task_id == task_id).values(**values)
    )
    await session.commit()


async def create_image_task_async(task_id: int) -> None:
    """Execute image generation task asynchronously with retry logic.

    Migrated from Java AgnesImageService. Uses asyncio for background execution
    and httpx.AsyncClient for HTTP calls to the Agnes API.
    """
    async with async_session() as session:
        # Load task and model configuration
        result = await session.execute(
            select(AiImageTask).where(AiImageTask.task_id == task_id)
        )
        task: Optional[AiImageTask] = result.scalar_one_or_none()
        if not task:
            logger.error("Image task %s not found", task_id)
            return

        model_result = await session.execute(
            select(AiModel).where(
                AiModel.model_id == task.model_id,
                AiModel.del_flag == 0,
            )
        )
        model: Optional[AiModel] = model_result.scalar_one_or_none()
        if not model:
            await _update_task_status(
                session, task_id, "failed", error_msg="模型配置不存在"
            )
            return

        # Normalize base_url (remove trailing slash)
        base_url = (model.base_url or "").rstrip("/")
        api_key = model.api_key or ""

        # 协议分发：model_provider 显式声明为 OpenAI images 协议（如 OpenOx gpt-image-2）
        # 的模型走新适配；其余（历史 Agnes 模型，含默认 model_provider="openai"）保持原行为
        api_format = resolve_api_format(model)
        provider_label = "Agnes" if api_format == API_FORMAT_AGNES else "图片服务"

        # 尺寸归一：档位/精确尺寸统一解析为官方原生尺寸，避免 Agnes 二次归一化；
        # OpenAI images 协议再钳制到 gpt-image 系列约束（16 倍数/最长边/像素范围/比例）
        final_size = resolve_size(task.size)
        if api_format == API_FORMAT_OPENAI_IMAGES:
            final_size = map_size_for_gpt_image(final_size)

        # 风格标签追加到提示词末尾（图片接口无独立风格参数）
        prompt = task.prompt
        if task.style and task.style not in prompt:
            prompt = f"{prompt}, {task.style}"

        # ── senseaudio 走独立 vendor(必须在 prompt 定义后, 因为要用 prompt) ──
        model_provider_norm = (model.model_provider or "").strip().lower()
        if model_provider_norm == "senseaudio":
            await _run_senseaudio_image_task(
                session=session,
                task_id=task_id,
                model=model,
                prompt=prompt,
                final_size=final_size,
                gen_type=task.gen_type or "text2img",
                input_image=task.input_image,
            )
            return

        # ── SenseNova(商汤日日新)走独立协议: /v1/images/generations|edits ──
        if is_sensenova_provider(model):
            await _run_sensenova_image_task(
                session=session,
                task_id=task_id,
                model=model,
                prompt=prompt,
                final_size=final_size,
                gen_type=task.gen_type or "text2img",
                input_image=task.input_image,
            )
            return

        response_format = task.response_format or "url"
        gen_type = task.gen_type or "text2img"

        # 请求在重试前构建完毕：json_body（generations）或 form_data+multipart_files（edits）
        api_url = f"{base_url}/v1/images/generations"
        json_body: Optional[Dict[str, Any]] = None
        form_data: Optional[Dict[str, str]] = None
        multipart_files: Optional[list] = None

        if api_format == API_FORMAT_OPENAI_IMAGES:
            if gen_type in ("img2img", "multi2img"):
                # OpenAI images 协议的图生图/多图合成走 edits 端点（multipart），
                # 参考图不接受 URL，需下载后以文件上传
                image_urls = _parse_image_list(task.input_image)
                if not image_urls:
                    await _update_task_status(
                        session, task_id, "failed", error_msg="图生图任务缺少参考图"
                    )
                    return
                try:
                    downloaded = await download_input_images(image_urls)
                except (httpx.HTTPError, ValueError) as exc:
                    logger.warning("Task %s 参考图下载失败: %s", task_id, exc)
                    await _update_task_status(
                        session, task_id, "failed", error_msg=f"参考图下载失败: {exc}"
                    )
                    return
                api_url = f"{base_url}/v1/images/edits"
                form_data = build_edit_form(
                    model_name=model.model_name,
                    prompt=prompt,
                    size=final_size,
                    response_format=response_format,
                )
                multipart_files = build_multipart_files(downloaded)
            else:
                json_body = build_generation_body(
                    model_name=model.model_name,
                    prompt=prompt,
                    size=final_size,
                    response_format=response_format,
                )
        else:
            json_body = build_request_body(
                model_name=model.model_name,
                prompt=prompt,
                size=final_size,
                gen_type=gen_type,
                input_image=task.input_image,
                response_format=response_format,
            )

        # Execute with retry
        last_error_msg: Optional[str] = None
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                headers: Dict[str, str] = {}
                if api_key:
                    headers["Authorization"] = f"Bearer {api_key}"
                # multipart 请求不能手动指定 Content-Type（httpx 需自行写入 boundary）
                if json_body is not None:
                    headers["Content-Type"] = "application/json"

                async with httpx.AsyncClient(
                    timeout=httpx.Timeout(
                        connect=settings.agnes_connect_timeout,
                        read=settings.agnes_read_timeout,
                        write=30.0,
                        pool=30.0,
                    )
                ) as client:
                    if json_body is not None:
                        response = await client.post(
                            api_url, json=json_body, headers=headers
                        )
                    else:
                        response = await client.post(
                            api_url, data=form_data, files=multipart_files, headers=headers
                        )

                if response.status_code in RETRYABLE_STATUS_CODES:
                    last_error_msg = (
                        f"{provider_label} API returned retryable status {response.status_code}: "
                        f"{response.text[:200]}"
                    )
                    logger.warning(
                        "Task %s attempt %d/%d: %s",
                        task_id,
                        attempt,
                        MAX_RETRIES,
                        last_error_msg,
                    )
                    if attempt < MAX_RETRIES:
                        await _update_task_status(
                            session, task_id, "retrying"
                        )
                        delay = RETRY_BASE_DELAY * attempt
                        await asyncio.sleep(delay)
                    continue

                if response.status_code != 200:
                    last_error_msg = (
                        f"{provider_label} API returned status {response.status_code}: "
                        f"{response.text[:200]}"
                    )
                    await _update_task_status(
                        session, task_id, "failed", error_msg=last_error_msg
                    )
                    return

                # Success - parse response
                response_data = response.json()
                if api_format == API_FORMAT_OPENAI_IMAGES:
                    image_result, is_b64 = extract_openai_image_result(response_data)
                    if image_result and is_b64:
                        # 部分中转对 gpt-image 系列只回 Base64；result_url 列为
                        # VARCHAR(500)，Base64 无法落库，直接失败并给出明确提示
                        await _update_task_status(
                            session,
                            task_id,
                            "failed",
                            error_msg=(
                                "服务商仅返回 Base64 图片数据，无法作为图片 URL 保存，"
                                "请确认 response_format=url 已生效或更换服务商"
                            ),
                        )
                        return
                else:
                    image_result = extract_image_result(response_data, response_format)

                if not image_result:
                    await _update_task_status(
                        session,
                        task_id,
                        "failed",
                        error_msg="响应中未找到图片数据",
                    )
                    return

                # Agnes 图片域名（platform-outputs.agnes-ai.space）存在 DNS 污染/可用性风险
                # （浏览器直载裂图，2026-10-03 实测），完成即转存自有 OSS 落永久 URL；
                # 转存失败回退原始 URL（宁可有链接别失败）
                stored = image_result
                if settings.oss_transfer_enabled and (settings.oss_access_key or "").strip():
                    from app.services.oss_transfer import transfer_url_to_oss

                    stored = await transfer_url_to_oss(image_result, prefix="ai-image") or image_result
                await _update_task_status(
                    session,
                    task_id,
                    "completed",
                    result_url=stored,
                )
                return

            except httpx.TimeoutException as exc:
                last_error_msg = f"请求超时: {exc}"
                logger.warning(
                    "Task %s attempt %d/%d timed out: %s",
                    task_id,
                    attempt,
                    MAX_RETRIES,
                    exc,
                )
                if attempt < MAX_RETRIES:
                    await _update_task_status(
                        session, task_id, "retrying"
                    )
                    delay = RETRY_BASE_DELAY * attempt
                    await asyncio.sleep(delay)
                continue

            except httpx.HTTPError as exc:
                last_error_msg = f"HTTP请求异常: {exc}"
                logger.warning(
                    "Task %s attempt %d/%d HTTP error: %s",
                    task_id,
                    attempt,
                    MAX_RETRIES,
                    exc,
                )
                if attempt < MAX_RETRIES:
                    await _update_task_status(
                        session, task_id, "retrying"
                    )
                    delay = RETRY_BASE_DELAY * attempt
                    await asyncio.sleep(delay)
                continue

        # All retries exhausted
        await _update_task_status(
            session,
            task_id,
            "failed",
            error_msg=last_error_msg or "重试次数耗尽",
        )


async def _run_senseaudio_image_task(
    *,
    session: AsyncSession,
    task_id: int,
    model: AiModel,
    prompt: str,
    final_size: str,
    gen_type: str = "text2img",
    input_image: Optional[str] = None,
) -> None:
    """senseaudio 走 SenseaudioUniversalVendor, 不用 agnes 协议。

    senseaudio 图生图: 把参考图作为 `reference` 字段(URL)传给 /v1/image/sync。
    当前只支持 text2img; img2img/multi2img 暂不支持(任务直接报失败)。
    """
    from app.services.music_vendor.senseaudio_universal import (
        SenseaudioRejectedError,
        SenseaudioUniversalVendor,
        normalize_image_size,
        parse_supported_sizes,
    )

    api_key = model.api_key or ""
    if not api_key:
        await _update_task_status(
            session, task_id, "failed",
            error_msg=f"模型 {model.model_name} 未配置 api_key",
        )
        return

    # 尺寸归一：Agnes 档位表（含 4:3/3:4/21:9 等）远宽于商汤实际支持的尺寸，
    # 直接透传会踩上游 400。这里按模型声明的 sizes 收敛到最近档位。
    sizes = parse_supported_sizes(model.ext_param)
    normalized_size = normalize_image_size(final_size, sizes)
    if normalized_size != final_size:
        logger.info(
            "Task %s senseaudio 尺寸归一: %s → %s（模型支持 %s）",
            task_id, final_size, normalized_size, sizes,
        )
    final_size = normalized_size

    vendor = SenseaudioUniversalVendor(
        api_key=api_key,
        base_url=model.base_url or "https://api.senseaudio.cn",
        model_name=model.model_name,
        timeout=180,
    )

    reference: Optional[str] = None
    if gen_type in ("img2img", "multi2img") and input_image:
        urls = _parse_image_list(input_image)
        reference = urls[0] if urls else None

    try:
        await _update_task_status(session, task_id, "processing")

        if gen_type in ("img2img", "multi2img"):
            result = await vendor.image_generate(
                prompt=prompt,
                size=final_size,
                reference=reference,
            )
        else:
            result = await vendor.image_generate(
                prompt=prompt,
                size=final_size,
            )

        image_url = result.get("url")
        if not image_url:
            raise ValueError(f"senseaudio 返回空 url: {result}")

        await _update_task_status(
            session, task_id, "completed",
            result_url=image_url,
        )
        logger.info("Task %s completed (senseaudio): %s", task_id, image_url)

    except SenseaudioRejectedError as exc:
        # 内容审核/参数被拒：文案已自带可操作指引，不再套一层前缀
        logger.warning("senseaudio image task %s rejected: %s", task_id, exc)
        await _update_task_status(session, task_id, "failed", error_msg=str(exc))
    except Exception as exc:
        logger.warning("senseaudio image task %s failed: %s", task_id, exc)
        await _update_task_status(
            session, task_id, "failed",
            error_msg=f"senseaudio 调用失败: {exc}",
        )


async def _run_sensenova_image_task(
    *,
    session: AsyncSession,
    task_id: int,
    model: AiModel,
    prompt: str,
    final_size: str,
    gen_type: str = "text2img",
    input_image: Optional[str] = None,
) -> None:
    """SenseNova（商汤日日新 / platform.sensenova.cn）图片生成。

    - 文生图 → POST {base}/v1/images/generations（JSON）
    - 图生图/多图合成 → POST {base}/v1/images/edits（JSON，参考图字段 images[].image_url，最多 5 张）
    - size 必须为 32 的倍数、单边 512~4096、长宽比 ≤3:1，先按模型声明白名单就近归一
    - 固定请求 response_format=url：ai_image_task.result_url 只能存 URL，Base64 无法落库
    """
    from app.services.music_vendor.senseaudio_universal import _prepare_reference

    api_key = model.api_key or ""
    if not api_key:
        await _update_task_status(
            session, task_id, "failed",
            error_msg=f"模型 {model.model_name} 未配置 api_key",
        )
        return

    base_url = (model.base_url or "https://token.sensenova.cn").rstrip("/")
    declared = parse_sensenova_declared_sizes(model.ext_param) or list(SENSENOVA_DEFAULT_SIZES)
    normalized_size = normalize_sensenova_size(final_size, declared)
    if normalized_size != final_size:
        logger.info(
            "Task %s SenseNova 尺寸归一: %s → %s（模型支持 %s）",
            task_id, final_size, normalized_size, declared,
        )

    if gen_type in ("img2img", "multi2img") and input_image:
        # 参考图：OSS 链接可能签名过期，复用商汤侧的「下载 + 压缩 + data URL」预处理
        # （接口本身也接受公网 URL，预处理失败会自动退回原 URL）
        urls = _parse_image_list(input_image)[:5]
        prepared = [await _prepare_reference(u) for u in urls]
        api_url = f"{base_url}/v1/images/edits"
        json_body = build_sensenova_edits_body(
            model_name=model.model_name,
            prompt=prompt,
            image_urls=prepared,
            size=normalized_size,
            response_format="url",
        )
    else:
        api_url = f"{base_url}/v1/images/generations"
        json_body = build_sensenova_generation_body(
            model_name=model.model_name,
            prompt=prompt,
            size=normalized_size,
            response_format="url",
        )

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    last_error_msg: Optional[str] = None

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            await _update_task_status(session, task_id, "processing")

            async with httpx.AsyncClient(
                timeout=httpx.Timeout(
                    connect=settings.agnes_connect_timeout,
                    read=settings.agnes_read_timeout,
                    write=60.0,
                    pool=30.0,
                )
            ) as client:
                response = await client.post(api_url, json=json_body, headers=headers)

            if response.status_code in RETRYABLE_STATUS_CODES:
                last_error_msg = (
                    f"SenseNova 返回可重试状态 {response.status_code}: {response.text[:200]}"
                )
                logger.warning(
                    "Task %s attempt %d/%d: %s", task_id, attempt, MAX_RETRIES, last_error_msg
                )
                if attempt < MAX_RETRIES:
                    await _update_task_status(session, task_id, "retrying")
                    await asyncio.sleep(RETRY_BASE_DELAY * attempt)
                continue

            if response.status_code != 200:
                # 400 多为参数/内容问题，重试无意义，直接把上游报文给用户看
                await _update_task_status(
                    session, task_id, "failed",
                    error_msg=(
                        f"SenseNova 调用失败(status={response.status_code}): "
                        f"{response.text[:300]}"
                    ),
                )
                return

            image_result, is_b64 = extract_openai_image_result(response.json())
            if image_result and is_b64:
                await _update_task_status(
                    session, task_id, "failed",
                    error_msg=(
                        "SenseNova 仅返回 Base64 图片数据，无法作为图片 URL 保存，"
                        "请确认 response_format=url 已生效"
                    ),
                )
                return
            if not image_result:
                await _update_task_status(
                    session, task_id, "failed",
                    error_msg="SenseNova 响应中未找到图片数据",
                )
                return

            # SenseNova 的 response_format=url 是 24h 过期的预签名 URL（2026-09-25 实测
            # task 64/72 已 403），完成时先转存自有 OSS 落库永久链接；转存失败回退原始 URL。
            final_url = image_result
            if settings.oss_transfer_enabled and (settings.oss_access_key or "").strip():
                from app.services.oss_transfer import transfer_url_to_oss

                transferred = await transfer_url_to_oss(image_result)
                if transferred:
                    final_url = transferred
                    logger.info("Task %s SenseNova 图片已转存 OSS: %s", task_id, transferred)
                else:
                    logger.warning(
                        "Task %s SenseNova 图片转存失败，落库临时 URL（24h 后失效）",
                        task_id,
                    )

            await _update_task_status(
                session, task_id, "completed", result_url=final_url
            )
            logger.info(
                "Task %s completed (SenseNova %s): %s",
                task_id, model.model_name, final_url,
            )
            return

        except httpx.TimeoutException as exc:
            last_error_msg = f"请求超时: {exc}"
            logger.warning("Task %s attempt %d/%d timeout: %s", task_id, attempt, MAX_RETRIES, exc)
            if attempt < MAX_RETRIES:
                await _update_task_status(session, task_id, "retrying")
                await asyncio.sleep(RETRY_BASE_DELAY * attempt)
            continue
        except httpx.HTTPError as exc:
            last_error_msg = f"HTTP请求异常: {exc}"
            logger.warning("Task %s attempt %d/%d HTTP error: %s", task_id, attempt, MAX_RETRIES, exc)
            if attempt < MAX_RETRIES:
                await _update_task_status(session, task_id, "retrying")
                await asyncio.sleep(RETRY_BASE_DELAY * attempt)
            continue

    await _update_task_status(
        session, task_id, "failed", error_msg=last_error_msg or "重试次数耗尽"
    )
