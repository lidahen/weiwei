#!/bin/bash
# ==========================================
# 邻里二手 AI Service — Linux / macOS 启动脚本
#
# 用法：
#   ./start.sh              生产模式启动
#   ./start.sh dev          开发模式（自动重载）
# ==========================================

set -e

echo "[1/4] 检查 Python 环境..."
if ! command -v python3 &>/dev/null; then
    echo "[ERROR] 未找到 python3，请先安装 Python 3.10+"
    exit 1
fi
python3 --version

echo "[2/4] 检查 .env 配置..."
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo "[WARN]  .env 不存在，从 .env.example 复制..."
        cp .env.example .env
        echo "[INFO] 已创建 .env，请编辑填写实际配置后重启"
        exit 1
    else
        echo "[ERROR] .env 和 .env.example 都不存在"
        exit 1
    fi
fi

echo "[3/4] 检查依赖..."
if ! python3 -c "import uvicorn; import fastapi" &>/dev/null; then
    echo "[INFO] 安装依赖..."
    pip install -r requirements.txt
fi

echo "[4/4] 启动服务..."
echo ""

if [ "$1" = "dev" ]; then
    echo "  -> 开发模式 (自动重载)"
    python3 start.py --reload
else
    echo "  -> 生产模式"
    python3 start.py
fi