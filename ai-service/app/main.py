import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.services.home_widget import ensure_warmer_started
from app.routers.model_config import router as model_router
from app.routers.image import router as image_router
from app.routers.chat import router as chat_router
from app.routers.video import router as video_router
from app.routers.knowledge import router as knowledge_router
from app.routers.doc import router as doc_router
from app.routers.slice import router as slice_router
from app.routers.assist import router as assist_router
from app.routers.recommend import router as recommend_router
from app.routers.exchange_match import router as exchange_match_router
from app.routers.moderation import router as moderation_router
from app.routers.music import router as music_router
from app.routers.creation import router as creation_router
from app.routers.rag_showcase import router as rag_showcase_router

@asynccontextmanager
async def lifespan(_: FastAPI):
    # 关键配置汇总告警（2026-11-03）：留空的功能将降级/不可用，此前是静默走默认值无任何提示。
    # 不阻断启动——dev 环境允许不配密钥
    from app.config import missing_critical_settings, settings

    missing = missing_critical_settings(settings)
    if missing:
        logging.getLogger(__name__).warning("以下关键配置为空（对应功能将降级/不可用）: %s", ", ".join(missing))
    # 启动即预热首页小组件天气，避免首个访客撞上外网冷启动（约 4~6s）
    ensure_warmer_started()
    # 预热 chat LLM 配置（ai_model 表的 LLM 默认模型，读不到回退 settings）。
    # 导购/RAG/提示词增强是同步取配置，预热后首个请求不必等一次查表。
    try:
        from app.services.model_resolver import refresh_llm_config

        await refresh_llm_config(force=True)
    except Exception as exc:  # noqa: BLE001 —— 预热失败不影响启动，请求时会再试
        logging.getLogger(__name__).warning("预热 LLM 配置失败: %s", exc)
    # 创作工作目录 TTL 清理（合成/拆解中间件单片数百 MB，不清会吃满磁盘）
    from app.services.creation.media_store import start_ttl_cleanup_loop

    cleanup_task = start_ttl_cleanup_loop()
    try:
        yield
    finally:
        cleanup_task.cancel()


app = FastAPI(title="邻里二手 AI Service", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(model_router)
app.include_router(image_router)
app.include_router(chat_router)
app.include_router(video_router)
app.include_router(knowledge_router)
app.include_router(doc_router)
app.include_router(slice_router)
app.include_router(assist_router)
app.include_router(moderation_router)
app.include_router(recommend_router)
app.include_router(exchange_match_router)
app.include_router(music_router)
app.include_router(creation_router)
app.include_router(rag_showcase_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
