"""Unit tests for the SymPy-backed answer check from Phase 1.

What this test file teaches:

1. pytest needs no annotation magic — test functions are just functions
   whose name starts with ``test_``. The runner discovers them.

2. Parametrising over a table of (correct, student, expected) tuples is
   the most readable way to verify a "many inputs, same behaviour" rule.
   With ``@pytest.mark.parametrize`` you write the assertion once and
   pytest runs it N times, naming each test after the input that produced it.

3. We test the *internal* helper ``_answers_equivalent`` directly, not the
   FastAPI endpoint. That keeps the tests in milliseconds. Endpoint-level
   tests are higher up the pyramid; this is the unit-level base.
"""

import sys
from pathlib import Path

# Add the parent directory (ai-python) to the import path so this file
# can ``import main`` regardless of where pytest is invoked from. There
# are cleaner ways (a setup.py, src layout) but for a small POC this is
# the path of least resistance.
sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest

from main import _answers_equivalent


class TestSymbolicEquivalence:
    """Cases SymPy can verify symbolically (used_symbolic_check=True)."""

    @pytest.mark.parametrize(
        "correct,student",
        [
            # Equivalent fractions
            ("2/3", "4/6"),
            ("1/2", "0.5"),
            ("1/2", "2/4"),
            # Numeric coercion
            ("85", "85.0"),
            ("100", "100"),
            # Commutative algebra
            ("(x+2)(x+3)", "(x+3)(x+2)"),
            # Factored vs expanded
            ("(x-3)(x+3)", "x^2 - 9"),
            ("(x+2)(x+3)", "x^2 + 5x + 6"),
            # Whitespace + case
            ("  7  ", "7"),
        ],
    )
    def test_equivalent_pairs_are_accepted(self, correct, student):
        is_correct, used_symbolic = _answers_equivalent(correct, student)
        assert is_correct is True
        assert used_symbolic is True

    @pytest.mark.parametrize(
        "correct,student",
        [
            ("5", "8"),                # plain wrong
            ("1/2", "1/3"),            # different fractions
            ("(x-3)(x+3)", "x^2 - 4"), # off-by-one in the constant
        ],
    )
    def test_inequivalent_pairs_are_rejected(self, correct, student):
        is_correct, _ = _answers_equivalent(correct, student)
        assert is_correct is False


class TestNonNumericInput:
    """Alphabetic answers still travel the symbolic path, not the string
    fallback. SymPy's ``split_symbols`` transformation (bundled into
    ``implicit_multiplication_application``) parses a word like ``"hello"``
    as a product of single-letter symbols — ``h*e*l*l*o`` → ``e*h*l**2*o`` —
    so ``parse_expr`` never raises. Equality is therefore decided
    symbolically (used_symbolic_check=True); the string-compare branch only
    triggers on genuinely unparseable input.
    """

    def test_matching_words_compare_equal(self):
        # "hello" and "Hello" both lower-case to "hello" and parse to the
        # same symbol product, so simplify(diff) == 0 on the symbolic path.
        is_correct, used_symbolic = _answers_equivalent("hello", "Hello")
        assert is_correct is True
        assert used_symbolic is True

    def test_non_matching_words_are_rejected(self):
        # "hello" → e*h*l**2*o, "world" → d*l*o*r*w — distinct products, so
        # the symbolic path correctly reports them as not equivalent.
        is_correct, used_symbolic = _answers_equivalent("hello", "world")
        assert is_correct is False
        assert used_symbolic is True


class TestEdgeCases:
    @pytest.mark.parametrize("correct,student", [
        ("", "5"),
        ("5", ""),
        ("", ""),
        (None, "5"),
        ("5", None),
    ])
    def test_empty_or_none_inputs_return_false(self, correct, student):
        is_correct, _ = _answers_equivalent(correct, student)
        assert is_correct is False
