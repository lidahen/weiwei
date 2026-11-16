"""离线确定性 embedding：TF-IDF + LSA（SVD）。

用途：在无法调用外部 embedding API 时，仍能复现 B0–B3 检索 baseline。
它不是生产 embedding 的等价替代——语义泛化能力明显弱于 bge-m3，
评测报告会显式标注。生产路径仍由 `app.services.embedding_service` 提供。
"""
from __future__ import annotations

import math
from collections import Counter

import numpy as np

from app.services.rag_showcase.tokenizer import tokenize


class LocalLsaEmbedder:
    """字符 n-gram TF-IDF 经 SVD 投影到低维稠密向量，并按 L2 归一化。"""

    def __init__(self, dim: int = 128, min_df: int = 1) -> None:
        if dim <= 0:
            raise ValueError("dim must be positive")
        if min_df < 1:
            raise ValueError("min_df must be >= 1")
        self.dim = dim
        self.min_df = min_df
        self._vocab: dict[str, int] = {}
        self._idf: np.ndarray | None = None
        self._projection: np.ndarray | None = None
        self._fitted = False

    def fit(self, texts: list[str]) -> "LocalLsaEmbedder":
        docs = [tokenize(text) for text in texts]
        if not docs:
            raise ValueError("texts must not be empty")
        df: Counter[str] = Counter()
        for tokens in docs:
            df.update(set(tokens))
        vocab = sorted(token for token, count in df.items() if count >= self.min_df)
        if not vocab:
            raise ValueError("no tokens to fit")
        self._vocab = {token: i for i, token in enumerate(vocab)}
        total = len(docs)
        self._idf = np.asarray(
            [math.log((1.0 + total) / (1.0 + df[token])) + 1.0 for token in vocab],
            dtype="float32",
        )

        matrix = self._raw_matrix(docs)
        norms = np.linalg.norm(matrix, axis=1, keepdims=True)
        matrix = matrix / np.maximum(norms, 1e-9)
        k = max(1, min(self.dim, matrix.shape[0], matrix.shape[1]))
        _, _, vt = np.linalg.svd(matrix, full_matrices=False)
        projection = vt[:k].T.copy()
        # SVD 的符号不唯一；固定每个分量的主载荷为正，保证跨进程可复现
        for j in range(projection.shape[1]):
            column = projection[:, j]
            pivot = int(np.argmax(np.abs(column)))
            if column[pivot] < 0:
                projection[:, j] *= -1
        self._projection = projection.astype("float32")
        self._fitted = True
        return self

    def _raw_matrix(self, docs: list[list[str]]) -> np.ndarray:
        if self._idf is None:
            raise RuntimeError("fit() must be called before embedding")
        matrix = np.zeros((len(docs), len(self._vocab)), dtype="float32")
        for i, tokens in enumerate(docs):
            for token, count in Counter(tokens).items():
                j = self._vocab.get(token)
                if j is not None:
                    matrix[i, j] = (1.0 + math.log(count)) * self._idf[j]
        return matrix

    def _transform(self, texts: list[str]) -> list[list[float]]:
        if not self._fitted or self._projection is None:
            raise RuntimeError("fit() must be called before embedding")
        matrix = self._raw_matrix([tokenize(text) for text in texts])
        dense = matrix @ self._projection
        norms = np.linalg.norm(dense, axis=1, keepdims=True)
        dense = dense / np.maximum(norms, 1e-9)
        return dense.astype("float32").tolist()

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return self._transform(texts)

    def embed_query(self, text: str) -> list[float]:
        return self._transform([text])[0]

    async def aembed_documents(self, texts: list[str]) -> list[list[float]]:
        return self.embed_documents(texts)

    async def aembed_query(self, text: str) -> list[float]:
        return self.embed_query(text)
