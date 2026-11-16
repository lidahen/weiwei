@echo off
REM ==========================================
REM 邻里二手 AI Service — Windows 启动脚本
REM
REM 用法：
REM   start.bat              生产模式启动
REM   start.bat dev          开发模式（自动重载）
REM ==========================================
chcp 65001 >nul 2>&1

echo [1/4] 检查 Python 环境...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未找到 Python，请先安装 Python 3.10+
    pause
    exit /b 1
)

echo [2/4] 检查 .env 配置...
if not exist ".env" (
    echo [WARN]  .env 不存在，从 .env.example 复制...
    if exist ".env.example" (
        copy ".env.example" ".env"
        echo [INFO] 已创建 .env，请编辑填写实际配置后重启
        pause
        exit /b 1
    ) else (
        echo [ERROR] .env 和 .env.example 都不存在
        pause
        exit /b 1
    )
)

echo [3/4] 检查依赖...
python -c "import uvicorn; import fastapi" >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] 安装依赖...
    pip install -r requirements.txt
    if %errorlevel% neq 0 (
        echo [ERROR] 依赖安装失败
        pause
        exit /b 1
    )
)

echo [4/4] 启动服务...
echo.

if "%~1"=="dev" (
    echo   -> 开发模式 (自动重载)
    python start.py --reload
) else (
    echo   -> 生产模式
    python start.py
)