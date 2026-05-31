"""
Unit tests for evaluation/diagnostician.py.

The interesting behavior here is NOT the LLM call itself (which we mock)
but the post-validation step: hallucinated weakness codes must be repaired
to a valid one with a warning log, so the response that reaches Spring Boot
is never a code KnowledgeNodeResolver can't look up.
"""

import os
import sys
from unittest.mock import patch

import pytest

# Make ai-python importable so `from evaluation...` resolves regardless of
# where pytest is invoked from.
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

# Set AI_MODE before importing diagnostician so the get_ai_adapter()
# factory doesn't raise during import-time module load.
os.environ.setdefault("AI_MODE", "local")
os.environ.setdefault("MODEL_URL", "http://localhost:11434")
os.environ.setdefault("MODEL_NAME", "llama3.2:3b")

from evaluation.diagnostician import _parse_llm_response, diagnose  # noqa: E402
from models.schemas import VALID_WEAKNESS_NODES, VerificationResult  # noqa: E402


# ---------- _parse_llm_response ----------------------------------------------

def test_parse_valid_response_passes_through():
    raw = {
        "weakness_node": "ARITH_DIVISION",
        "confidence": 0.8,
        "explanation": "The algebra was right; 15/3 = 5, not 3.",
    }
    result = _parse_llm_response(raw, topic_node="ALGEBRA_LINEAR")
    assert result.weakness_node_code == "ARITH_DIVISION"
    assert result.confidence == 0.8
    assert "15/3" in result.explanation


def test_parse_accepts_alternate_key():
    """The LLM is allowed to use `weakness_node_code` (the internal name)
    interchangeably with `weakness_node` (the Spring Boot wire name)."""
    raw = {
        "weakness_node_code": "ARITH_ADDITION",
        "confidence": 0.6,
        "explanation": "Forgot to carry the 1.",
    }
    result = _parse_llm_response(raw, topic_node=None)
    assert result.weakness_node_code == "ARITH_ADDITION"


def test_parse_hallucinated_code_falls_back_to_topic():
    """Most common defensive case: the LLM invents a code that isn't in
    our taxonomy. The topic node is the best fallback because it's at
    least related to what was being tested."""
    raw = {
        "weakness_node": "MULTIPLICATION_REGROUPING",  # not in our 8 codes
        "confidence": 0.7,
        "explanation": "Carry error.",
    }
    result = _parse_llm_response(raw, topic_node="ARITH_MULTIPLICATION")
    assert result.weakness_node_code == "ARITH_MULTIPLICATION"


def test_parse_hallucinated_code_without_valid_topic_falls_back_to_first():
    """When the topic itself is also invalid, fall back to the canonical
    first code rather than crashing or returning the bad code."""
    raw = {
        "weakness_node": "INVENTED",
        "explanation": "...",
    }
    result = _parse_llm_response(raw, topic_node="ALSO_INVENTED")
    assert result.weakness_node_code == VALID_WEAKNESS_NODES[0]


def test_parse_missing_explanation_gets_default():
    raw = {"weakness_node": "ARITH_ADDITION"}
    result = _parse_llm_response(raw, topic_node=None)
    assert result.explanation, "explanation must never be empty"


def test_parse_clamps_confidence_to_unit_interval():
    raw_high = {"weakness_node": "ARITH_ADDITION", "confidence": 5.0, "explanation": "."}
    raw_low = {"weakness_node": "ARITH_ADDITION", "confidence": -2.0, "explanation": "."}
    raw_garbage = {"weakness_node": "ARITH_ADDITION", "confidence": "high", "explanation": "."}

    assert _parse_llm_response(raw_high, None).confidence == 1.0
    assert _parse_llm_response(raw_low, None).confidence == 0.0
    # Garbage falls back to 0.5 default
    assert _parse_llm_response(raw_garbage, None).confidence == 0.5


# ---------- diagnose() end-to-end with mocked adapter -------------------------

class _FakeAdapter:
    def __init__(self, payload):
        self._payload = payload

    def evaluate_student_error(self, prompt):
        # Sanity: the prompt must contain the actual error so the LLM has
        # the verdict to reason from. This catches regressions where someone
        # rewires diagnose() to forget passing the verification.
        assert "Student wrote: 3" in prompt
        assert "Expected: 5" in prompt
        # And it must list the 8 valid codes so the LLM can pick one.
        assert "ARITH_DIVISION" in prompt
        return self._payload


def test_diagnose_round_trip_with_valid_llm_response():
    verification = VerificationResult(
        correct=False,
        expected_str="5",
        student_str="3",
        error_detail="Expected: 5. Student wrote: 3.",
    )
    fake_response = {
        "weakness_node": "ARITH_DIVISION",
        "confidence": 0.9,
        "explanation": "15 ÷ 3 = 5, not 3.",
    }
    with patch("evaluation.diagnostician.get_ai_adapter", return_value=_FakeAdapter(fake_response)):
        result = diagnose(
            equation="3x - 4 = 11",
            verification=verification,
            topic_node="ALGEBRA_LINEAR",
            prerequisite_codes=["ARITH_DIVISION"],
            rag_excerpts=["When solving ax+b=c, the final division step uses ARITH_DIVISION."],
        )
    assert result.weakness_node_code == "ARITH_DIVISION"
    assert result.confidence == 0.9


def test_diagnose_repairs_hallucinated_code_end_to_end():
    verification = VerificationResult(
        correct=False, expected_str="5", student_str="3",
        error_detail="Expected: 5. Student wrote: 3.",
    )
    fake_response = {"weakness_node": "DIVISION_ERROR", "explanation": "."}  # invalid code
    with patch("evaluation.diagnostician.get_ai_adapter", return_value=_FakeAdapter(fake_response)):
        result = diagnose(
            equation="3x - 4 = 11",
            verification=verification,
            topic_node="ALGEBRA_LINEAR",
        )
    # Fallback resolves to the topic node, which IS in the taxonomy.
    assert result.weakness_node_code == "ALGEBRA_LINEAR"
