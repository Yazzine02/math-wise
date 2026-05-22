from sympy import simplify
from sympy.parsing.sympy_parser import parse_expr
from fastapi import HTTPException

from models.shemas import VerificationResult


# ================================================================
# PARSING
# ================================================================

def _parse(expr_str: str):
    try:
        return parse_expr(expr_str.strip(), transformations='all')
    except Exception as e:
        raise HTTPException(
            status_code=400,
            detail=f"Expression invalide '{expr_str}': {str(e)}"
        )


# ================================================================
# ÉQUIVALENCE MATHÉMATIQUE
# ================================================================

def _equivalent(expected, student) -> bool:
    try:
        if expected == student:
            return True
        return simplify(expected - student) == 0
    except Exception:
        return False


# ================================================================
# VÉRIFICATION ÉQUATION
# ================================================================

def _verify_equation(expected_str: str, student_str: str) -> VerificationResult:
    expected = _parse(expected_str)
    student  = _parse(student_str)
    correct  = _equivalent(expected, student)

    return VerificationResult(
        correct=correct,
        expected_str=str(expected),
        student_str=str(student),
        error_detail="" if correct else (
            f"Solution attendue : x = {expected}. "
            f"Solution donnée : x = {student}."
        )
    )


# ================================================================
# POINT D'ENTRÉE UNIQUE
# ================================================================

def verify(exercise_type: str,
           expected_sympy: str,
           student_answer: str,
           sympy_context: str = "") -> VerificationResult:

    if exercise_type.lower().strip() == "equation":
        return _verify_equation(expected_sympy, student_answer)

    raise HTTPException(
        status_code=400,
        detail=f"Type d'exercice inconnu : '{exercise_type}'. Valeur acceptée : equation."
    )