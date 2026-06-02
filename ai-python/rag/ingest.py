"""
One-off corpus ingestion script. Walks `corpus/*.md`, splits each file
into paragraph-aware chunks, embeds them, and writes them to ChromaDB.

Filename convention: `<NODE_CODE>.md` (e.g. `ARITH_DIVISION.md`). The node
code is parsed straight from the stem so excerpts get the right metadata
filter at retrieval time.

Idempotent: each chunk carries the source file's md5 hash; re-running
with an unchanged file is a no-op. When the file changes, the old chunks
are deleted before the new ones are added (no orphaned content).

Usage:
    cd ai-python
    python -m rag.ingest
"""

import hashlib
import logging
import os

from rag.config import (
    CHROMA_DB_PATH,
    CHUNK_OVERLAP,
    CHUNK_SIZE,
    COLLECTION_NAME,
    CORPUS_PATH,
    EMBEDDING_MODEL,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
_logger = logging.getLogger(__name__)


def _hash_file(path: str) -> str:
    with open(path, "rb") as f:
        return hashlib.md5(f.read()).hexdigest()


def _chunk_text(text: str) -> list[str]:
    """Paragraph-aware chunking with word-level overlap between adjacent
    chunks. Preserves paragraph boundaries so the LLM never sees a sentence
    cut in half."""
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    chunks: list[str] = []
    current: list[str] = []
    current_size = 0

    for para in paragraphs:
        word_count = len(para.split())
        if current_size + word_count > CHUNK_SIZE and current:
            chunks.append("\n\n".join(current))
            tail_words = " ".join(current).split()[-CHUNK_OVERLAP:]
            current = [" ".join(tail_words)] if tail_words else []
            current_size = len(tail_words)
        current.append(para)
        current_size += word_count

    if current:
        chunks.append("\n\n".join(current))
    return chunks


def _node_code_from_filename(filename: str) -> str:
    """`ARITH_ADDITION.md` → `ARITH_ADDITION`. We control the corpus filenames
    so this is a straight strip."""
    return os.path.splitext(filename)[0]


def ingest() -> None:
    # Lazy imports so callers that don't run ingestion don't pay the cost.
    import chromadb
    from sentence_transformers import SentenceTransformer

    _logger.info("=== RAG corpus ingestion ===")
    model = SentenceTransformer(EMBEDDING_MODEL)
    client = chromadb.PersistentClient(path=CHROMA_DB_PATH)
    collection = client.get_or_create_collection(
        name=COLLECTION_NAME,
        metadata={"hnsw:space": "cosine"},
    )

    if not os.path.isdir(CORPUS_PATH):
        _logger.warning("Corpus directory not found: %s", CORPUS_PATH)
        return

    files = sorted(
        f for f in os.listdir(CORPUS_PATH)
        if f.endswith(".md") or f.endswith(".txt")
    )

    total_chunks = 0
    skipped = 0

    for filename in files:
        filepath = os.path.join(CORPUS_PATH, filename)
        file_hash = _hash_file(filepath)
        node_code = _node_code_from_filename(filename)
        doc_id_prefix = node_code

        existing = collection.get(where={"file_hash": file_hash}, limit=1)
        if existing.get("ids"):
            _logger.info("SKIP (unchanged): %s", filename)
            skipped += 1
            continue

        old = collection.get(where={"source_file": filename})
        if old.get("ids"):
            collection.delete(ids=old["ids"])
            _logger.info("Replaced previous version: %s", filename)

        with open(filepath, "r", encoding="utf-8") as f:
            text = f.read()

        chunks = _chunk_text(text)
        if not chunks:
            _logger.info("EMPTY: %s", filename)
            continue

        embeddings = [model.encode(c).tolist() for c in chunks]
        ids = [f"{doc_id_prefix}_{i}" for i in range(len(chunks))]
        metadatas = [
            {
                "source_file": filename,
                "chunk_index": i,
                "knowledge_node_code": node_code,
                "file_hash": file_hash,
            }
            for i in range(len(chunks))
        ]

        collection.add(
            documents=chunks,
            embeddings=embeddings,
            metadatas=metadatas,
            ids=ids,
        )
        _logger.info("OK: %s → %d chunks (node=%s)", filename, len(chunks), node_code)
        total_chunks += len(chunks)

    _logger.info(
        "=== Done: %d chunks added, %d files unchanged ===", total_chunks, skipped
    )


if __name__ == "__main__":
    ingest()
