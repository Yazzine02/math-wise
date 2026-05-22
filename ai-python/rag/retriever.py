import logging
import chromadb
from sentence_transformers import SentenceTransformer
from rag.config import (
    CHROMA_DB_PATH, EMBEDDING_MODEL,
    COLLECTION_NAME, TOP_K, MIN_RELEVANCE_SCORE
)

logger = logging.getLogger(__name__)

_retriever_instance = None


class _RAGRetriever:
    def __init__(self):
        try:
            self.model = SentenceTransformer(EMBEDDING_MODEL)
            self.client = chromadb.PersistentClient(path=CHROMA_DB_PATH)
            self.collection = self.client.get_or_create_collection(
                name=COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"}
            )
            logger.info("RAG retriever initialisé avec succès")
        except Exception as e:
            logger.warning(f"RAG retriever init failed: {e}")
            self.model = None
            self.collection = None

    def retrieve(self, query: str, k: int = TOP_K) -> list[str]:
        if not self.model or not self.collection:
            return []

        try:
            embedding = self.model.encode(query).tolist()
            results = self.collection.query(
                query_embeddings=[embedding],
                n_results=k,
                include=["documents", "distances"]
            )

            excerpts = []
            documents = results.get("documents", [[]])[0]
            distances = results.get("distances", [[]])[0]

            for doc, distance in zip(documents, distances):
                if distance <= (1 - MIN_RELEVANCE_SCORE):
                    excerpts.append(doc)

            return excerpts

        except Exception as e:
            logger.warning(f"RAG retrieve failed: {e}")
            return []


def retrieve_excerpts(query: str, k: int = TOP_K) -> list[str]:
    global _retriever_instance
    try:
        if _retriever_instance is None:
            _retriever_instance = _RAGRetriever()
        return _retriever_instance.retrieve(query, k)
    except Exception as e:
        logger.warning(f"RAG retrieval failed: {e}")
        return []