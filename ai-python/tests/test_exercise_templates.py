"""Unit tests for the deterministic generators (Phase 8).

The teaching point in this file: when you have a generator that produces
random outputs, the standard testing trick is **run it many times and
verify a property holds on every output**. Property-based tests are a
weak form of fuzz testing — cheaper than exhaustive enumeration, stronger
than a single example.

For each generator we run it N times and assert that the question + answer
SymPy says are mathematically consistent. SymPy gives us an independent
oracle: if the generator's arithmetic ever regresses (off-by-one in the
quotient computation, sign error in factorization), SymPy disagrees and
the test fails.
"""

import sys
import re
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest
from sympy import simplify, sympify, Symbol, solve, expand
from sympy.parsing.sympy_parser import (
    parse_expr,
    standard_transformations,
    implicit_multiplication_application,
    convert_xor,
)

from exercise_templates import (
    gen_addition,
    gen_subtraction,
    gen_multiplication,
    gen_division,
    gen_fractions_simplify,
    gen_fractions_add_sub,
    gen_algebra_linear,
    gen_algebra_factorize,
    generate_templates,
)


T = standard_transformations + (
    implicit_multiplication_application, convert_xor,
)

# Each generator is randomised — run each test enough times to catch
# parameter ranges that produce invalid output.
SAMPLES = 25


def _extract_math(question_text: str, op_char: str) -> tuple[int, int]:
    """Pull the two operands out of a question like "What is 47 + 38?"."""
    match = re.search(rf"(\d+)\s*{re.escape(op_char)}\s*(\d+)", question_text)
    assert match is not None, f"could not parse operands from {question_text!r}"
    return int(match.group(1)), int(match.group(2))


# ─────────── ARITHMETIC ───────────

class TestArithmeticGenerators:

    def test_addition_answer_matches_operands(self):
        for _ in range(SAMPLES):
            ex = gen_addition()
            a, b = _extract_math(ex["question_text"], "+")
            assert int(ex["correct_answer"]) == a + b

    def test_subtraction_answer_is_positive(self):
        for _ in range(SAMPLES):
            ex = gen_subtraction()
            a, b = _extract_math(ex["question_text"], "-")
            assert int(ex["correct_answer"]) == a - b
            assert a - b > 0, "subtraction generator must avoid negative results"

    def test_multiplication_answer_matches_operands(self):
        for _ in range(SAMPLES):
            ex = gen_multiplication()
            a, b = _extract_math(ex["question_text"], "×")
            assert int(ex["correct_answer"]) == a * b

    def test_division_quotient_is_exact_integer(self):
        for _ in range(SAMPLES):
            ex = gen_division()
            a, d = _extract_math(ex["question_text"], "÷")
            quotient = int(ex["correct_answer"])
            # The whole reason ARITH_DIVISION requires this:
            assert a % d == 0, "division must produce an exact integer quotient"
            assert quotient * d == a


# ─────────── FRACTIONS ───────────

class TestFractionGenerators:

    def test_simplify_answer_equals_original_rational(self):
        # The expected answer must be mathematically equal to n/d, AND must
        # be in lowest terms (i.e. SymPy's canonical form matches the answer).
        for _ in range(SAMPLES):
            ex = gen_fractions_simplify()
            # Extract "n/d" from "Simplify n/d."
            match = re.search(r"Simplify (\d+)/(\d+)", ex["question_text"])
            assert match is not None
            n, d = int(match.group(1)), int(match.group(2))
            answer = parse_expr(ex["correct_answer"], transformations=T)
            original = parse_expr(f"{n}/{d}", transformations=T)
            assert simplify(answer - original) == 0
            # The reduced form must equal SymPy's canonical of the original.
            assert str(simplify(original)) == ex["correct_answer"]

    def test_add_sub_result_is_correct(self):
        for _ in range(SAMPLES):
            ex = gen_fractions_add_sub()
            # Re-evaluate the question with SymPy.
            # Strip "What is " and trailing "?".
            expr_text = ex["question_text"].replace("What is", "").rstrip("?").strip()
            evaluated = simplify(parse_expr(expr_text, transformations=T))
            claimed = parse_expr(ex["correct_answer"], transformations=T)
            assert simplify(evaluated - claimed) == 0


# ─────────── ALGEBRA ───────────

class TestAlgebraGenerators:

    def test_linear_solution_actually_solves_the_equation(self):
        # The deepest correctness check: take the question text, parse it
        # back as an equation, plug in the claimed answer, and verify it
        # satisfies the equation. If the generator ever has an off-by-one
        # in how it computes c, this will catch it immediately.
        x = Symbol("x")
        for _ in range(SAMPLES):
            ex = gen_algebra_linear()
            # The question is shaped "Solve for x: <equation>."
            equation_text = ex["question_text"].split(":")[1].strip().rstrip(".")
            lhs_text, rhs_text = equation_text.split("=")
            lhs = parse_expr(lhs_text, transformations=T)
            rhs = parse_expr(rhs_text, transformations=T)
            claimed_x = int(ex["correct_answer"])
            # Substitute and check the equation holds.
            assert simplify(lhs.subs(x, claimed_x) - rhs.subs(x, claimed_x)) == 0

    def test_factorize_expansion_matches_polynomial(self):
        # The expected_answer is the factored form. Expanding it must give
        # back the polynomial from the question text.
        for _ in range(SAMPLES):
            ex = gen_algebra_factorize()
            poly_text = ex["question_text"].split(":")[1].strip().rstrip(".")
            poly = parse_expr(poly_text, transformations=T)
            factored = parse_expr(ex["correct_answer"], transformations=T)
            assert expand(factored) == expand(poly)


# ─────────── DISPATCH ───────────

class TestDispatch:

    def test_generate_templates_for_known_node(self):
        out = generate_templates("ARITH_ADDITION", 3)
        assert len(out) == 3
        for ex in out:
            assert "question_text" in ex
            assert "correct_answer" in ex
            assert "difficulty_level" in ex

    def test_unknown_node_returns_empty_list(self):
        # The caller treats this as "no templates available" and surfaces
        # an error upstream — see FastAPI's _try_llm_generation path.
        assert generate_templates("NOT_A_NODE", 5) == []
