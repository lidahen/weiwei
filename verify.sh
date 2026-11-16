#!/usr/bin/env bash
# ============================================================================
# Weiwei 三端统一质量门禁
#
# 用法（本机 Git Bash / Linux / CI 通用）：
#   bash verify.sh
#   MVN_FLAGS="" bash verify.sh          # 需要联网下载 Maven 依赖时（默认离线 -o）
#   MVN=/path/to/mvn PYTHON=python3.12 bash verify.sh
#
# 退出码：全部通过 = 0；任一失败或工具缺失 = 1
#
# 设计要点：
#   1) 失败不中断——一次跑完所有检查，避免"修一个跑一次"；
#   2) 工具缺失算失败——门禁不应因为"跑不起来"而显示绿色；
#   3) Windows/Git Bash 下 maven 自带的 bash 脚本会报 classworlds 故障
#      （见 backend/mvnw.sh 注释），因此优先调用 mvn.cmd。
# ============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
START_ALL=$SECONDS

case "$(uname -s)" in
	MINGW* | MSYS* | CYGWIN*) IS_WINDOWS=1 ;;
	*) IS_WINDOWS=0 ;;
esac

# ── 工具探测 ────────────────────────────────────────────────────────────────
MVN_CMD=()
if [ -n "${MVN:-}" ]; then
	MVN_CMD=("$MVN")
elif [ "$IS_WINDOWS" = 1 ] && command -v mvn.cmd >/dev/null 2>&1; then
	MVN_CMD=("mvn.cmd")
elif command -v mvn >/dev/null 2>&1; then
	MVN_CMD=("mvn")
fi

PY_CMD=()
if [ -n "${PYTHON:-}" ]; then
	PY_CMD=("$PYTHON")
elif [ "$IS_WINDOWS" = 1 ] && command -v py >/dev/null 2>&1; then
	PY_CMD=(py -3)
elif command -v python3 >/dev/null 2>&1; then
	PY_CMD=(python3)
elif command -v python >/dev/null 2>&1; then
	PY_CMD=(python)
fi

NODE_BIN="$(command -v node || true)"

# 默认离线（本机 m2 仓库在 F:\apache-maven-3.9.9\mvn_repo，已缓存依赖）
MVN_FLAGS_ARR=(-o)
if [ -n "${MVN_FLAGS+x}" ]; then
	# 显式传入（含空串）时以传入值为准
	read -r -a MVN_FLAGS_ARR <<<"${MVN_FLAGS}"
fi

# ── 结果收集 ────────────────────────────────────────────────────────────────
RESULTS=()
FAILED=0

run_check() {
	local name="$1" dir="$2"
	shift 2
	echo ""
	echo "──────────────────────────────────────────────────────────────"
	echo "▶ ${name}"
	echo "  (cd ${dir} && $*)"
	echo "──────────────────────────────────────────────────────────────"
	local t0=$SECONDS
	if (cd "$dir" && "$@"); then
		local el=$((SECONDS - t0))
		echo "✅ PASS  ${name}  (${el}s)"
		RESULTS+=("PASS|${name}|${el}s")
	else
		local code=$?
		local el=$((SECONDS - t0))
		echo "❌ FAIL  ${name}  (exit ${code}, ${el}s)"
		RESULTS+=("FAIL|${name}|exit ${code}, ${el}s")
		FAILED=1
	fi
}

skip_check() {
	local name="$1" reason="$2"
	echo ""
	echo "⚠️  SKIP  ${name} —— ${reason}"
	RESULTS+=("SKIP|${name}|${reason}")
	# 工具缺失视为失败：门禁不因"跑不起来"而变绿
	FAILED=1
}

echo "================================================================"
echo " Weiwei 三端质量门禁"
echo " 仓库根目录 : ${REPO_ROOT}"
echo " 平台       : $(uname -s) ${IS_WINDOWS:+（Windows）}"
echo " Maven      : ${MVN_CMD[*]:-<未找到>} ${MVN_FLAGS_ARR[*]:-}"
echo " Python     : ${PY_CMD[*]:-<未找到>}"
echo " Node       : ${NODE_BIN:-<未找到>}"
echo "================================================================"

# ── 1) backend 单元测试 ─────────────────────────────────────────────────────
if [ ${#MVN_CMD[@]} -eq 0 ]; then
	skip_check "backend 单元测试 (mvn test)" "未找到 mvn/mvn.cmd"
else
	run_check "backend 单元测试 (mvn test)" "${REPO_ROOT}/backend" \
		"${MVN_CMD[@]}" -B "${MVN_FLAGS_ARR[@]}" test
fi

# ── 2) backend 代码格式（现有 CI 的第一条命令）──────────────────────────────
if [ ${#MVN_CMD[@]} -eq 0 ]; then
	skip_check "backend 格式校验 (spring-javaformat)" "未找到 mvn/mvn.cmd"
else
	run_check "backend 格式校验 (spring-javaformat:validate)" "${REPO_ROOT}/backend" \
		"${MVN_CMD[@]}" -B "${MVN_FLAGS_ARR[@]}" spring-javaformat:validate
fi

# ── 3) ui 类型检查（tsc）────────────────────────────────────────────────────
if [ -z "$NODE_BIN" ]; then
	skip_check "ui 类型检查 (tsc)" "未找到 node"
elif [ ! -f "${REPO_ROOT}/ui/node_modules/typescript/bin/tsc" ]; then
	skip_check "ui 类型检查 (tsc)" "ui/node_modules 未安装（先执行 npm ci）"
else
	run_check "ui 类型检查 (tsc --noEmit)" "${REPO_ROOT}/ui" \
		"$NODE_BIN" node_modules/typescript/bin/tsc --noEmit
fi

# ── 4) ui 单元测试（jest）───────────────────────────────────────────────────
if [ -z "$NODE_BIN" ]; then
	skip_check "ui 单元测试 (jest)" "未找到 node"
elif [ ! -f "${REPO_ROOT}/ui/node_modules/jest/bin/jest.js" ]; then
	skip_check "ui 单元测试 (jest)" "ui/node_modules 未安装（先执行 npm ci）"
else
	run_check "ui 单元测试 (jest --ci)" "${REPO_ROOT}/ui" \
		"$NODE_BIN" node_modules/jest/bin/jest.js --ci
fi

# ── 5) ai-service 测试（pytest）─────────────────────────────────────────────
if [ ${#PY_CMD[@]} -eq 0 ]; then
	skip_check "ai-service 测试 (pytest)" "未找到 python"
else
	run_check "ai-service 测试 (pytest -q)" "${REPO_ROOT}/ai-service" \
		"${PY_CMD[@]}" -m pytest tests -q -p no:cacheprovider
fi

# ── 汇总 ────────────────────────────────────────────────────────────────────
echo ""
echo "================================================================"
echo " 汇总（总耗时 $((SECONDS - START_ALL))s）"
echo "================================================================"
for r in "${RESULTS[@]}"; do
	IFS='|' read -r status name detail <<<"$r"
	case "$status" in
		PASS) printf '  ✅ %-46s %s\n' "$name" "$detail" ;;
		FAIL) printf '  ❌ %-46s %s\n' "$name" "$detail" ;;
		SKIP) printf '  ⚠️  %-46s %s\n' "$name" "$detail" ;;
	esac
done
echo ""
if [ "$FAILED" -eq 0 ]; then
	echo " 结果：全部通过 ✅"
else
	echo " 结果：存在失败项 ❌"
fi
echo ""
echo " 待补（阶段 D 安全项，当前故意未纳入门禁）："
echo "   - 密钥扫描（.env / 配置文件 / 文档）"
echo "   原因：凭据出库已按用户决定推迟到阶段 D；现在纳入会让门禁长期恒红。"
echo ""

exit "$FAILED"
