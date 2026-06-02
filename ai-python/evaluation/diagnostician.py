"""
Structured diagnosis prompt + RAG injection + post-validation.

`diagnose()` is the single entry point. It:
  1. Pulls the relevant corpus excerpts for the topic (RAG)
  2. Builds a structured prompt constraining the LLM to a closed list of
     weakness codes and a strict JSON output shape
  3. Calls the active AI adapter
  4. Parses the response and validates the returned `weakness_node_code`
     against the canonical taxonomy — if the LLM hallucinated an unknown
     code we fall back to the topic itself (or the first valid code) and
     log a warning instead of crashing
"""

import json
import logging
from typing import Optional

from fastapi import HTTPException

from adapters.ai_adapters import get_ai_adapter
from models.schemas import DiagnosisResult, VerificationResult, VALID_WEAKNESS_NODES

_logger = logging.getLogger("uvicorn.error")


def _build_prompt(
    *,
    topic_node: Optional[str],
    prerequisite_codes: list[str],
    equation: str,
    verification: VerificationResult,
    rag_excerpts: list[str],
) -> str:
    nodes_block = "\n".join(f"- {code}" for code in VALID_WEAKNESS_NODES)
    topic_block = (
        f"Topic being tested: {topic_node}"
        if topic_node else "Topic being tested: (unspecified)"
    )
    prereq_block = (
        "Formal prerequisites: " + ", ".join(prerequisite_codes)
        if prerequisite_codes
        else "Formal prerequisites: (none — this is a foundational topic)"
    )

    rag_section = ""
    if rag_excerpts:
        excerpts_formatted = "\n\n".join(f"• {e.strip()}" for e in rag_excerpts)
        rag_section = (
            "\n──── RELEVANT COURSE NOTES ────\n"
            "Ground your explanation in these excerpts from our curriculum.\n"
            "Do not contradict them; paraphrase their vocabulary.\n\n"
            f"{excerpts_formatted}\n"
        )

    return f"""You are an expert math tutor analyzing why a student got an answer wrong.
Your job is to identify the SPECIFIC underlying mistake, not just the topic.

Question: {equation}
Correct answer: {verification.expected_str}
Student's answer: {verification.student_str}
Deterministic verdict: {verification.error_detail}
{topic_block}
{prereq_block}
{rag_section}
REASON STEP BY STEP. Mentally reconstruct the sequence of operations the
student most likely performed to reach their answer. Identify the FIRST
step where they went wrong.

The weakness code should reflect the concept involved in THAT specific
erroneous step — which is very often a PREREQUISITE skill, not the topic
itself. Solving a linear equation requires addition, subtraction,
multiplication, and division; a slip in any of those is the real weakness
even though the question looked like an "algebra" question.

──── INSTRUCTIONS ────
The weakness_node MUST be EXACTLY ONE of these codes, copied verbatim
(uppercase, with underscores). Do NOT translate, paraphrase, or invent:
{nodes_block}

Prefer a prerequisite code over the topic code when the error is purely
computational. Only attribute the weakness to the topic itself if the
student misunderstood the *method* (e.g. didn't isolate x correctly,
didn't apply the distributive property), not the arithmetic.

Return ONLY a JSON object with these keys:
- "weakness_node": (string) one of the codes above, exactly as written
- "confidence":    (number) your confidence, between 0.0 and 1.0
- "explanation":   (string) a brief, friendly tutor explanation that
                   names the specific arithmetic mistake and shows the
                   correct step

Do not include any text outside the JSON.
"""


def _parse_llm_response(raw: dict, topic_node: Optional[str]) -> DiagnosisResult:
    """Validate the LLM's JSON. Accept both `weakness_node` (the legacy
    Phase 7 key) and `weakness_node_code` (the alternative). Fall back to
    a known-valid code if the LLM hallucinated."""
    code = (raw.get("weakness_node") or raw.get("weakness_node_code") or "").strip()
    confidence_raw = raw.get("confidence", 0.5)
    explanation = (raw.get("explanation") or "").strip()

    try:
        confidence = float(confidence_raw)
    except (TypeError, ValueError):
        confidence = 0.5
    confidence = max(0.0, min(1.0, confidence))

    if code not in VALID_WEAKNESS_NODES:
        fallback = (
            topic_node
            if topic_node in VALID_WEAKNESS_NODES
            else VALID_WEAKNESS_NODES[0]
        )
        _logger.warning(
            "LLM returned unknown weakness_node %r — falling back to %r",
            code, fallback,
        )
        code = fallback

    if not explanation:
        explanation = "Review this concept and try again — you've got this!"

    return DiagnosisResult(
        weakness_node_code=code,
        confidence=confidence,
        explanation=explanation,
    )


def diagnose(
    *,
    equation: str,
    verification: VerificationResult,
    topic_node: Optional[str] = None,
    prerequisite_codes: Optional[list[str]] = None,
    rag_excerpts: Optional[list[str]] = None,
) -> DiagnosisResult:
    """Build the structured prompt, call the active adapter, validate the
    response. Raises HTTPException(500) if the adapter call itself fails;
    a malformed-but-present response is repaired (fallback code, default
    explanation) rather than rejected."""
    ai_adapter = get_ai_adapter()
    prompt = _build_prompt(
        topic_node=topic_node,
        prerequisite_codes=prerequisite_codes or [],
        equation=equation,
        verification=verification,
        rag_excerpts=rag_excerpts or [],
    )
    try:
        raw_response = ai_adapter.evaluate_student_error(prompt)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM call failed: {e}")

    if not isinstance(raw_response, dict):
        # Some adapters might double-encode — try to recover once.
        try:
            raw_response = json.loads(raw_response)  # type: ignore[arg-type]
        except Exception:
            raise HTTPException(
                status_code=500,
                detail=f"LLM returned non-JSON: {raw_response!r}",
            )

    return _parse_llm_response(raw_response, topic_node)
