"""Okapi BM25：混合检索里的稀疏通道。

政策问答里数字、版本号、专有名词（「48 小时」「电子烟」「v2」）是 embedding 的弱项，
BM25 的精确词匹配正好补位；两路结果用 RRF 融合，不需要做分数校准。
"""
from __future__ import annotations

import math
from collections import Counter
from typing import Sequence

from app.services.rag_showcase.tokenizer import tokenize


class BM25:
    def __init__(self, documents: Sequence[str], *, k1: float = 1.5, b: float = 0.75) -> None:
        self.k1 = k1
        self.b = b
        self.doc_tokens = [tokenize(doc) for doc in documents]
        self.doc_len = [len(tokens) for tokens in self.doc_tokens]
        self.n_docs = len(self.doc_tokens)
        self.avgdl = (sum(self.doc_len) / self.n_docs) if self.n_docs else 0.0
        self.term_freq = [Counter(tokens) for tokens in self.doc_tokens]
        self.doc_freq: Counter[str] = Counter()
        for tokens in self.doc_tokens:
            self.doc_freq.update(set(tokens))

    def scores(self, query: str) -> list[float]:
        if not self.n_docs:
            return []
        result = [0.0] * self.n_docs
        for token in tokenize(query):
            df = self.doc_freq.get(token, 0)
            if df == 0:
                continue
            # 加 1 保证 idf 恒正，避免高频词产生负分干扰排序
            idf = math.log((self.n_docs - df + 0.5) / (df + 0.5) + 1.0)
            for i, freqs in enumerate(self.term_freq):
                freq = freqs.get(token, 0)
                if freq == 0:
                    continue
                denom = freq + self.k1 * (
                    1.0 - self.b + self.b * self.doc_len[i] / (self.avgdl or 1.0)
                )
                result[i] += idf * freq * (self.k1 + 1.0) / denom
        return result

    def top_n(self, query: str, n: int) -> list[tuple[int, float]]:
        """返回 (文档下标, 分数)，按分数降序；分数为 0 的不返回。"""
        if n <= 0:
            return []
        ranked = [(i, score) for i, score in enumerate(self.scores(query)) if score > 0]
        ranked.sort(key=lambda item: (-item[1], item[0]))
        return ranked[:n]
