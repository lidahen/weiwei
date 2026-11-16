#!/usr/bin/env python3
"""AI 内容初审评测脚本（评估文档 P1：初审质量回归评测）。

两种模式：
  离线（默认，无需 API Key）：
      调用 text_rule_violations + combine_verdicts（纯规则链路，LLM 视为不可用），
      校验用例结构 + 统计规则链路的命中/漏放/误拒。
      可在任何装了项目依赖的环境运行。
  在线（--online，需 AI_API_KEY 与完整服务依赖）：
      对每条用例调用 run_text_moderation（真实 LLM）后 combine_verdicts 合并，
      统计准确率 / 混淆分布 / 漏放率，报告写入 tests/moderation_eval/report_<ts>.json。

关键指标口径（初审的特殊性：漏放比误拒严重）：
  - 准确率：risk_level 完全命中
  - 漏放率：expected=high 但判定为 low（最严重的失败，单独列出）
  - 过拒率：expected=low 但判定为 high（用户体验损失）

用法（在 ai-service 目录下）：
    py -3 tests/moderation_eval/run_moderation_eval.py            # 离线（规则链路）
    py -3 tests/moderation_eval/run_moderation_eval.py --online   # 在线（真实 LLM）
"""
import json
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

CASES_FILE = Path(__file__).resolve().parent / "moderation_cases.json"
VALID_RISKS = {"low", "medium", "high"}


def load_cases() -> list:
    data = json.loads(CASES_FILE.read_text(encoding="utf-8"))
    return data["cases"]


def validate_case(case: dict) -> list:
    """单条用例结构校验，返回问题列表（空=通过）。"""
    errs = []
    for field in ("id", "title", "description", "expected", "note"):
        if field not in case:
            errs.append(f"{case.get('id', '?')}: 缺少字段 {field}")
    if case.get("expected") not in VALID_RISKS:
        errs.append(f"{case.get('id', '?')}: expected={case.get('expected')} 非法（须为 low/medium/high）")
    if not (case.get("title") or "").strip() and not (case.get("description") or "").strip():
        errs.append(f"{case.get('id', '?')}: title 与 description 不能同时为空")
    return errs


def judge_offline(case: dict) -> str:
    """离线判定：规则链路（LLM 不可用时的降级结论，与线上降级行为一致）。"""
    from app.services.moderation import combine_verdicts, text_rule_violations

    violations = text_rule_violations(case.get("title") or "", case.get("description") or "")
    return combine_verdicts(violations, None, None)["risk_level"]


def classify_failure(expected: str, actual: str) -> str:
    """失败分类。"""
    if expected == "high" and actual == "low":
        return "严重漏放（high 判成 low）"
    if expected != "low" and actual == "low":
        return "漏放（违规判成正常）"
    if expected == "low" and actual == "high":
        return "过拒（正常判成违规）"
    if expected == "low" and actual == "medium":
        return "偏严（正常判为存疑）"
    return "档位偏差"


def _report(results: list, cases: list, mode: str) -> int:
    """统计并输出报告。results: [{id, actual}], cases: 原用例。"""
    passed = 0
    miss_high_to_low = 0
    over_reject = 0
    confusion = defaultdict(Counter)
    rows = []
    for case, res in zip(cases, results):
        expected, actual = case["expected"], res["actual"]
        hit = expected == actual
        passed += hit
        confusion[expected][actual] += 1
        if expected == "high" and actual == "low":
            miss_high_to_low += 1
        if expected == "low" and actual == "high":
            over_reject += 1
        row = {"id": case["id"], "note": case["note"], "expected": expected,
               "actual": actual, "hit": hit}
        if not hit:
            row["failure_class"] = classify_failure(expected, actual)
        rows.append(row)
        mark = "PASS" if hit else "FAIL"
        extra = "" if hit else f"  ← {row.get('failure_class', '')}"
        print(f"[{mark}] {case['id']} {case['note']}: 期望 {expected}，实际 {actual}{extra}")

    total = len(cases)
    accuracy = passed / total if total else 0.0
    high_total = sum(1 for c in cases if c["expected"] == "high")
    low_total = sum(1 for c in cases if c["expected"] == "low")
    miss_rate = miss_high_to_low / high_total if high_total else 0.0
    over_rate = over_reject / low_total if low_total else 0.0
    print(f"\n模式: {mode}")
    print(f"准确率: {passed}/{total} = {accuracy:.1%}")
    print(f"漏放率（high→low，最严重）: {miss_high_to_low}/{high_total} = {miss_rate:.1%}")
    print(f"过拒率（low→high）: {over_reject}/{low_total} = {over_rate:.1%}")
    print("混淆分布（期望→实际）:")
    for exp, ctr in sorted(confusion.items()):
        print(f"  {exp}: {dict(ctr)}")
    return {"total": total, "passed": passed, "accuracy": round(accuracy, 4),
            "miss_high_to_low": miss_high_to_low, "miss_rate": round(miss_rate, 4),
            "over_reject": over_reject, "over_rate": round(over_rate, 4),
            "rows": rows}


def run_offline() -> int:
    cases = load_cases()
    errors = []
    for case in cases:
        errors.extend(validate_case(case))
    if errors:
        print(f"用例校验 FAIL（{len(errors)} 处）:")
        for e in errors:
            print(f"  [FAIL] {e}")
        return 1

    try:
        from app.services.moderation import combine_verdicts, text_rule_violations  # noqa: F401
    except ImportError:
        # 与 run_router_eval 同策略：把 ai-service 根目录加入 sys.path 再试
        sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
        try:
            from app.services.moderation import combine_verdicts, text_rule_violations  # noqa: F401
        except ImportError as e:
            print(f"[ERROR] 需要项目依赖（当前环境缺: {e}）。请在 ai-service 服务环境运行。")
            return 2

    results = [{"id": c["id"], "actual": judge_offline(c)} for c in cases]
    stats = _report(results, cases, "离线（规则链路，LLM 降级）")
    out_file = CASES_FILE.parent / f"report_offline_{time.strftime('%Y%m%d_%H%M%S')}.json"
    out_file.write_text(json.dumps({"generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                                    "mode": "offline-rules", **stats},
                                   ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"报告已写入: {out_file}")
    # 离线模式只要求规则链路兜底能力：high 用例不允许漏放
    return 0 if stats["miss_high_to_low"] == 0 else 1


def run_online() -> int:
    """在线评测：真实调用 run_text_moderation。"""
    import asyncio

    cases = load_cases()
    errors = []
    for case in cases:
        errors.extend(validate_case(case))
    if errors:
        print(f"用例校验 FAIL（{len(errors)} 处）:")
        for e in errors:
            print(f"  [FAIL] {e}")
        return 1

    try:
        from app.services.model_resolver import resolve_llm_model, build_chat_llm
        from app.services.moderation import combine_verdicts, run_text_moderation, text_rule_violations
        from app.database import async_session
    except ImportError as e:
        print(f"[ERROR] 在线模式需要完整服务依赖（当前环境缺: {e}）。请在 ai-service 服务环境运行。")
        return 2

    async def run_one(case: dict) -> dict:
        async with async_session() as db:
            model = await resolve_llm_model(db)
        if not model:
            return {"id": case["id"], "actual": "", "error": "未配置 LLM 模型"}
        llm = build_chat_llm(model, temperature=0.1)
        violations = text_rule_violations(case.get("title") or "", case.get("description") or "")
        try:
            llm_verdict = await run_text_moderation(
                llm, case.get("title") or "", case.get("description") or "",
                None, None, None, "product" if case.get("title") else "post",
            )
        except Exception as e:
            llm_verdict = None
            print(f"[WARN] {case['id']} LLM 调用失败，降级规则结论: {e}")
        verdict = combine_verdicts(violations, llm_verdict, None)
        return {"id": case["id"], "actual": verdict["risk_level"]}

    async def run_all() -> list:
        out = []
        for case in cases:
            try:
                out.append(await run_one(case))
            except Exception as e:
                out.append({"id": case["id"], "actual": "", "error": str(e)})
        return out

    results = asyncio.run(run_all())
    stats = _report(results, cases, "在线（规则 + 真实 LLM）")
    out_file = CASES_FILE.parent / f"report_{time.strftime('%Y%m%d_%H%M%S')}.json"
    out_file.write_text(json.dumps({"generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                                    "mode": "online", **stats},
                                   ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"报告已写入: {out_file}")
    # 在线模式：准确率 ≥85% 且无严重漏放（high→low ≤2 条）才通过
    return 0 if (stats["accuracy"] >= 0.85 and stats["miss_high_to_low"] <= 2) else 1


def main() -> int:
    if "--online" in sys.argv:
        return run_online()
    return run_offline()


if __name__ == "__main__":
    sys.exit(main())
