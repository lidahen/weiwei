"""构建平台规则索引。

默认使用 bge-m3（免费版；但需要账户可用，402 余额不足时无法构建）；`--local-embedding` 使用
TF-IDF+SVD 离线兜底，输出到 `<index-dir>/local/`，与在线索引隔离。
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.config import settings  # noqa: E402
from app.services.rag_showcase.corpus import load_corpus  # noqa: E402
from app.services.rag_showcase.index import RulebookIndex  # noqa: E402
from app.services.rag_showcase.local_embedding import LocalLsaEmbedder  # noqa: E402


def _resolve(path: str | Path) -> Path:
    candidate = Path(path)
    return candidate if candidate.is_absolute() else (ROOT / candidate)


async def main() -> int:
    parser = argparse.ArgumentParser(description="Build the platform rulebook index")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--corpus-dir", default=settings.rag_showcase_corpus_path)
    parser.add_argument("--index-dir", default=settings.rag_showcase_index_dir)
    parser.add_argument("--local-embedding", action="store_true")
    args = parser.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:  # noqa: BLE001
        pass

    corpus_dir = _resolve(args.corpus_dir)
    index_dir = _resolve(args.index_dir)
    if args.local_embedding:
        snapshot = load_corpus(corpus_dir)
        if not snapshot.chunks:
            print("corpus is empty", file=sys.stderr)
            return 1
        local = LocalLsaEmbedder().fit([chunk.content for chunk in snapshot.chunks])
        index_dir = index_dir / "local"
        index = RulebookIndex(
            corpus_dir,
            index_dir,
            embedder=local.aembed_documents,
            embedding_model="local-lsa-zh-v1",
        )
    else:
        index = RulebookIndex(corpus_dir, index_dir)

    try:
        manifest = await index.build(force=args.force)
    except Exception as exc:  # noqa: BLE001 —— CLI 需要把失败原因完整报给使用者
        print(f"index build failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
