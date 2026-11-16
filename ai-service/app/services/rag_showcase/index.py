import asyncio
import hashlib
import json
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Awaitable, Callable

import numpy as np

from app.services.rag_showcase.corpus import Chunk, load_corpus

Embedder = Callable[[list[str]], Awaitable[list[list[float]]]]

# 切片/索引格式版本：切块逻辑或落盘结构变更时递增，旧索引会被判为陈旧。
INDEX_VERSION = "1"


def _chunk_hash(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def _chunk_to_dict(chunk: Chunk) -> dict:
    return {
        "chunk_id": chunk.chunk_id,
        "doc_id": chunk.doc_id,
        "title": chunk.title,
        "category": chunk.category,
        "version": chunk.version,
        "effective_date": chunk.effective_date.isoformat(),
        "status": chunk.status,
        "section_path": list(chunk.section_path),
        "content": chunk.content,
        "char_count": chunk.char_count,
    }


def _chunk_from_dict(data: dict) -> Chunk:
    return Chunk(
        chunk_id=data["chunk_id"],
        doc_id=data["doc_id"],
        title=data["title"],
        category=data["category"],
        version=data["version"],
        effective_date=date.fromisoformat(data["effective_date"]),
        status=data["status"],
        section_path=tuple(data.get("section_path") or ()),
        content=data["content"],
        char_count=int(data.get("char_count") or len(data["content"])),
    )


class RulebookIndex:
    """按语料整体构建的文件索引。

    文件：rulebook.faiss / rulebook.chunks.jsonl / rulebook.vectors.npy / rulebook.manifest.json。
    manifest 记录语料哈希与逐块哈希，用于「无变化跳过」与「只重嵌变更块」。
    """

    def __init__(
        self,
        corpus_dir: Path,
        index_dir: Path,
        *,
        embedder: Embedder | None = None,
        embedding_model: str | None = None,
    ) -> None:
        self.corpus_dir = Path(corpus_dir)
        self.index_dir = Path(index_dir)
        self._embedder = embedder
        self._embedding_model_override = embedding_model
        self._index = None
        self._chunks: list[Chunk] = []
        self._vectors: np.ndarray | None = None
        self._manifest: dict = {}

    # ---------- 路径 ----------

    @property
    def faiss_path(self) -> Path:
        return self.index_dir / "rulebook.faiss"

    @property
    def chunks_path(self) -> Path:
        return self.index_dir / "rulebook.chunks.jsonl"

    @property
    def vectors_path(self) -> Path:
        return self.index_dir / "rulebook.vectors.npy"

    @property
    def manifest_path(self) -> Path:
        return self.index_dir / "rulebook.manifest.json"

    # ---------- 对外只读状态 ----------

    @property
    def chunks(self) -> list[Chunk]:
        return list(self._chunks)

    @property
    def manifest(self) -> dict:
        return dict(self._manifest)

    # ---------- 构建 ----------

    async def build(self, *, force: bool = False) -> dict:
        snapshot = load_corpus(self.corpus_dir)
        if not snapshot.chunks:
            self._remove_files()
            raise ValueError("corpus is empty")

        current_model = self._embedding_model()
        old_manifest: dict = {}
        if not force and self._files_exist():
            old_manifest = self._read_manifest()
            if (
                old_manifest.get("corpus_hash") == snapshot.corpus_hash
                and old_manifest.get("embedding_model") == current_model
                and old_manifest.get("index_version") == INDEX_VERSION
            ):
                self._manifest = old_manifest
                return old_manifest

        old_by_hash: dict[str, np.ndarray] = {}
        if not force and (
            old_manifest.get("embedding_model") == current_model
            and old_manifest.get("index_version") == INDEX_VERSION
        ):
            old_chunks, old_vectors = self._read_chunks_and_vectors()
            if old_vectors is not None and len(old_chunks) == len(old_vectors):
                for chunk, vector in zip(old_chunks, old_vectors):
                    old_by_hash[_chunk_hash(chunk.content)] = np.asarray(vector, dtype="float32")

        chunks = list(snapshot.chunks)
        to_embed: list[str] = []
        to_embed_positions: list[int] = []
        vectors: list[np.ndarray | None] = [None] * len(chunks)
        for i, chunk in enumerate(chunks):
            cached = old_by_hash.get(_chunk_hash(chunk.content))
            if cached is not None:
                vectors[i] = cached
            else:
                to_embed.append(chunk.content)
                to_embed_positions.append(i)

        if to_embed:
            embedded = await self._embed_texts(to_embed)
            if len(embedded) != len(to_embed):
                raise RuntimeError("embedding count mismatch")
            for pos, vector in zip(to_embed_positions, embedded):
                vectors[pos] = np.asarray(vector, dtype="float32")

        matrix = np.ascontiguousarray(np.vstack(vectors), dtype="float32")
        if matrix.ndim != 2 or matrix.shape[1] == 0:
            raise RuntimeError("invalid embedding matrix")

        manifest = {
            "index_version": INDEX_VERSION,
            "corpus_hash": snapshot.corpus_hash,
            "built_at": datetime.now(timezone.utc).isoformat(),
            "embedding_model": current_model,
            "chunk_count": len(chunks),
            "doc_hashes": snapshot.doc_hashes,
            "chunk_hashes": {c.chunk_id: _chunk_hash(c.content) for c in chunks},
        }
        self._write_files(chunks, matrix, manifest)
        self._chunks = chunks
        self._vectors = matrix
        self._manifest = manifest
        self._index = self._build_faiss(matrix)
        return manifest

    async def _embed_texts(self, texts: list[str]) -> list[list[float]]:
        if self._embedder is not None:
            return await self._embedder(texts)
        from app.services.embedding_service import embed_texts

        return await embed_texts(texts)

    def _embedding_model(self) -> str:
        if self._embedding_model_override is not None:
            return self._embedding_model_override
        try:
            from app.config import settings

            return settings.embedding_model
        except Exception:
            return ""

    def _write_files(self, chunks: list[Chunk], matrix: np.ndarray, manifest: dict) -> None:
        self.index_dir.mkdir(parents=True, exist_ok=True)
        with self.chunks_path.open("w", encoding="utf-8") as f:
            for chunk in chunks:
                f.write(json.dumps(_chunk_to_dict(chunk), ensure_ascii=False) + "\n")
        np.save(self.vectors_path, matrix)
        faiss_index = self._build_faiss(matrix)
        import faiss

        faiss.write_index(faiss_index, str(self.faiss_path))
        self.manifest_path.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    @staticmethod
    def _build_faiss(matrix: np.ndarray):
        import faiss

        mat = np.ascontiguousarray(matrix, dtype="float32")
        index = faiss.IndexFlatIP(mat.shape[1])
        faiss.normalize_L2(mat)
        index.add(mat)
        return index

    def _remove_files(self) -> None:
        for path in (self.faiss_path, self.chunks_path, self.vectors_path, self.manifest_path):
            if path.exists():
                path.unlink()

    # ---------- 加载 / 陈旧检测 ----------

    async def load(self) -> bool:
        if not self._files_exist():
            return False
        chunks, vectors = self._read_chunks_and_vectors()
        if vectors is None or len(chunks) != len(vectors):
            return False
        self._chunks = chunks
        self._vectors = np.asarray(vectors, dtype="float32")
        self._manifest = self._read_manifest()
        self._index = self._build_faiss(self._vectors)
        return True

    def is_stale(self) -> bool:
        if not self.manifest_path.exists():
            return True
        manifest = self._read_manifest()
        if manifest.get("index_version") != INDEX_VERSION:
            return True
        if manifest.get("embedding_model") != self._embedding_model():
            return True
        try:
            snapshot = load_corpus(self.corpus_dir)
        except Exception:
            return True
        return snapshot.corpus_hash != manifest.get("corpus_hash")

    def _files_exist(self) -> bool:
        return all(
            path.exists()
            for path in (self.faiss_path, self.chunks_path, self.vectors_path, self.manifest_path)
        )

    def _read_manifest(self) -> dict:
        try:
            return json.loads(self.manifest_path.read_text(encoding="utf-8"))
        except Exception:
            return {}

    def _read_chunks_and_vectors(self) -> tuple[list[Chunk], np.ndarray | None]:
        if not self.chunks_path.exists() or not self.vectors_path.exists():
            return [], None
        chunks = [
            _chunk_from_dict(json.loads(line))
            for line in self.chunks_path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        try:
            vectors = np.load(self.vectors_path)
        except Exception:
            return chunks, None
        return chunks, vectors

    # ---------- 检索 ----------

    def search_vector(self, query_vector: np.ndarray, k: int) -> list[tuple[int, float]]:
        if self._index is None or k <= 0 or not self._chunks:
            return []
        import faiss

        q = np.ascontiguousarray(np.asarray([query_vector], dtype="float32"))
        if q.shape[1] != self._index.d:
            return []
        faiss.normalize_L2(q)
        scores, positions = self._index.search(q, min(k, self._index.ntotal))
        return [
            (int(pos), float(score))
            for score, pos in zip(scores[0], positions[0])
            if 0 <= int(pos) < len(self._chunks)
        ]
