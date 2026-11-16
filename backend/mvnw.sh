#!/usr/bin/env bash
# ============================================================================
# Maven 包装脚本 —— 绕过 Git Bash / MSYS 下的 classworlds 加载故障
#
# 背景：
#   本机 `mvn`（Maven 自带 bash 脚本）在 Git Bash 下会报
#     ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher
#   jar 本身完好，问题是自带脚本解析 classpath 失败。
#   因此这里直接调 Launcher 主类。
#
# 用法：
#   bash backend/mvnw.sh -o test
#   bash backend/mvnw.sh -v
#
# 注意：MVN_HOME 路径按本机实际情况修改。
# ============================================================================

set -e

MVN_HOME="F:\\apache-maven-3.9.9"
PROJECT_DIR="F:\\code\\weiwei\\backend"

exec java \
  -classpath "${MVN_HOME}\\boot\\plexus-classworlds-2.8.0.jar" \
  "-Dclassworlds.conf=${MVN_HOME}\\bin\\m2.conf" \
  "-Dmaven.home=${MVN_HOME}" \
  "-Dmaven.multiModuleProjectDirectory=${PROJECT_DIR}" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
