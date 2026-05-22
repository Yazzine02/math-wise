import os
import hashlib
import logging
import chromadb
from sentence_transformers import SentenceTransformer
from rag.config import (
    CHROMA_DB_PATH, CORPUS_PATH, EMBEDDING_MODEL,
    COLLECTION_NAME, CHUNK_SIZE, CHUNK_OVERLAP
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def _hash_file(path: str) -> str:
    with open(path, "r", encoding="utf-8") as f:
        return hashlib.md5(f.read().encode()).hexdigest()


def _chunk_text(text: str) -> list[str]:
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    chunks = []
    current = []
    current_size = 0

    for para in paragraphs:
        words = para.split()
        if current_size + len(words) > CHUNK_SIZE and current:
            chunks.append("\n\n".join(current))
            # overlap
            overlap_text = " ".join(" ".join(current).split()[-CHUNK_OVERLAP:])
            current = [overlap_text]
            current_size = CHUNK_OVERLAP
        current.append(para)
        current_size += len(words)

    if current:
        chunks.append("\n\n".join(current))

    return chunks


def _extract_node_code(filename: str) -> str:
    name = os.path.splitext(filename)[0]
    parts = name.split("_")
    if len(parts) >= 2 and parts[0].isupper():
        # Cas EQ1, EQ2, EQ_POWER, EQ_SYSTEMS
        if len(parts) > 1 and parts[1].isupper():
            return f"{parts[0]}_{parts[1]}"
        return parts[0]
    return "GENERAL"


def ingest():
    logger.info("=== Démarrage ingestion corpus RAG ===")

    model = SentenceTransformer(EMBEDDING_MODEL)
    client = chromadb.PersistentClient(path=CHROMA_DB_PATH)
    collection = client.get_or_create_collection(
        name=COLLECTION_NAME,
        metadata={"hnsw:space": "cosine"}
    )

    files = [
        f for f in os.listdir(CORPUS_PATH)
        if f.endswith(".md") or f.endswith(".txt")
    ]

    total_chunks = 0
    skipped = 0

    for filename in files:
        filepath = os.path.join(CORPUS_PATH, filename)
        file_hash = _hash_file(filepath)
        doc_id_prefix = os.path.splitext(filename)[0]

        # Vérifier si déjà ingéré
        existing = collection.get(
            where={"file_hash": file_hash},
            limit=1
        )
        if existing["ids"]:
            logger.info(f"SKIP (déjà ingéré) : {filename}")
            skipped += 1
            continue

        # Supprimer ancienne version si elle existe
        old = collection.get(where={"source_file": filename})
        if old["ids"]:
            collection.delete(ids=old["ids"])
            logger.info(f"Ancienne version supprimée : {filename}")

        with open(filepath, "r", encoding="utf-8") as f:
            text = f.read()

        chunks = _chunk_text(text)
        node_code = _extract_node_code(filename)

        documents = []
        embeddings = []
        metadatas = []
        ids = []

        for i, chunk in enumerate(chunks):
            chunk_id = f"{doc_id_prefix}_{i}"
            embedding = model.encode(chunk).tolist()

            documents.append(chunk)
            embeddings.append(embedding)
            metadatas.append({
                "source_file": filename,
                "chunk_index": i,
                "knowledge_node_code": node_code,
                "file_hash": file_hash
            })
            ids.append(chunk_id)

        collection.add(
            documents=documents,
            embeddings=embeddings,
            metadatas=metadatas,
            ids=ids
        )

        logger.info(f"OK : {filename} → {len(chunks)} chunks (node: {node_code})")
        total_chunks += len(chunks)

    logger.info(f"=== Ingestion terminée : {total_chunks} chunks ajoutés, {skipped} fichiers skippés ===")


if __name__ == "__main__":
    ingest()