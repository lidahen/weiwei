"""内置 Skill 加载器。

从 ai-service/skills/ 目录加载提示词增强类 Skill（SKILL.md：YAML frontmatter + Markdown 正文），
注入提示词增强的 System Prompt，使增强能力可以通过编辑 Markdown 文件调整，而无需改动代码。

frontmatter 支持字段：
- name: Skill 名称
- description: 一句话说明
- applies_to: 适用任务类型列表，如 [image, video]
- priority: 同类型命中多个 Skill 时的优先级，数字越大越优先

加载策略：
- 扫描 skills/*/SKILL.md 并缓存；文件 mtime 变化时自动重载（开发期热更新）
- 按 task_type 匹配 applies_to，取 priority 最高者；无匹配则回退到第一个可用 Skill
- 目录缺失或解析异常时安全降级为空内容，不影响生成主流程
"""
import logging
from pathlib import Path
from typing import Any, Dict, Optional

import yaml

logger = logging.getLogger(__name__)

# ai-service 根目录（app/services/skill_loader.py -> parents[2]）
_SKILLS_DIR = Path(__file__).resolve().parents[2] / "skills"

_cache: Dict[str, Dict[str, Any]] = {}


def _parse_skill_file(path: Path) -> Optional[Dict[str, Any]]:
    """解析单个 SKILL.md，返回 {meta, body, mtime}；解析失败返回 None。"""
    try:
        text = path.read_text(encoding="utf-8")
        mtime = path.stat().st_mtime

        meta: Dict[str, Any] = {}
        body = text.strip()
        if text.lstrip().startswith("---"):
            parts = text.lstrip().split("---", 2)
            if len(parts) >= 3:
                meta = yaml.safe_load(parts[1]) or {}
                body = parts[2].strip()

        return {"meta": meta, "body": body, "mtime": mtime}
    except Exception as exc:
        logger.warning("Failed to parse skill file %s: %s", path, exc)
        return None


def _load_skills() -> Dict[str, Dict[str, Any]]:
    """扫描 skills 目录，按文件 mtime 增量刷新缓存。"""
    skill_dirs = [d for d in _SKILLS_DIR.iterdir() if d.is_dir()] if _SKILLS_DIR.is_dir() else []
    seen_dirs = set()

    for d in skill_dirs:
        skill_file = d / "SKILL.md"
        if not skill_file.is_file():
            continue
        seen_dirs.add(d.name)
        cached = _cache.get(d.name)
        try:
            mtime = skill_file.stat().st_mtime
        except OSError:
            continue
        if cached and cached["mtime"] == mtime:
            continue
        parsed = _parse_skill_file(skill_file)
        if parsed:
            _cache[d.name] = parsed
            logger.info("Loaded skill '%s' from %s", d.name, skill_file)

    # 目录被移除时清理缓存
    for name in list(_cache.keys()):
        if name not in seen_dirs:
            del _cache[name]

    return _cache


def get_skill_body(task_type: Optional[str] = None) -> str:
    """按任务类型返回最匹配 Skill 的正文内容。

    Args:
        task_type: 任务类型，如 "image" / "video"（对应 applies_to 字段）。

    Returns:
        Skill 正文 Markdown；无可用 Skill 时返回空字符串。
    """
    skills = _load_skills()
    if not skills:
        return ""

    def _priority(item: Dict[str, Any]) -> int:
        try:
            return int(item["meta"].get("priority") or 0)
        except (TypeError, ValueError):
            return 0

    candidates = list(skills.values())

    if task_type:
        matched = [
            s for s in candidates
            if task_type in (s["meta"].get("applies_to") or [])
        ]
        if matched:
            candidates = matched

    best = max(candidates, key=_priority)
    return best["body"]


def get_skill_by_name(name: str) -> str:
    """按 Skill 目录名精确取正文（导购/初审等非增强类 Skill 的取用方式）。

    与 get_skill_body 的 applies_to 匹配不同：这里按 skills/<name>/SKILL.md 的
    目录名直接定位，取不到返回空字符串（调用方回退内置 prompt，不影响主流程）。
    """
    if not name:
        return ""
    skills = _load_skills()
    skill = skills.get(name)
    return skill["body"] if skill else ""
