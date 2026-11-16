"""中文友好的轻量 tokenizer：字符 n-gram + 英文/数字词。

为什么不用 jieba：政策文本里数字、版本号、条款名是强信号，分词器一旦切错
（如「退货运费」切成「退货/运费」或「退/货运/费」）会直接影响 BM25 召回；
字符 1/2/3-gram 对未登录词和数字更稳，且零额外依赖。
"""
from __future__ import annotations

import re
import unicodedata

_TOKEN_RE = re.compile(r"[一-鿿]+|[a-z0-9]+(?:[.%][0-9]+)?%?")


def tokenize(text: str) -> list[str]:
    """返回保留词频的 token 序列（不去重，BM25 需要 tf）。"""
    normalized = unicodedata.normalize("NFKC", text or "").lower()
    tokens: list[str] = []
    for run in _TOKEN_RE.findall(normalized):
        if "一" <= run[0] <= "鿿":
            # 中文 run：单字 + 二元 + 三元，兼顾短词与「工作日」这类三字词
            tokens.extend(run)
            tokens.extend(run[i:i + 2] for i in range(len(run) - 1))
            tokens.extend(run[i:i + 3] for i in range(len(run) - 2))
        else:
            tokens.append(run)
    return tokens
