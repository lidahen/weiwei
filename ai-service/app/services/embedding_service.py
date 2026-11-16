"""Embedding 服务：封装 OpenAI 兼容的 Embedding API。

配置项（.env 或环境变量）：
- EMBEDDING_BASE_URL  默认 https://api.siliconflow.cn/v1（SiliconFlow 提供免费的 BAAI/bge-m3）
- EMBEDDING_API_KEY   必填；未配置时 RAG 检索退化为全文匹配
- EMBEDDING_MODEL     默认 BAAI/bge-m3

后续扩展：可在此处按配置切换本地 sentence-transformers、Milvus 向量库等。
"""
import logging
from typing import List, Optional

from langchain_openai import OpenAIEmbeddings

from app.config import settings

logger = logging.getLogger(__name__)

_embeddings: Optional[OpenAIEmbeddings] = None


def embedding_enabled() -> bool:
    """是否已配置 Embedding API Key。未配置时检索退化为全文匹配。"""
    return bool(settings.embedding_api_key)


def get_embeddings() -> OpenAIEmbeddings:
    """懒加载全局 Embeddings 实例。"""
    global _embeddings
    if _embeddings is None:
        _embeddings = OpenAIEmbeddings(
            base_url=settings.embedding_base_url,
            api_key=settings.embedding_api_key,
            model=settings.embedding_model,
            check_embedding_ctx_length=False,
        )
    return _embeddings


async def embed_texts(texts: List[str]) -> List[List[float]]:
    """批量向量化切片文本。"""
    if not texts:
        return []
    try:
        return await get_embeddings().aembed_documents(texts)
    except Exception as e:
        logger.error("embed_texts error: %s", e)
        raise


async def embed_query(text: str) -> List[float]:
    """向量化查询文本。"""
    try:
        return await get_embeddings().aembed_query(text)
    except Exception as e:
        logger.error("embed_query error: %s", e)
        raise
