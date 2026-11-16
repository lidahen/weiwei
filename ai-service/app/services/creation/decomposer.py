"""爆款拆解：链接/上传视频 → 下载 → ASR + 切镜 + 抽帧 → LLM 结构分析 → 模板入库。

模板只存结构与镜头语言（钩子/节奏/分镜骨架），不存素材与文案——复刻必须真二创。
"""
from __future__ import annotations

import asyncio
import json
import logging
from pathlib import Path
from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.ai_viral_template import AiViralTemplate
from app.services.creation.media_store import (
    CreationError, cleanup_work_dir, upload_file, work_dir,
)
from app.services.creation import video_ingest
from app.services.model_resolver import build_chat_llm, resolve_llm_model

logger = logging.getLogger(__name__)

_DECOMPOSE_SYSTEM = """你是抖音爆款短视频拆解专家。分析给定素材（语音转写 + 镜头切分 + 关键帧），\
输出这份视频的"可复用结构模板"，供后续以全新内容套用重拍。

严格输出以下 JSON（不要输出任何其他文字）：
{
  "title": "原片主题概括（≤20字）",
  "hook": {"type": "悬念|冲突|反常识|福利|身份共鸣|结果前置 中选一",
            "pattern": "钩子的话术套路描述（不抄原句，描述其结构，如'先抛极端结果再留悬念'）",
            "visual": "首镜画面特征（景别/运动/文字条）"},
  "shots": [
    {"role": "hook|body|proof|cta 之一",
     "duration_ratio": "该镜时长占比 0-1 的小数",
     "visual_type": "口播中景|特写演示|B-roll混剪|图文卡|对比分屏 中选一",
     "lens": "镜头语言（推/拉/摇/固定/手持）",
     "transition": "cut|fade|whip 中选一"}
  ],
  "rhythm": "叙事节奏描述（如'前3秒高密度信息，中段两问一答，结尾快促CTA'）",
  "visual_style": "整体视觉风格（色调/字幕形态/BGM 情绪，一段话）",
  "cta": "结尾行动号召的结构描述"
}

要求：
- shots 数量与实际切分镜头数量基本对应（3-8 镜）；duration_ratio 合计约等于 1
- 只描述结构与套路，禁止照抄原文案句子
- 必须是合法 JSON，字段齐全"""


def _extract_json(text: str) -> Optional[dict]:
    """从 LLM 输出中稳健提取 JSON 对象（容忍 ```json 围栏与前后杂文）。"""
    text = text.strip()
    if "```" in text:
        for seg in text.split("```"):
            seg = seg.strip()
            if seg.startswith("json"):
                seg = seg[4:]
            if seg.startswith("{"):
                text = seg
                break
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None


async def run_decompose(template_id: int, tenant_id: int = 0, user_id: str = "") -> None:
    """后台执行拆解（由路由创建 analyzing 状态的模板行后调度）。"""
    from app.database import async_session

    wdir = work_dir(f"template_{template_id}")
    async with async_session() as db:
        row = (await db.execute(
            select(AiViralTemplate).where(AiViralTemplate.template_id == template_id)
        )).scalar_one_or_none()
        if row is None:
            return
        try:
            await _decompose(row, db, wdir)
            from app.services.quota_service import log_usage

            await log_usage(tenant_id, user_id=user_id, feature="creation-decompose")
        except Exception as exc:  # noqa: BLE001 —— 落库失败态
            logger.error("拆解失败 template=%s: %s", template_id, exc, exc_info=True)
            row.status = "failed"
            row.error_msg = str(exc)[:2000]
            await db.commit()


async def _decompose(row: AiViralTemplate, db: AsyncSession, wdir) -> None:
    # 1. 获取视频：source_url（链接或上传后的本地媒体 URL）
    video_path: Optional[Path] = None
    if row.source_url:
        if row.source_url.startswith("/ai/creation/media/"):
            # 手动上传的本地文件，直接取盘上路径（拷贝放线程池，大文件不阻塞事件循环）
            import shutil

            from app.services.creation.media_store import local_path_of

            src = local_path_of(row.source_url)
            if not src.exists():
                raise CreationError("上传的源文件已丢失，请重新上传")
            video_path = wdir / f"source{src.suffix or '.mp4'}"
            await asyncio.to_thread(shutil.copyfile, src, video_path)
            row.title = row.title or (src.stem or "上传视频")[:200]
        else:
            video_path, title = await video_ingest.download_video(row.source_url, wdir)
            row.title = (title or "")[:200]
            # 原片转储持久化（失败不阻断）
            try:
                stored = await upload_file(video_path, prefix="ai-creation/source")
                row.source_video_url = stored
            except Exception as exc:  # noqa: BLE001
                logger.warning("原片转储失败: %s", exc)
    else:
        raise CreationError("缺少视频来源（source_url 或上传文件）")

    # 2. ASR 转写（无声/未配置则空串，退化为纯视觉拆解）
    row.asr_text = await video_ingest.transcribe(video_path, wdir)

    # 3. 切镜
    scenes = await video_ingest.detect_scenes(video_path)

    # 4. 抽帧并转存公开 URL（本地 URL 无法作为 LLM 视觉输入，OSS 未配置时直接跳过省 IO）
    frame_urls: list[str] = []
    if (settings.oss_access_key or "").strip():
        try:
            frames = await video_ingest.extract_frames(video_path, wdir)
            for f in frames:
                url = await upload_file(f, prefix="ai-creation/frame")
                if url.startswith("http"):
                    frame_urls.append(url)
        except Exception as exc:  # noqa: BLE001
            logger.warning("抽帧/转存失败（退化为纯文本拆解）: %s", exc)
    else:
        logger.info("OSS 未配置，跳过关键帧抽帧（退化为纯文本拆解）")

    # 5. LLM 结构分析
    llm_model = await resolve_llm_model(db)
    llm = build_chat_llm(llm_model, timeout=120)
    from langchain_core.messages import HumanMessage, SystemMessage

    duration_total = scenes[-1]["end"] if scenes else 0
    user_parts = [
        f"【语音转写】\n{row.asr_text or '（无语音内容）'}",
        f"【镜头切分】共 {len(scenes)} 镜\n" + "\n".join(
            f"镜{s['index']}: {s['start']:.1f}s-{s['end']:.1f}s（{s['duration']:.1f}s）" for s in scenes
        ) if scenes else "【镜头切分】未检出明显切换，按整片处理",
    ]
    if frame_urls:
        content: list = [{"type": "text", "text": "\n\n".join(user_parts) +
                          f"\n\n【关键帧】附 {len(frame_urls)} 张按时间顺序排列"}]
        for u in frame_urls:
            content.append({"type": "image_url", "image_url": {"url": u}})
        message = HumanMessage(content=content)
    else:
        message = HumanMessage(content="\n\n".join(user_parts))

    resp = await llm.ainvoke([SystemMessage(content=_DECOMPOSE_SYSTEM), message])
    raw = resp.content if isinstance(resp.content, str) else "".join(
        p.get("text", "") if isinstance(p, dict) else str(p) for p in resp.content
    )
    structure = _extract_json(raw)
    if not structure:
        raise CreationError("结构分析输出无法解析，请重试")

    # 6. 落库
    shots = structure.get("shots") or []
    row.structure_json = json.dumps(structure, ensure_ascii=False)
    row.hook_type = (structure.get("hook") or {}).get("type")
    row.duration_sec = int(duration_total) if duration_total else None
    row.shot_count = len(shots)
    row.status = "ready"
    row.error_msg = None
    await db.commit()

    # 7. 清理大文件（原片已转储，帧已上传）
    if duration_total:
        cleanup_work_dir(f"template_{row.template_id}")
