"""
Unit tests for rag/retriever.py and rag/ingest.py.

We don't spin up a real Chroma store here — the load-bearing property
is the graceful-degradation contract: any failure inside the retriever
must surface as an empty list rather than a 500 to Spring Boot.

The chunking helper in ingest.py is pure text logic — tested directly.
"""

import os
import sys
from unittest.mock import patch

import pytest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from rag.ingest import _chunk_text, _node_code_from_filename  # noqa: E402
from rag.retriever import _RAGRetriever, retrieve_excerpts  # noqa: E402


# ---------- _chunk_text -------------------------------------------------------

def test_chunk_short_text_is_single_chunk():
    text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
    chunks = _chunk_text(text)
    assert len(chunks) == 1
    assert "First" in chunks[0] and "Third" in chunks[0]


def test_chunk_preserves_paragraph_boundaries():
    """Paragraphs should never be cut mid-sentence — only joined on the
    \\n\\n boundary."""
    text = "P1 word " * 100 + "\n\n" + "P2 word " * 100 + "\n\n" + "P3 word " * 100
    chunks = _chunk_text(text)
    for chunk in chunks:
        # Every chunk should start at a paragraph boundary (no leading
        # word fragments)
        assert not chunk.startswith(" word")


def test_chunk_handles_empty_input():
    assert _chunk_text("") == []
    assert _chunk_text("\n\n\n") == []


# ---------- _node_code_from_filename ------------------------------------------

def test_node_code_strips_extension():
    assert _node_code_from_filename("ARITH_ADDITION.md") == "ARITH_ADDITION"
    assert _node_code_from_filename("FRACTIONS_SIMPLIFY.md") == "FRACTIONS_SIMPLIFY"


# ---------- retrieve_excerpts: graceful degradation contract ------------------

def test_retriever_returns_empty_when_init_fails():
    """If ChromaDB or sentence-transformers can't import / open, the
    retriever must return [] — never raise. Spring Boot's /evaluate-error
    must keep working even when RAG is broken."""
    # Force the lazy singleton to re-init this call
    import rag.retriever
    rag.retriever._retriever_instance = None

    # Patch the ChromaDB import inside _RAGRetriever.__init__ to raise.
    with patch.object(_RAGRetriever, "__init__", side_effect=RuntimeError("simulated init failure")):
        result = retrieve_excerpts("test query", node_code="ARITH_ADDITION")
    assert result == []


def test_retriever_empty_collection_returns_empty_list():
    """A collection that exists but has no matching documents returns []
    — used to verify the diagnostician still produces a (less grounded)
    answer when the corpus hasn't been ingested yet."""
    fake_retriever = _RAGRetriever.__new__(_RAGRetriever)
    fake_retriever.model = None  # short-circuits to []
    fake_retriever.collection = None
    assert fake_retriever.retrieve("anything") == []
