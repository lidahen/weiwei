"""路由评测集离线回归（学习路线 Stage 7）：保证评测用例文件始终结构合法、
意图常量与 state.py 一致——防止评测集腐化。在线评测见 run_router_eval.py --online。
"""
import sys
from pathlib import Path


EVAL_DIR = Path(__file__).resolve().parent / "agent_eval"
sys.path.insert(0, str(EVAL_DIR))

from run_router_eval import load_cases, validate_case  # noqa: E402


def _valid_intents() -> set:
    from app.services.agent.state import ALL_INTENTS

    return set(ALL_INTENTS)


def test_cases_file_exists():
    assert (EVAL_DIR / "router_cases.json").is_file()


def test_cases_valid():
    cases = load_cases()
    assert len(cases) >= 20, "评测集不足 20 条（路线图 Stage 7 最低要求）"
    valid = _valid_intents()
    errors = []
    for case in cases:
        errors.extend(validate_case(case, valid))
    assert not errors, f"用例结构问题: {errors}"


def test_cases_cover_all_intents_or_clarify():
    cases = load_cases()
    expected = {c["expected"] for c in cases}
    valid = _valid_intents()
    uncovered = valid - expected - {"other"}  # other 无需单独用例，clarify 覆盖它
    assert not uncovered, f"以下意图无评测用例: {sorted(uncovered)}"
    assert "clarify" in expected, "缺少模糊意图→澄清的用例"
