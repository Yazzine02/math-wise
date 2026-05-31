# Factorizing Quadratic Expressions

Factorizing a quadratic x² + bx + c means rewriting it as a product of two linear factors **(x + p)(x + q)**. The factored form makes the roots of the equation visible (x = -p and x = -q solve the equation x² + bx + c = 0) and is the algebraic inverse of expanding.

## The pair-finding procedure

Given x² + bx + c, the goal is to find two integers **p** and **q** such that:
- **p + q = b** (the coefficient of x)
- **p × q = c** (the constant term)

Worked example — factorize x² + 5x + 6:
1. Need p and q with p + q = 5 and p × q = 6.
2. Pairs of integers multiplying to 6: (1, 6), (2, 3), (-1, -6), (-2, -3). Of these, only (2, 3) sums to 5.
3. Factored form: **(x + 2)(x + 3)**.

Verify by expanding (FOIL): (x + 2)(x + 3) = x² + 3x + 2x + 6 = x² + 5x + 6. ✓

## Signs of p and q

- If c is **positive** and b is **positive**: both p and q are positive.
- If c is **positive** and b is **negative**: both p and q are negative.
- If c is **negative**: p and q have **opposite signs**, and the larger-magnitude one shares its sign with b.

Example — factorize x² - x - 6:
1. Need p + q = -1 and p × q = -6.
2. Pairs multiplying to -6: (1, -6), (-1, 6), (2, -3), (-2, 3). Of these, (2, -3) sums to -1.
3. Factored form: **(x + 2)(x - 3)**.

## Common student errors

- **Sign error on the pair**: choosing the correct magnitudes of p and q but flipping a sign — e.g. factorizing x² - 5x + 6 as (x + 2)(x + 3) instead of (x - 2)(x - 3). The product still gives c, but the sum is now +5 instead of -5. The expansion check catches this immediately.
- **Pair sums to b instead of multiplies to c (or vice versa)**: confusing the two conditions. For x² + 5x + 6, choosing (1, 4) because 1 + 4 = 5, ignoring that 1 × 4 = 4 ≠ 6.
- **Forgetting to include the leading coefficient when a ≠ 1**: for 2x² + 7x + 3, treating it as x² + 7x + 3. The "AC method" or grouping must be used when the leading coefficient isn't 1.
- **Arithmetic slip when expanding to check**: the factored form is correct but the verification expansion is computed wrong, leading the student to believe their answer is wrong and redo it incorrectly.

The diagnostic shortcut: when the student's factored form expands to the right c but the wrong b, the pair satisfies p × q = c but not p + q = b — they searched the wrong condition. When the sign on b is flipped, both p and q signs need flipping.
