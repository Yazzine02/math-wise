"""
RAG retriever — wraps ChromaDB + sentence-transformers behind a tiny
`retrieve_excerpts()` function so the diagnostician can pull relevant
course content without knowing the storage internals.

Graceful degradation is the load-bearing property here: if Chroma fails
to open, the embedding model fails to load, the collection is empty, or
the query crashes for any reason, we return `[]` instead of raising. The
diagnostician treats an empty list as "no excerpts available" and still
produces a useful (just-less-grounded) answer.
"""

import logging
from typing import Optional

from rag.config import (
    CHROMA_DB_PATH,
    COLLECTION_NAME,
    EMBEDDING_MODEL,
    MIN_RELEVANCE_SCORE,
    TOP_K,
)

_logger = logging.getLogger("uvicorn.error")

_retriever_instance: Optional["_RAGRetriever"] = None


class _RAGRetriever:
    def __init__(self) -> None:
        # Imports live here so the heavy ML deps load lazily on first use,
        # not at module import time. Saves ~150 MB of RAM in any process
        # that never touches the retriever (e.g. the test runner).
        try:
            import chromadb
            from sentence_transformers import SentenceTransformer

            self.model = SentenceTransformer(EMBEDDING_MODEL)
            self.client = chromadb.PersistentClient(path=CHROMA_DB_PATH)
            self.collection = self.client.get_or_create_collection(
                name=COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"},
            )
            _logger.info("RAG retriever ready (collection=%s)", COLLECTION_NAME)
        except Exception as e:
            _logger.warning("RAG retriever init failed: %s", e)
            self.model = None
            self.collection = None

    def retrieve(
        self,
        query: str,
        k: int = TOP_K,
        node_code: Optional[str] = None,
    ) -> list[str]:
        if not self.model or self.collection is None:
            return []
        try:
            embedding = self.model.encode(query).tolist()
            where_filter = {"knowledge_node_code": node_code} if node_code else None
            results = self.collection.query(
                query_embeddings=[embedding],
                n_results=k,
                where=where_filter,
                include=["documents", "distances"],
            )
            documents = results.get("documents", [[]])[0]
            distances = results.get("distances", [[]])[0]
            # Chroma cosine distance = 1 - cosine_similarity. Lower is better.
            cutoff = 1 - MIN_RELEVANCE_SCORE
            return [doc for doc, dist in zip(documents, distances) if dist <= cutoff]
        except Exception as e:
            _logger.warning("RAG retrieve failed: %s", e)
            return []


def retrieve_excerpts(
    query: str,
    k: int = TOP_K,
    node_code: Optional[str] = None,
) -> list[str]:
    """Return up to `k` corpus excerpts ranked by semantic similarity to
    `query`. Filters to the specific `node_code` when provided so we never
    surface a fractions chunk for an addition error. Returns `[]` on any
    failure — the diagnostician treats that as "no excerpts available"."""
    global _retriever_instance
    try:
        if _retriever_instance is None:
            _retriever_instance = _RAGRetriever()
        return _retriever_instance.retrieve(query, k, node_code)
    except Exception as e:
        _logger.warning("RAG retrieval failed: %s", e)
        return []
