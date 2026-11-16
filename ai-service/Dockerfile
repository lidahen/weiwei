FROM python:3.11-slim

WORKDIR /app

# creation 域系统依赖：ffmpeg（音视频合成/字幕烧录）+ 中文字体（libass 字幕渲染）
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg fonts-noto-cjk && rm -rf /var/lib/apt/lists/*

# Install dependencies first (layer caching)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# Copy application code
COPY . .

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
