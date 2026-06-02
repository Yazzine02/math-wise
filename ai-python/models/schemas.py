"""
Internal Pydantic models for the diagnosis pipeline.

These types circulate inside the FastAPI service only — they are NOT the
external wire format. The external request (`MathEvaluationRequest`) and
response shape (`{weakness_node, explanation}`) are still defined in
main.py to preserve the Spring Boot contract.
"""

from pydantic import BaseModel


# Canonical knowledge-node codes, mirroring DataSeeder.java on the Spring
# Boot side. Defined here as the single source of truth so both main.py and
# the diagnostician import from one place.
VALID_WEAKNESS_NODES: list[str] = [
    "ARITH_ADDITION",
    "ARITH_SUBTRACTION",
    "ARITH_MULTIPLICATION",
    "ARITH_DIVISION",
    "FRACTIONS_SIMPLIFY",
    "FRACTIONS_ADD_SUB",
    "ALGEBRA_LINEAR",
    "ALGEBRA_FACTORIZE",
]


class VerificationResult(BaseModel):
    """
    Outcome of the deterministic correctness check, handed to the
    diagnostician as factual ground truth (the LLM must explain THIS
    error, not invent its own version of what went wrong).
    """

    correct: bool
    expected_str: str
    student_str: str
    error_detail: str = ""


class DiagnosisResult(BaseModel):
    """LLM output after the structured prompt + post-validation pass."""

    weakness_node_code: str
    confidence: float
    explanation: str
