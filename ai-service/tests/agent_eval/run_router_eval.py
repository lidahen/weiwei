#!/usr/bin/env python3
"""路由意图分类评测脚本（学习路线 Stage 7：eval 先行）。

两种模式：
  离线（默认，无需 API Key）：
      校验用例文件结构、意图常量覆盖、意图→域映射一致性、clarify 判定字段齐备。
      可在任何装了 langgraph 的环境运行。
  在线（--online，需 AI_API_KEY 与完整服务依赖）：
      对每条用例真实调用 router_node，统计准确率 / 混淆分布 / 失败分类，
      报告写入 tests/agent_eval/report_<ts>.json。

用法（在 ai-service 目录下）：
    python tests/agent_eval/run_router_eval.py            # 离线校验
    python tests/agent_eval/run_router_eval.py --online   # 在线评测
"""
import json
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

CASES_FILE = Path(__file__).resolve().parent / "router_cases.json"
CONFIDENCE_THRESHOLD = 0.6


def load_cases() -> list:
    data = json.loads(CASES_FILE.read_text(encoding="utf-8"))
    return data["cases"]


def validate_case(case: dict, valid_intents: set) -> list:
    """单条用例结构校验，返回问题列表（空=通过）。"""
    errs = []
    for field in ("id", "input", "expected", "note"):
        if not case.get(field):
            errs.append(f"{case.get('id', '?')}: 缺少字段 {field}")
    expected = case.get("expected")
    if expected != "clarify" and expected not in valid_intents:
        errs.append(f"{case.get('id', '?')}: expected={expected} 不在意图清单且不是 clarify")
    for alt in case.get("expected_any") or []:
        if alt not in valid_intents:
            errs.append(f"{case.get('id', '?')}: expected_any 含非法意图 {alt}")
    for m in case.get("history") or []:
        if m.get("role") not in ("user", "assistant") or not m.get("content"):
            errs.append(f"{case.get('id', '?')}: history 消息格式非法: {m}")
    return errs


def run_offline(valid_intents: set, intent_domain: dict) -> int:
    cases = load_cases()
    errors = []
    for case in cases:
        errors.extend(validate_case(case, valid_intents))
    # 域映射一致性：除 clarify 外每个用到的意图都必须有域
    for case in cases:
        exp = case["expected"]
        if exp != "clarify" and exp not in intent_domain:
            errors.append(f"{case['id']}: expected={exp} 缺少 INTENT_DOMAIN 映射")
    # 覆盖度统计：期望意图的分布
    dist = Counter(c["expected"] for c in cases)
    uncovered = valid_intents - set(dist)
    print(f"用例总数: {len(cases)}")
    print(f"期望意图分布: {dict(dist)}")
    if uncovered:
        print(f"⚠ 未覆盖的意图: {sorted(uncovered)}（建议补充用例）")
    if errors:
        print(f"\n离线校验 FAIL（{len(errors)} 处）:")
        for e in errors:
            print(f"  [FAIL] {e}")
        return 1
    print("\n离线校验 PASS：结构、意图清单、域映射一致。")
    print("提示：路由分类准确率需 --online 模式（需 AI_API_KEY 与完整服务依赖）。")
    return 0


def _hit(case: dict, intent: str, confidence: float) -> bool:
    """判定单条用例是否命中（clarify = other 或低置信度；expected_any 支持多个可接受意图）。"""
    expected = case["expected"]
    if expected == "clarify":
        return intent == "other" or confidence < CONFIDENCE_THRESHOLD
    accepted = case.get("expected_any") or [expected]
    if intent not in accepted:
        return False
    return confidence >= CONFIDENCE_THRESHOLD


def classify_failure(case: dict, intent: str, confidence: float) -> str:
    """失败分类（路线图 Stage 7 交付要求）。"""
    expected = case["expected"]
    accepted = case.get("expected_any") or [expected]
    if expected == "clarify":
        return "误分发(应澄清却强行分发)" if confidence >= CONFIDENCE_THRESHOLD and intent != "other" else "低置信度处理失当"
    if intent in accepted and confidence < CONFIDENCE_THRESHOLD:
        return "意图正确但置信度不足"
    return "意图误判"


def run_online() -> int:
    """在线评测：真实调用 router_node。需在具备完整服务依赖与 AI_API_KEY 的环境运行。"""
    import asyncio

    from langchain_core.messages import AIMessage, HumanMessage

    try:
        from app.config import settings
        from app.services.agent.nodes import router_node
    except ImportError as e:
        print(f"[ERROR] 在线模式需要完整服务依赖（当前环境缺: {e}）。请在 ai-service 服务环境运行。")
        return 2

    if not settings.ai_api_key:
        print("[ERROR] 未配置 AI_API_KEY，无法在线评测。")
        return 2

    cases = load_cases()
    results = []
    confusion = defaultdict(Counter)

    async def run_one(case: dict) -> dict:
        msgs = [HumanMessage(content=m["content"]) if m["role"] == "user"
                else AIMessage(content=m["content"]) for m in (case.get("history") or [])]
        msgs.append(HumanMessage(content=case["input"]))
        state = {"messages": msgs, "user_id": "eval", "tenant_id": "", "knowledge_id": None}
        t0 = time.perf_counter()
        out = await router_node(state)
        return {
            "id": case["id"],
            "intent": out.get("intent") or "",
            "confidence": out.get("confidence") or 0.0,
            "ms": round((time.perf_counter() - t0) * 1000),
        }

    async def run_all() -> list:
        out = []
        for case in cases:
            try:
                out.append(await run_one(case))
            except Exception as e:
                out.append({"id": case["id"], "intent": "", "confidence": 0.0, "error": str(e)})
        return out

    results = asyncio.run(run_all())

    passed = 0
    report_rows = []
    for case, res in zip(cases, results):
        hit = _hit(case, res["intent"], res["confidence"])
        passed += hit
        expected = case["expected"]
        confusion[expected][res["intent"] or "(error)"] += 1
        row = {
            "id": case["id"], "note": case["note"], "expected": expected,
            "actual": res["intent"], "confidence": round(res["confidence"], 2),
            "hit": hit, "ms": res.get("ms"),
        }
        if not hit:
            row["failure_class"] = res.get("error") or classify_failure(case, res["intent"], res["confidence"])
        report_rows.append(row)
        mark = "PASS" if hit else "FAIL"
        extra = "" if hit else f"  ← {row.get('failure_class', '')}"
        print(f"[{mark}] {case['id']} {case['note']}: 期望 {expected}，实际 {res['intent']}({res['confidence']:.2f}){extra}")

    total = len(cases)
    accuracy = passed / total if total else 0.0
    print(f"\n准确率: {passed}/{total} = {accuracy:.1%}")
    print("混淆分布（期望→实际）:")
    for exp, ctr in sorted(confusion.items()):
        print(f"  {exp}: {dict(ctr)}")

    report = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total": total, "passed": passed, "accuracy": round(accuracy, 4),
        "rows": report_rows,
    }
    out_file = CASES_FILE.parent / f"report_{time.strftime('%Y%m%d_%H%M%S')}.json"
    out_file.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"报告已写入: {out_file}")
    return 0 if accuracy >= 0.85 else 1


def main() -> int:
    if "--online" in sys.argv:
        return run_online()
    return run_offline(VALID_INTENTS, VALID_DOMAIN)


# ── 意图常量解析：优先 import 项目 state.py（单一事实源），失败则用本地兜底清单 ──
_VALID_INTENTS_FALLBACK = {"find_product", "product_detail", "favorite_manage", "checkout",
                           "order_query", "logistics", "after_sales", "runner_query",
                           "community_ask", "chitchat", "other"}
try:
    from app.services.agent.state import ALL_INTENTS as VALID_INTENTS, INTENT_DOMAIN as VALID_DOMAIN
except ImportError:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    try:
        from app.services.agent.state import ALL_INTENTS as _AI, INTENT_DOMAIN as _ID
        VALID_INTENTS, VALID_DOMAIN = set(_AI), _ID
    except ImportError:
        print("[WARN] 未找到 app.services.agent.state，使用本地兜底意图清单（建议在 ai-service 目录运行）")
        VALID_INTENTS, VALID_DOMAIN = _VALID_INTENTS_FALLBACK, {}

if __name__ == "__main__":
    sys.exit(main())
