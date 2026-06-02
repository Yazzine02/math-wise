"""RAG configuration — paths, embedding model, retrieval knobs."""

import os

# On-disk locations (relative to ai-python/).
CHROMA_DB_PATH = os.path.join(os.path.dirname(__file__), "..", "chroma_store")
CORPUS_PATH = os.path.join(os.path.dirname(__file__), "..", "corpus")

# Local multilingual embedding model (~120 MB).
EMBEDDING_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

# Chroma collection holding the pedagogical corpus chunks.
COLLECTION_NAME = "mathwise_corpus"

# Retrieval knobs.
TOP_K = 3
MIN_RELEVANCE_SCORE = 0.45  # cosine similarity floor

# Ingestion chunking.
CHUNK_SIZE = 400      # words per chunk
CHUNK_OVERLAP = 50    # word overlap between adjacent chunks
