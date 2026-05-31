# Solving Linear Equations in One Variable

A linear equation in one variable has the shape **ax + b = c** (or any rearrangement of it), where a, b, c are known constants and x is the unknown. Solving means isolating x on one side of the equation by undoing the operations attached to it, in reverse order.

## The "inverse operations" procedure

To solve 3x - 4 = 11:
1. Identify what's attached to x. Reading the left side: x is multiplied by 3, and then 4 is subtracted. To isolate x we must undo these — but in **reverse order**: undo the subtraction first, then the multiplication.
2. Undo the subtraction: add 4 to both sides. 3x - 4 + 4 = 11 + 4, giving **3x = 15**.
3. Undo the multiplication: divide both sides by 3. 3x ÷ 3 = 15 ÷ 3, giving **x = 5**.

The cardinal rule: whatever you do to one side, you must do to the **other side** as well, or the equation becomes false. This is the principle that justifies every legal move.

## Checking the answer

Substitute the candidate solution back into the original equation. For x = 5: 3(5) - 4 = 15 - 4 = 11. ✓ Matches the right side, so x = 5 is correct.

## Common student errors

- **Doing the operation on only one side**: subtracting 4 from the left to get 3x = 11. The right side wasn't touched, so the equation is no longer balanced. Result: x = 11/3, badly wrong.
- **Reversing the wrong way**: dividing first by 3 (giving x - 4/3 = 11/3), then trying to add 4. Technically legal but algebraically clumsy and error-prone — the conventional order is to undo addition/subtraction first because it keeps the constants on one side and the variable on the other.
- **Arithmetic slip in the final step**: the algebra is perfect but 15 ÷ 3 is computed as 3 instead of 5. The weakness is **ARITH_DIVISION**, not algebra.
- **Sign error when moving a term across the equals sign**: in 3x - 4 = 11, adding 4 to the right but forgetting to add 4 to the left, OR writing the move as "the -4 becomes +4 when it crosses" but applying it to the wrong side.
- **Combining like terms incorrectly**: in 2x + 3x = 10, writing 5x = 10 correctly but in 2x + 3 = 5x + 7, trying to "combine" the 3 and the 5x by adding them.

The diagnostic shortcut: when the algebra steps look right but the final number is off, the slip is arithmetic — diagnose the specific operation that went wrong (ARITH_ADDITION / SUBTRACTION / MULTIPLICATION / DIVISION). When intermediate equations don't balance, the student broke the symmetry rule.
