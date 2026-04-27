from sympy import symbols, diff, integrate, simplify, oo
from sympy.parsing.sympy_parser import parse_expr
from fastapi import HTTPException

from models.shemas import VerificationResult

# Symboles Sympy globaux
x = symbols('x')
n = symbols('n')


# ================================================================
# PARSING
# ================================================================

def _parse(expr_str: str):
    """
    Convertit une string en expression Sympy.
    'all' active toutes les transformations utiles :
      - puissances : x**2
      - fonctions  : sin, cos, exp, log...
      - constantes : pi, E, oo...
    """
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
    """
    Vérifie si deux expressions sont mathématiquement identiques.

    On utilise simplify(A - B) == 0 et pas A == B parce que
    Sympy compare les formes, pas le sens mathématique :

        A == B          → False  (formes différentes)
        simplify(A - B) → 0      (mathématiquement identiques)

    Exemple :
        expected = 2*x*sin(x) + x**2*cos(x)
        student  = x*(2*sin(x) + x*cos(x))
        simplify(expected - student) → 0  ✓
    """
    try:
        # Cas spécial : infini — simplify(oo - oo) = nan pas 0
        if expected == student:
            return True
        return simplify(expected - student) == 0
    except Exception:
        return False


# ================================================================
# VÉRIFICATION PAR TYPE
# ================================================================

def _verify_derivative(expected_str: str, student_str: str) -> VerificationResult:
    expected = _parse(expected_str)
    student  = _parse(student_str)
    correct  = _equivalent(expected, student)

    return VerificationResult(
        correct=correct,
        expected_str=str(expected),
        student_str=str(student),
        error_detail="" if correct else (
            f"Dérivée attendue : {expected}. "
            f"Dérivée donnée : {student}. "
            f"Différence : {simplify(expected - student)}"
        )
    )


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


def _verify_integral(expected_str: str, student_str: str, context: str) -> VerificationResult:
    expected    = _parse(expected_str)
    student     = _parse(student_str)
    is_definite = "definite: true" in context

    if is_definite:
        # Intégrale définie → résultat numérique, comparaison directe
        correct = _equivalent(expected, student)
    else:
        # Intégrale indéfinie → on dérive les deux pour ignorer la constante C
        # ∫x² dx = x³/3 + C  et  x³/3 + 5  sont toutes les deux correctes
        # → diff(x³/3) == diff(x³/3 + 5) == x²  ✓
        correct = _equivalent(diff(expected, x), diff(student, x))

    return VerificationResult(
        correct=correct,
        expected_str=str(expected),
        student_str=str(student),
        error_detail="" if correct else (
            f"Primitive attendue : {expected}. "
            f"Primitive donnée : {student}."
        )
    )


def _verify_limit(expected_str: str, student_str: str) -> VerificationResult:
    expected = _parse(expected_str)
    student  = _parse(student_str)
    correct  = _equivalent(expected, student)

    return VerificationResult(
        correct=correct,
        expected_str=str(expected),
        student_str=str(student),
        error_detail="" if correct else (
            f"Limite attendue : {expected}. "
            f"Limite donnée : {student}."
        )
    )


# ================================================================
# POINT D'ENTRÉE UNIQUE
# Appelé par pipeline.py — c'est la seule fonction publique
# ================================================================

def verify(exercise_type: str,
           expected_sympy: str,
           student_answer: str,
           sympy_context: str = "") -> VerificationResult:
    """
    Route vers la bonne fonction selon exercise_type.
    Retourne un VerificationResult avec correct=True/False
    et error_detail si incorrect (transmis au LLM ensuite).

    Args:
        exercise_type  : "equation" | "derivative" | "integral" | "limit"
        expected_sympy : bonne réponse format Sympy (depuis la DB)
        student_answer : réponse de l'étudiant
        sympy_context  : infos optionnelles (ex: "definite: true")

    Returns:
        VerificationResult
    """
    t = exercise_type.lower().strip()

    if t == "derivative":
        return _verify_derivative(expected_sympy, student_answer)

    elif t == "equation":
        return _verify_equation(expected_sympy, student_answer)

    elif t == "integral":
        return _verify_integral(expected_sympy, student_answer, sympy_context)

    elif t == "limit":
        return _verify_limit(expected_sympy, student_answer)

    else:
        raise HTTPException(
            status_code=400,
            detail=f"Type d'exercice inconnu : '{exercise_type}'. "
                   f"Valeurs acceptées : equation, derivative, integral, limit."
        )