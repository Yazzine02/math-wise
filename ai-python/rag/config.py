import os

# Chemins
CHROMA_DB_PATH = os.path.join(os.path.dirname(__file__), "..", "chroma_store")
CORPUS_PATH    = os.path.join(os.path.dirname(__file__), "..", "corpus")

# Modèle d'embedding — local, multilingue, léger (~120 Mo)
EMBEDDING_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

# ChromaDB
COLLECTION_NAME = "mathwise_courses"

# Retrieval
TOP_K = 3
MIN_RELEVANCE_SCORE = 0.5
CHUNK_SIZE = 400
CHUNK_OVERLAP = 50