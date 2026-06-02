"""Deterministic per-node exercise generators (Phase 8).

Used in two roles:
1. As the global safeguard when the LLM-backed generator is unavailable.
2. As the in-endpoint fallback inside POST /generate-exercises when none of
   the LLM's candidates pass SymPy verification.

Each generator returns a plain dict matching the GeneratedExercise Pydantic
schema in main.py. Always correct by construction — no parsing, no LLM
involvement, no SymPy round-trip needed.
"""

import random
from math import gcd
from typing import Callable


# ---------------- ARITHMETIC ----------------

def gen_addition() -> dict:
    a = random.randint(10, 999)
    b = random.randint(10, 999)
    return {
        "question_text": f"What is {a} + {b}?",
        "correct_answer": str(a + b),
        "difficulty_level": 1 if max(a, b) < 100 else 2,
    }


def gen_subtraction() -> dict:
    a = random.randint(50, 999)
    b = random.randint(10, a - 1)  # ensure positive result
    return {
        "question_text": f"What is {a} - {b}?",
        "correct_answer": str(a - b),
        "difficulty_level": 1 if a < 100 else 2,
    }


def gen_multiplication() -> dict:
    a = random.randint(2, 25)
    b = random.randint(2, 15)
    return {
        "question_text": f"What is {a} × {b}?",
        "correct_answer": str(a * b),
        "difficulty_level": 1 if a < 10 and b < 10 else 2,
    }


def gen_division() -> dict:
    # Build from quotient × divisor so the answer is always an exact integer.
    q = random.randint(2, 25)
    d = random.randint(2, 12)
    a = q * d
    return {
        "question_text": f"What is {a} ÷ {d}?",
        "correct_answer": str(q),
        "difficulty_level": 1 if d <= 9 else 2,
    }


# ---------------- FRACTIONS ----------------

def gen_fractions_simplify() -> dict:
    # Pick a fully-reduced fraction p/q, then multiply both sides by a common
    # factor so the question is non-trivial.
    p = random.randint(1, 9)
    q = random.randint(p + 1, 12)
    g = gcd(p, q)
    p, q = p // g, q // g  # ensure we start from a reduced form
    factor = random.randint(2, 6)
    n, d = p * factor, q * factor
    return {
        "question_text": f"Simplify {n}/{d}.",
        "correct_answer": f"{p}/{q}",
        "difficulty_level": 3,
    }


def gen_fractions_add_sub() -> dict:
    op = random.choice(["+", "-"])
    d1 = random.randint(2, 8)
    d2 = random.randint(2, 8)
    n1 = random.randint(1, d1 - 1)
    n2 = random.randint(1, d2 - 1)

    # For subtraction, swap so the result is non-negative.
    if op == "-" and (n1 * d2) < (n2 * d1):
        n1, n2 = n2, n1
        d1, d2 = d2, d1

    common = d1 * d2
    n1c, n2c = n1 * d2, n2 * d1
    result_num = (n1c + n2c) if op == "+" else (n1c - n2c)

    if result_num == 0:
        answer = "0"
    else:
        g = gcd(abs(result_num), common)
        rp, rq = result_num // g, common // g
        answer = f"{rp}" if rq == 1 else f"{rp}/{rq}"

    return {
        "question_text": f"What is {n1}/{d1} {op} {n2}/{d2}?",
        "correct_answer": answer,
        "difficulty_level": 3,
    }


# ---------------- ALGEBRA ----------------

def gen_algebra_linear() -> dict:
    # ax + b = c with an integer solution.
    a = random.randint(2, 9)
    x = random.randint(2, 20)
    b = random.randint(-10, 10)
    c = a * x + b
    sign = "+" if b >= 0 else "-"
    question = f"Solve for x: {a}x {sign} {abs(b)} = {c}." if b != 0 \
        else f"Solve for x: {a}x = {c}."
    return {
        "question_text": question,
        "correct_answer": str(x),
        "difficulty_level": 4,
    }


def gen_algebra_factorize() -> dict:
    # Generate (x - r1)(x - r2) — pick the two roots, expand to get
    # the polynomial, and write the factored answer using the canonical
    # SymPy-friendly form "(x + p)(x + q)".
    r1 = _nonzero_int(-5, 5)
    r2 = _nonzero_int(-5, 5)

    # Polynomial coefficients: x^2 + (-r1-r2)x + (r1*r2)
    middle_coef = -(r1 + r2)
    const = r1 * r2

    polynomial = _format_quadratic(middle_coef, const)

    def factor_term(r: int) -> str:
        return f"(x - {r})" if r > 0 else f"(x + {-r})"

    factored = factor_term(r1) + factor_term(r2)

    return {
        "question_text": f"Factorize: {polynomial}.",
        "correct_answer": factored,
        "difficulty_level": 5,
    }


def _nonzero_int(lo: int, hi: int) -> int:
    """Random integer in [lo, hi] excluding 0 — avoids degenerate factoring."""
    while True:
        n = random.randint(lo, hi)
        if n != 0:
            return n


def _format_quadratic(middle_coef: int, const: int) -> str:
    """Render x^2 + bx + c as a clean human-readable string."""
    parts = ["x^2"]
    if middle_coef > 0:
        parts.append(f" + {middle_coef}x" if middle_coef != 1 else " + x")
    elif middle_coef < 0:
        parts.append(f" - {-middle_coef}x" if middle_coef != -1 else " - x")
    if const > 0:
        parts.append(f" + {const}")
    elif const < 0:
        parts.append(f" - {-const}")
    return "".join(parts)


# ---------------- DISPATCH ----------------

TEMPLATE_GENERATORS: dict[str, Callable[[], dict]] = {
    "ARITH_ADDITION": gen_addition,
    "ARITH_SUBTRACTION": gen_subtraction,
    "ARITH_MULTIPLICATION": gen_multiplication,
    "ARITH_DIVISION": gen_division,
    "FRACTIONS_SIMPLIFY": gen_fractions_simplify,
    "FRACTIONS_ADD_SUB": gen_fractions_add_sub,
    "ALGEBRA_LINEAR": gen_algebra_linear,
    "ALGEBRA_FACTORIZE": gen_algebra_factorize,
}


def generate_templates(node_code: str, count: int) -> list[dict]:
    """Produce `count` exercises for the given node using only deterministic
    templates. Returns an empty list if the node code isn't recognised — the
    caller should treat that as a "no exercises generated" outcome and
    surface an appropriate error upstream.
    """
    generator = TEMPLATE_GENERATORS.get(node_code)
    if generator is None:
        return []
    return [generator() for _ in range(count)]
