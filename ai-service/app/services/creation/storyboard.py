"""脚本与分镜编排：选题/模板 + 人设 → 口播稿 → 分镜 JSON（校验后落库）。

LLM 只产出分镜 JSON（§4 结构），编排规则在此写死校验——AI 不稳定，消费端永远稳定。
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import List, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.ai_creation_project import AiCreationProject
from app.models.ai_persona import AiPersona
from app.models.ai_storyboard_shot import AiStoryboardShot
from app.models.ai_viral_template import AiViralTemplate
from app.services.creation.media_store import CreationError
from app.services.moderation import text_rule_violations
from app.services.model_resolver import build_chat_llm, resolve_llm_model
from app.services.prompt_enhancer import enrich_prompt

logger = logging.getLogger(__name__)

_SCRIPT_SYSTEM = """你是抖音爆款短视频口播稿写手。根据选题和人设，一次写出 {candidate_count} 个不同开头策略的候选脚本，并自评打分。

{persona_block}
{template_block}
{hook_block}

硬性输出 JSON：
{{"candidates": [
  {{"strategy": "开头策略标签（悬念前置/反常识/数字冲击/身份共鸣/结果前置 中选一）",
    "title": "视频标题(≤20字)",
    "hook": "首句钩子",
    "script": "完整口播稿(含钩子, 总长 100-160 字)",
    "score": 1-10 整数自评分,
    "reason": "一句话说明这条脚本最强和最弱的点"}}
]}}

每条候选都必须满足（这是硬标准，写完逐条自检）：
1. 第一句就是钩子：3 秒内抛出最强的点（极端结果/反常识/具体数字/直接点名人群），禁止任何铺垫问候
2. 全程口语：短句为主（一句 ≤15 字），禁止书面语、破折号、分号、「那么/接下来/首先」式导航词
3. 制造信息差：观众以为 A，其实 B——用「但是/其实/结果」在中间制造一次反转
4. 论据具体：至少一个具体数字或对比，不喊空口号
5. 结尾 CTA 是明确的单一动作（关注/评论某个答案/看完下一支），不写「希望对你有帮助」
6. 不出现禁用词；各候选开头策略必须明显不同"""


_REFINE_SYSTEM = """你是抖音口播稿优化师。把这份口播稿改得更抓人、更口语、更有节奏——只做润色，不改事实与观点。

{persona_block}

按顺序自检并修正：
1. 首句是否 3 秒内抛出最强信息，弱则重写首句
2. 删掉书面语与废话连接词，长句拆成 ≤15 字的短句
3. 中段是否至少有一个具体数字或对比，没有则在不改事实的前提下补一个疑问式强调
4. 结尾 CTA 是否为明确的单一动作指令
5. 总长保持 100-160 字，不出现禁用词

只输出 JSON：{{"title": "视频标题", "hook": "首句钩子", "script": "完整口播稿"}}（title/hook 同步更新）"""


_SHOT_SYSTEM = """你是分镜师。把口播稿切成竖屏短视频分镜，每镜将用 AI 生图+图生视频实现。

{template_block}

硬性输出 JSON：
{{"shots": [
  {{"role": "hook|body|proof|cta",
    "text": "该镜台词（从口播稿切出，≤{max_chars}字，不改写不增删语义）",
    "image_prompt": "画面描述：主体+场景+景别+光照+风格，80-150字，中文",
    "video_prompt": "画面运动描述：描述哪些元素如何运动、镜头如何运动，40-100字，中文",
    "transition": "cut|fade|whip"}}
]}}

规则：
1. 分镜数 {min_shots}-{max_shots} 镜；第一镜必为 hook（对应口播稿开头钩子句），末镜为 cta
2. 所有 text 按顺序拼接 = 完整口播稿，逐字来源于口播稿
3. 断句必须落在语义自然边界：每镜台词以标点（，。！？；）结尾，禁止拆开词组或把转折词切到下一镜开头
4. image_prompt 描述"这一帧静止画面"；video_prompt 描述"画面内外的运动"；两者不重复彼此职责
5. 禁止出现台词字幕、文字条等画面内文字要求（AI 生成文字易乱码）"""


_SAFETY_SYSTEM = """你是内容安全审核员。审核以下短视频口播稿是否适合在抖音发布。\
只输出 JSON：{"pass": true/false, "reason": "不通过原因(通过时为空)"}。\
不通过标准：违法违规、色情低俗、血腥暴力、虚假医疗/金融承诺、侮辱歧视、导流欺诈。"""


def _persona_block(persona: Optional[AiPersona]) -> str:
    if persona is None:
        return "（未指定人设：用通用短视频网感语言）"
    parts = [f"【账号人设】{persona.name}", persona.description or ""]
    if persona.style_fewshot:
        parts.append(f"【语言风格样例（模仿其句式与语气）】\n{persona.style_fewshot[:1500]}")
    if persona.banned_words:
        parts.append(f"【禁用词（绝对不得出现）】{persona.banned_words[:500]}")
    return "\n".join(p for p in parts if p)


def _template_block(template: Optional[AiViralTemplate]) -> str:
    if template is None:
        return ""
    try:
        structure = json.loads(template.structure_json or "{}")
    except json.JSONDecodeError:
        structure = {}
    return (f"【套用爆款结构模板（来源片主题：{template.title}）】\n"
            + json.dumps(structure, ensure_ascii=False)[:2500])


async def _hook_patterns_block(db: AsyncSession) -> str:
    """从爆款模板库沉淀钩子套路注入自创模式（复刻沉淀越多，自创起点越高）。"""
    rows = (await db.execute(select(AiViralTemplate).where(
        AiViralTemplate.status == "ready", AiViralTemplate.del_flag == 0,
        AiViralTemplate.hook_type.isnot(None),
    ).order_by(AiViralTemplate.template_id.desc()).limit(6))).scalars().all()
    patterns: List[str] = []
    for r in rows:
        try:
            h = (json.loads(r.structure_json or "{}").get("hook") or {})
        except json.JSONDecodeError:
            continue
        p = (h.get("pattern") or "").strip()
        if p:
            patterns.append(f"- [{r.hook_type}] {p}")
    if not patterns:
        return ""
    return ("【可借鉴的开头策略（来自已验证的爆款拆解，选最适合本选题的，只借结构不抄原句）】\n"
            + "\n".join(patterns[:6]))


def _extract_json(text: str) -> Optional[dict]:
    text = text.strip()
    if "```" in text:
        for seg in text.split("```"):
            seg = seg.strip()
            if seg.startswith("json"):
                seg = seg[4:]
            if seg.startswith("{"):
                text = seg
                break
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None


async def _llm_json(llm, system: str, user: str, *, what: str) -> dict:
    from langchain_core.messages import HumanMessage, SystemMessage

    messages = [SystemMessage(content=system), HumanMessage(content=user)]
    resp = None
    for attempt in (1, 2):
        try:
            resp = await llm.ainvoke(messages)
            break
        except Exception as exc:  # noqa: BLE001 —— LLM 异常统一转业务错误（429 限流单次重试）
            status = getattr(exc, "status_code", None)
            logger.warning("%s LLM 调用失败 status=%s: %s", what, status, str(exc)[:300])
            if status == 429 and attempt == 1:
                logger.warning("%s LLM 触发 429 限流，3s 后重试一次", what)
                await asyncio.sleep(3)
                continue
            if status == 429:
                raise CreationError(
                    "LLM 调用触发平台限流（429）：请稍后重试，或在创建时换一个 LLM 模型/升级平台套餐"
                ) from exc
            raise CreationError(f"{what}LLM 调用失败: {str(exc)[:200]}") from exc
    if resp is None:
        raise CreationError(f"{what}LLM 调用失败: 未知错误")
    raw = resp.content if isinstance(resp.content, str) else "".join(
        p.get("text", "") if isinstance(p, dict) else str(p) for p in resp.content
    )
    data = _extract_json(raw)
    if data is None:
        raise CreationError(f"{what}输出无法解析，请重试")
    return data


async def create_project(db: AsyncSession, req) -> AiCreationProject:
    """创建项目：口播稿 → 安全审核 → 分镜（含 prompt 增强）→ 落库。"""
    persona = template = None
    if req.persona_id:
        persona = (await db.execute(select(AiPersona).where(
            AiPersona.persona_id == req.persona_id, AiPersona.del_flag == 0
        ))).scalar_one_or_none()
        if persona is None:
            raise CreationError("人设不存在")
    if req.template_id:
        template = (await db.execute(select(AiViralTemplate).where(
            AiViralTemplate.template_id == req.template_id, AiViralTemplate.del_flag == 0,
            AiViralTemplate.status == "ready",
        ))).scalar_one_or_none()
        if template is None:
            raise CreationError("模板不存在或尚未拆解完成")

    # LLM：请求指定模型优先，否则走模型管理的默认模型（Agnes LLM → 聚合 TEXT 逐级回退）
    llm_model = None
    if getattr(req, "llm_model_id", None):
        from app.models.ai_model import AiModel

        llm_model = (await db.execute(select(AiModel).where(
            AiModel.model_id == req.llm_model_id, AiModel.del_flag == 0
        ))).scalar_one_or_none()
        if llm_model is None:
            raise CreationError("所选模型不存在或已删除")
        if llm_model.model_type not in ("LLM", "TEXT"):
            raise CreationError(f"所选模型类型为 {llm_model.model_type}，需要文本类模型（LLM/TEXT）")
        if llm_model.status != 1:
            raise CreationError("所选模型已停用（模型管理中重新启用）")
    if llm_model is None:
        from app.services.model_resolver import resolve_model

        llm_model = await resolve_llm_model(db) or await resolve_model(db, "TEXT")
    if llm_model is None:
        raise CreationError("未配置可用的文本模型（模型管理中添加 LLM/TEXT 类型）")
    llm = build_chat_llm(llm_model, timeout=120)

    # 1. 口播稿：一次生成多候选（不同开头策略）→ 自评分择优
    script_data = await _llm_json(
        llm,
        _SCRIPT_SYSTEM.format(
            persona_block=_persona_block(persona),
            template_block=_template_block(template),
            hook_block="" if template else await _hook_patterns_block(db),
            candidate_count=max(2, settings.creation_script_candidates),
        ),
        f"【选题】{req.topic}",
        what="脚本候选",
    )
    candidates = [c for c in (script_data.get("candidates") or [])
                  if isinstance(c, dict) and (c.get("script") or "").strip()]
    if not candidates and (script_data.get("script") or "").strip():
        candidates = [script_data]  # 兼容单脚本输出
    if not candidates:
        raise CreationError("口播稿生成失败：无有效候选，请重试")
    best = max(candidates, key=lambda c: (c.get("score") or 0))
    script_text = (best.get("script") or "").strip()
    title = (best.get("title") or req.topic)[:200]
    strategy = (best.get("strategy") or "").strip()
    if len(script_text) < 20:
        raise CreationError("口播稿生成过短，请重试或补充选题信息")

    # 2. 优化师修订（可配置开关）：只润色不改事实，弱钩子/书面语/长句在此修掉
    if settings.creation_script_refine:
        try:
            refined = await _llm_json(
                llm,
                _REFINE_SYSTEM.format(persona_block=_persona_block(persona)),
                f"【初稿（开头策略：{strategy or '未知'}）】\n{script_text}",
                what="口播稿优化",
            )
            r_text = (refined.get("script") or "").strip()
            if len(r_text) >= 20:
                script_text = r_text
                title = (refined.get("title") or title)[:200]
        except CreationError as exc:
            # 修订失败不阻断：初稿仍然可用
            logger.warning("口播稿优化师修订失败（用初稿）: %s", exc)

    # 3. 安全审核第一道（规则层硬违禁直接拒绝 + LLM 复核；LLM 失败降级放行，人工编辑兜底）。
    #    规则层复用 moderation 服务的违禁词库，口径与平台内容审核一致。
    rule_hits = text_rule_violations(title, script_text)
    if any(v.get("hard") for v in rule_hits):
        raise CreationError(f"口播稿未通过内容审核：{rule_hits[0]['detail']}")
    verdict = await _llm_json(
        llm, _SAFETY_SYSTEM, f"【标题】{title}\n【口播稿】{script_text}", what="安全审核"
    )
    if verdict.get("pass") is False:
        raise CreationError(f"口播稿未通过安全审核：{verdict.get('reason') or '内容风险'}")

    # 3. 分镜
    shot_data = await _llm_json(
        llm,
        _SHOT_SYSTEM.format(template_block=_template_block(template),
                            max_chars=settings.creation_shot_max_chars,
                            min_shots=3, max_shots=settings.creation_max_shots),
        f"【口播稿】\n{script_text}",
        what="分镜",
    )
    shots = await _validate_and_enrich_shots(shot_data.get("shots") or [], script_text)

    # 4. 安全审核第二道：分镜画面 prompt（可注入违规画面，与口播稿同一审核口径）
    prompt_bundle = "\n".join(
        f"[镜{i + 1}.image] {s['image_prompt']}\n[镜{i + 1}.video] {s['video_prompt']}"
        for i, s in enumerate(shots)
    )
    prompt_rule_hits = text_rule_violations("", prompt_bundle)
    if any(v.get("hard") for v in prompt_rule_hits):
        raise CreationError(f"分镜画面描述未通过内容审核：{prompt_rule_hits[0]['detail']}")
    prompt_verdict = await _llm_json(
        llm, _SAFETY_SYSTEM, f"【分镜画面描述】\n{prompt_bundle[:4000]}", what="分镜审核"
    )
    if prompt_verdict.get("pass") is False:
        raise CreationError(f"分镜画面描述未通过安全审核：{prompt_verdict.get('reason') or '内容风险'}")

    # 5. 落库
    shell = _storyboard_shell(shots)
    shell["meta"]["llm_model"] = llm_model.model_name
    if strategy:
        shell["meta"]["script_strategy"] = strategy
    project = AiCreationProject(
        title=title,
        source_type=req.source_type,
        template_id=req.template_id,
        persona_id=req.persona_id,
        topic=req.topic,
        script_text=script_text,
        storyboard_json=json.dumps(shell, ensure_ascii=False),
        status="storyboard_ready",
        create_by=req.create_by,
    )
    db.add(project)
    await db.flush()
    for i, s in enumerate(shots):
        db.add(AiStoryboardShot(
            project_id=project.project_id,
            shot_index=i + 1,
            text=s["text"],
            mode="img2vid",
            image_prompt=s["image_prompt"],
            video_prompt=s["video_prompt"],
            reference_images=json.dumps(s.get("reference_images") or [], ensure_ascii=False),
            create_by=req.create_by,
        ))
    await db.commit()
    await db.refresh(project)
    return project


def _storyboard_shell(shots: List[dict]) -> dict:
    """项目级分镜 JSON 外壳（audio/subtitles 默认值，前端可改）。"""
    return {
        "meta": {"aspect": "9:16", "target_duration_sec": sum(len(s["text"]) for s in shots) // 4},
        "audio": {"voice": {"type": "clone"}, "bgm": {"type": "none"}},
        "subtitles": {"style": "sentence", "font_size": 56, "highlight": "#FFE24A"},
        "shots": shots,
    }


async def _validate_and_enrich_shots(raw_shots: list, script_text: str) -> List[dict]:
    """校验分镜结构并逐镜增强 prompt；不合格自动让 LLM 重排一次的入口在此处简化为裁剪修复。"""
    if not raw_shots:
        raise CreationError("分镜为空，请重试")
    shots: List[dict] = []
    for s in raw_shots[: settings.creation_max_shots]:
        text = (s.get("text") or "").strip()
        image_prompt = (s.get("image_prompt") or "").strip()
        video_prompt = (s.get("video_prompt") or "").strip()
        if not text:
            continue
        if text[-1] not in "，。！？；、!?.;,":
            logger.warning("镜 %d 台词未以标点结尾（断句可能生硬）: …%s",
                           len(shots) + 1, text[-8:])
        if not image_prompt:
            image_prompt = text
        if not video_prompt:
            video_prompt = "镜头缓慢推进，画面元素自然运动"
        shots.append({
            "role": s.get("role") or "body",
            "text": text,
            "image_prompt": image_prompt,
            "video_prompt": video_prompt,
            "transition": s.get("transition") if s.get("transition") in ("cut", "fade", "whip") else "cut",
        })
    if not shots:
        raise CreationError("分镜解析后无有效镜，请重试")

    # prompt 增强（prompt-enhance / video-prompt-pro skill 注入）
    # 并行执行：8 镜 × 2 调用串行可达数分钟（超出前端超时），4 路并发后与单镜耗时同量级
    sem = asyncio.Semaphore(4)

    async def _enrich_one(s: dict) -> None:
        async with sem:
            try:
                img_en = await enrich_prompt(s["image_prompt"], task_type="image")
                vid_en = await enrich_prompt(s["video_prompt"], task_type="video")
                if img_en.is_enriched:
                    s["image_prompt"] = img_en.prompt
                if vid_en.is_enriched:
                    s["video_prompt"] = vid_en.prompt
            except Exception as exc:  # noqa: BLE001 —— 增强失败用原 prompt
                logger.warning("分镜 prompt 增强失败（用原始 prompt）: %s", exc)

    await asyncio.gather(*[_enrich_one(s) for s in shots])
    return shots


async def update_storyboard(db: AsyncSession, project_id: int, storyboard: dict,
                            create_by: Optional[str] = None) -> AiCreationProject:
    """保存人工编辑后的分镜：按提交顺序重排 shot_index（1..N 连续，消除删镜空洞，
    否则合成阶段按 index 取音频文件会错位）；text 变更的镜失效 audio（重渲染时重配）。"""
    project = (await db.execute(select(AiCreationProject).where(
        AiCreationProject.project_id == project_id, AiCreationProject.del_flag == 0
    ))).scalar_one_or_none()
    if project is None:
        raise CreationError("项目不存在")

    incoming = {int(s["index"]): s for s in (storyboard.get("shots") or [])
                if isinstance(s, dict) and s.get("index") is not None}
    if not incoming:
        raise CreationError("分镜数据为空")

    rows = (await db.execute(select(AiStoryboardShot).where(
        AiStoryboardShot.project_id == project_id, AiStoryboardShot.del_flag == 0
    ).order_by(AiStoryboardShot.shot_index))).scalars().all()
    existing = {r.shot_index: r for r in rows}

    # 删除不再存在的镜（按原 index 匹配）
    for idx, row in existing.items():
        if idx not in incoming:
            row.del_flag = 1

    # 按用户提交顺序重排为 1..N 连续序号
    for pos, idx in enumerate(sorted(incoming), start=1):
        s = incoming[idx]
        text = (s.get("text") or "").strip()
        row = existing.get(idx)
        if row is None:
            row = AiStoryboardShot(project_id=project_id, shot_index=pos, create_by=create_by)
            db.add(row)
        else:
            row.shot_index = pos
        text_changed = row.text != text
        row.text = text
        row.mode = s.get("mode") if s.get("mode") in ("text2vid", "img2vid", "keyframe") else "img2vid"
        row.image_prompt = s.get("image_prompt") or row.image_prompt
        row.video_prompt = s.get("video_prompt") or row.video_prompt
        row.reference_images = json.dumps(s.get("reference_images") or [], ensure_ascii=False)
        if text_changed:
            # 台词变了：配音失效，画面保留（画面语义未变，用户可手动重试）
            row.audio_url = None
            row.audio_duration_ms = None
            row.shot_status = "image_done" if row.image_url else "pending"

    project.storyboard_json = json.dumps(storyboard, ensure_ascii=False)
    if project.status in ("storyboard_ready", "done", "failed"):
        project.status = "storyboard_ready"
    await db.commit()
    await db.refresh(project)
    return project


_PUBLISH_SYSTEM = """你是抖音运营专家。根据视频标题与口播稿生成发布包。

只输出 JSON：
{"title": "发布标题（≤30字，含钩子）",
 "caption": "正文文案（80字内，口语化，含话题引导）",
 "tags": ["相关话题标签，5-8个，不带#号"],
 "ai_declaration": "本视频由 AI 辅助生成"}"""


_HOOK_VARIANT_SYSTEM = """你是抖音爆款编导。给同一条口播视频生成 {count} 个替换版「钩子」（首镜台词）。
正文其余部分一字不动，钩子必须能自然衔接正文。

硬性输出 JSON：
{{"variants": [
  {{"hook": "新钩子台词（≤{max_chars}字）", "angle": "策略标签（悬念前置/反常识/数字冲击/身份共鸣/结果前置/福利钩子 中选一）"}}
]}}

要求：
1. 各钩子策略方向要有明显差异，不与原钩子同质，互相之间也不同质
2. 口语化短句，3 秒内讲完；禁止虚假承诺式标题党
3. 紧扣选题与正文内容，不跑题"""


async def build_publish_package(db: AsyncSession, project: AiCreationProject,
                                extra_instructions: Optional[str]) -> dict:
    """生成发布包：标题/文案/标签 + AI 生成内容声明。"""
    llm_model = await resolve_llm_model(db)
    llm = build_chat_llm(llm_model, timeout=60)
    user = f"【标题】{project.title or ''}\n【口播稿】{(project.script_text or '')[:600]}"
    if extra_instructions:
        user += f"\n【补充要求】{extra_instructions[:300]}"
    package = await _llm_json(llm, _PUBLISH_SYSTEM, user, what="发布包")
    package.setdefault("ai_declaration", "本视频由 AI 辅助生成")
    package["tags"] = [str(t).lstrip("#") for t in (package.get("tags") or [])][:8]
    return package


async def create_hook_variants(db: AsyncSession, project: AiCreationProject, count: int,
                               create_by: Optional[str] = None) -> List[dict]:
    """一键多变体（hypit「正文不动、只重跑钩子」的策略）：克隆项目生成 N 版新钩子。

    成本设计：正文各镜产物（分镜图/视频片段/配音）直接复用父项目产物 URL，
    变体渲染只重跑首镜 TTS 与首镜音画对齐，近乎零额外成本；
    首镜台词变更 → 仅首镜 audio 失效，image/clip 沿用（钩子画面语义不变）。
    """
    shots = (await db.execute(select(AiStoryboardShot).where(
        AiStoryboardShot.project_id == project.project_id, AiStoryboardShot.del_flag == 0
    ).order_by(AiStoryboardShot.shot_index))).scalars().all()
    if not shots:
        raise CreationError("项目无分镜，无法生成变体")
    first = shots[0]

    # 口播稿：正文保持原样，仅替换开头的钩子句
    rest_script = ""
    if project.script_text and first.text and project.script_text.startswith(first.text):
        rest_script = project.script_text[len(first.text):]

    persona = None
    if project.persona_id:
        persona = (await db.execute(select(AiPersona).where(
            AiPersona.persona_id == project.persona_id, AiPersona.del_flag == 0
        ))).scalar_one_or_none()

    llm_model = await resolve_llm_model(db)
    llm = build_chat_llm(llm_model, timeout=60)
    user = f"【原钩子】{first.text or ''}\n【正文】{(rest_script or project.script_text or '')[:600]}"
    if persona:
        user = f"【人设】{persona.name} {persona.description or ''}\n{user}"
    data = await _llm_json(
        llm,
        _HOOK_VARIANT_SYSTEM.format(count=count, max_chars=settings.creation_shot_max_chars),
        user, what="钩子变体",
    )

    # 去重（含原钩子），保序截断到 count
    hooks: List[tuple] = []
    seen = {(first.text or "").strip()}
    for v in data.get("variants") or []:
        if not isinstance(v, dict):
            continue
        h = (v.get("hook") or "").strip()
        if h and h not in seen:
            seen.add(h)
            hooks.append((h, (v.get("angle") or "").strip()))
        if len(hooks) >= count:
            break
    if not hooks:
        raise CreationError("钩子变体生成失败，请重试")

    # 父项目级配置（audio/bgm/subtitles）与各镜 role 原样继承
    try:
        parent_shell = json.loads(project.storyboard_json or "{}") or {}
    except json.JSONDecodeError:
        parent_shell = {}
    parent_roles = [s.get("role") for s in (parent_shell.get("shots") or [])]

    created: List[dict] = []
    for i, (hook, angle) in enumerate(hooks, 1):
        proj = AiCreationProject(
            title=f"{project.title or '作品'} · 变体{i}",
            source_type=project.source_type,
            template_id=project.template_id,
            persona_id=project.persona_id,
            topic=project.topic,
            script_text=(hook + rest_script) if rest_script else hook,
            status="storyboard_ready",
            create_by=create_by or project.create_by,
        )
        db.add(proj)
        await db.flush()

        new_shell_shots: List[dict] = []
        for idx, src in enumerate(shots, start=1):
            is_hook = idx == 1
            db.add(AiStoryboardShot(
                project_id=proj.project_id,
                shot_index=idx,
                text=hook if is_hook else (src.text or ""),
                mode=src.mode or "img2vid",
                image_prompt=src.image_prompt,
                video_prompt=src.video_prompt,
                reference_images=src.reference_images,
                # 正文产物全复用；首镜台词变更仅配音失效（图/片段沿用，渲染只补 TTS）
                image_url=src.image_url,
                clip_url=src.clip_url,
                audio_url=None if is_hook else src.audio_url,
                audio_duration_ms=None if is_hook else src.audio_duration_ms,
                shot_status=("image_done" if src.image_url else "pending") if is_hook
                else (src.shot_status or "pending"),
                create_by=create_by or project.create_by,
            ))
            new_shell_shots.append({
                "role": (parent_roles[idx - 1] if idx <= len(parent_roles) and parent_roles[idx - 1]
                         else ("hook" if idx == 1 else ("cta" if idx == len(shots) else "body"))),
                "text": hook if is_hook else (src.text or ""),
                "image_prompt": src.image_prompt or "",
                "video_prompt": src.video_prompt or "",
                "transition": "cut",
            })

        shell = parent_shell if parent_shell.get("shots") else _storyboard_shell(new_shell_shots)
        shell["shots"] = new_shell_shots
        shell.setdefault("meta", {})
        shell["meta"]["variant_of"] = project.project_id
        if angle:
            shell["meta"]["variant_angle"] = angle
        proj.storyboard_json = json.dumps(shell, ensure_ascii=False)
        created.append({"project_id": proj.project_id, "title": proj.title,
                        "hook": hook, "angle": angle})

    await db.commit()
    return created
