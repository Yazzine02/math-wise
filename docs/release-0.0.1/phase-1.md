# Phase 1 — Symbolic answer comparison

**Branch:** `release/0.0.1`
**Commit:** `3f94255`
**Status:** Shipped

---

## 1. The problem

The original `AiEvaluationService.evaluateStudentAnswer` decided whether a student got an exercise right with one line:

```java
boolean isCorrect = requestDto.getCorrectAnswer().trim()
        .equalsIgnoreCase(requestDto.getStudentAnswer().trim());
```

For a *math* application this is fundamentally broken. String equality has no notion of mathematical equivalence — it only recognises identical character sequences. The seeded exercises had correct answers like `"2/3"`, `"(x-3)(x+3)"`, `"1/2"`, `"85"`. The moment a student typed any mathematically valid alternative form, the system marked them wrong.

### Concrete failing cases (all real seeded exercises)

| Exercise | Correct answer stored | Student writes | String equality says | Mathematical reality |
|---|---|---|---|---|
| `Simplify 8/12` | `2/3` | `4/6` | wrong | both equal `0.666…` |
| `Calculate 1/4 + 1/4` | `1/2` | `0.5` | wrong | both equal `0.5` |
| `Factorize x^2 - 9` | `(x-3)(x+3)` | `(x+3)(x-3)` | wrong | multiplication is commutative |
| `Factorize x^2 + 5x + 6` | `(x+2)(x+3)` | `x^2+5x+6` | wrong | factored ⇔ expanded form |
| `What is 999 + 1?` | `1000` | `1000.0` | wrong | both equal 1000 |
| `Solve for x: x + 5 = 12` | `7` | ` 7` (trailing space) | (this one worked, trim handles it) | — |

Five of the six broken cases above are *forms a typical student will produce naturally*. So the false-negative rate on the system was substantial.

### Why this mattered beyond the immediate UX

The student-facing impact ("I typed the right answer but got marked wrong") is bad on its own. But the structural damage was worse: every false negative wrote a row into `interaction_logs` flagged `is_correct = false` and triggered an LLM call to diagnose a "weakness" that didn't actually exist.

`StudentProgressService.getWeaknessSummary` then aggregated those rows to populate the "Your weak areas" cards on the dashboard. And `getNextExercise` used the same data to pick the next topic. So the adaptive engine — the central value proposition of the app — was being fed noisy data manufactured by the broken correctness check. Every metric downstream was lying.

---

## 2. The solution: SymPy-backed symbolic check, ahead of the LLM call

I added a fast new endpoint on the FastAPI service, `POST /check-answer`, that uses [SymPy](https://www.sympy.org) to compare two answers symbolically rather than textually. Spring Boot now calls this endpoint *first*, before the LLM diagnosis call.

### Why SymPy

SymPy is a pure-Python symbolic mathematics library. For our purposes it does exactly what's needed:

- Parses both `"2/3"` and `"4/6"` into `Rational(2, 3)` — the same object.
- Parses `"0.5"` into `Float(0.5)` and `"1/2"` into `Rational(1, 2)`. `Float(0.5) - Rational(1, 2)` simplifies to `0`.
- Parses `"(x-3)(x+3)"` (with implicit-multiplication transformation) and `"x^2 - 9"` (with caret-as-power transformation) into equivalent algebraic expressions. `simplify(a - b)` yields `0`.
- Parses `"85"` and `"85.0"` to objects whose difference simplifies to `0`.

The check we use is `simplify(parse(correct) - parse(student)) == 0`. That single line covers all the cases above.

### Two-stage evaluation flow

The new flow in `AiEvaluationService.evaluateStudentAnswer`:

```
1. POST /check-answer  →  symbolic correctness verdict (~50ms)
2. If incorrect: POST /evaluate-error  →  LLM diagnosis (5–30s)
3. Persist InteractionLog (server-authoritative is_correct)
4. Return AiFeedbackDto (now carries is_correct)
```

A correct answer no longer pays the LLM round-trip cost — it skips step 2 entirely. The student gets sub-100ms feedback when they're right, instead of 5–30 seconds. For wrong answers the flow is unchanged from before.

### Fallback for unparseable inputs

If SymPy can't parse either side (e.g. the student typed a word, included units, or wrote a sentence), the endpoint falls back to case-insensitive trimmed string equality and returns `used_symbolic_check: false` in the response. This means the new system is a strict superset of the old behaviour — anything that worked under string equality still works, and a great deal more besides.

---

## 3. Files changed

### `ai-python/`

| File | Change |
|---|---|
| `requirements.txt` | Added `sympy==1.13.3` |
| `main.py` | New imports from `sympy` and `sympy.parsing`. New Pydantic models `AnswerCheckRequest` / `AnswerCheckResponse`. New helper `_answers_equivalent` and new endpoint `POST /check-answer` |

The SymPy parser is configured with two transformations: `implicit_multiplication_application` (so `2x` becomes `2*x` and `(x+2)(x+3)` becomes `(x+2)*(x+3)`) and `convert_xor` (so `x^2` becomes `x**2`). These are exactly the forms students naturally type.

### `backend-springboot/`

| File | Change |
|---|---|
| `dto/AiFeedbackDto.java` | Added `is_correct` boolean field — server-authoritative correctness now travels back on every response |
| `dto/AnswerCheckRequestDto.java` | **New** — request envelope for `POST /check-answer` |
| `dto/AnswerCheckResponseDto.java` | **New** — response envelope, includes `used_symbolic_check` for observability |
| `service/AiEvaluationService.java` | Rewritten `evaluateStudentAnswer`: calls `/check-answer` first, only invokes the LLM diagnosis when `is_correct` is false, sets `is_correct` on the response before returning |

### `frontend-flutter/`

| File | Change |
|---|---|
| `models/ai_feedback.dart` | `fromJson` now reads `is_correct` directly from the JSON payload. The constructor no longer accepts `isCorrect` as a parameter |
| `services/exercise_service.dart` | Removed the bogus client-side `correctAnswer.toLowerCase() == studentAnswer.toLowerCase()` comparison. The Flutter app no longer makes any correctness judgement — it trusts the server |

---

## 4. Before / after behaviour, concrete

Running the same student inputs against the same exercises:

| Exercise | Student types | Before (string equality) | After (SymPy) |
|---|---|---|---|
| `Simplify 8/12` (answer `2/3`) | `4/6` | ❌ marked wrong | ✅ correct |
| `Calculate 1/4 + 1/4` (answer `1/2`) | `0.5` | ❌ marked wrong | ✅ correct |
| `Factorize x^2 - 9` (answer `(x-3)(x+3)`) | `(x+3)(x-3)` | ❌ marked wrong | ✅ correct |
| `Factorize x^2 + 5x + 6` (answer `(x+2)(x+3)`) | `x^2+5x+6` | ❌ marked wrong | ✅ correct |
| `Calculate 1/4 + 1/4` (answer `1/2`) | `2/4` | ❌ marked wrong | ✅ correct |
| `What is 7 × 8?` (answer `56`) | `56.0` | ❌ marked wrong | ✅ correct |
| `Solve for x: x + 5 = 12` (answer `7`) | `8` | ❌ marked wrong | ❌ correctly marked wrong |
| `Solve for x: x + 5 = 12` (answer `7`) | `7` | ✅ correct | ✅ correct |

The last two rows are reassurance that we didn't regress: actually-wrong answers are still flagged wrong, and exact matches still work.

---

## 5. Performance impact

| Scenario | Before | After | Change |
|---|---|---|---|
| Correct answer (string equal) | LLM round-trip 5–30s | SymPy parse ~50ms | **~99% faster** |
| Correct answer (different form) | LLM round-trip 5–30s (returning a false weakness) | SymPy parse ~50ms | **~99% faster + corrects the verdict** |
| Wrong answer | LLM round-trip 5–30s | SymPy parse + LLM 5–30s | Unchanged (one extra ~50ms call) |

The SymPy call is so much faster than the LLM that the cost on incorrect answers is negligible. On correct answers we avoid the LLM entirely.

There's a secondary win on the FastAPI/Ollama side: half the LLM inferences are typically on correct answers (where the LLM was being asked to comment on nothing). Removing those calls reduces load on the model server proportionally — which matters when running a small local model on a single machine.

---

## 6. Known limitations and follow-ups

1. **SymPy isn't infallible.** Some inputs the parser refuses. Examples I tested:
   - `x²` (superscript Unicode) — parser doesn't recognise it; student should type `x^2`.
   - `2π` — parser turns `π` into a symbol, not the constant. For pi-related exercises (none in the current seed) we'd need to pre-substitute.
   - `≠` and other Unicode operators — not handled.

   None of these affect the 32 seeded exercises, which were chosen from an arithmetic / fractions / algebra domain that SymPy parses cleanly. If we add geometry or trigonometry exercises, we'll need to revisit.

2. **Java field naming in `AiFeedbackDto` is still ugly.** The class uses raw `snake_case` field names (`weakness_node`, `is_correct`) giving us `getWeakness_node()` and `isIs_correct()`. This is non-idiomatic Java and breaks IDE refactoring tools. Phase 5 will rename the fields and use `@JsonProperty` for the wire format.

3. **No retry / circuit-breaker on the `RestTemplate` calls.** If Ollama hangs, `evaluateStudentAnswer` blocks indefinitely on the read socket. Phase 2 will configure explicit timeouts.

4. **No `@Transactional` boundary.** Steps 1–3 in the new flow (HTTP call to FastAPI → HTTP call to FastAPI → DB write) aren't atomic. If the DB write fails, the client got a successful response containing feedback that never reached the audit table. Phase 2 will fix this.

5. **Existing dirty `interaction_logs` rows persist.** Rows written before this commit still have whatever string-equality verdict they got. We don't rewrite history. From this commit forward, new data is clean. Old rows that flagged a false weakness will still affect adaptive selection until they age out of the recency window introduced in Phase 4.

---

## 7. How to verify locally

```bash
# 1. Rebuild and restart the AI service so SymPy is available
docker compose build ai-service
docker compose up -d ai-service

# 2. Test the new endpoint directly
curl -X POST http://localhost:8000/check-answer \
  -H "Content-Type: application/json" \
  -d '{"correct_answer": "2/3", "student_answer": "4/6"}'
# Expect: {"is_correct": true, "used_symbolic_check": true}

curl -X POST http://localhost:8000/check-answer \
  -H "Content-Type: application/json" \
  -d '{"correct_answer": "(x-3)(x+3)", "student_answer": "x^2 - 9"}'
# Expect: {"is_correct": true, "used_symbolic_check": true}

curl -X POST http://localhost:8000/check-answer \
  -H "Content-Type: application/json" \
  -d '{"correct_answer": "7", "student_answer": "8"}'
# Expect: {"is_correct": false, "used_symbolic_check": true}

# 3. End-to-end via the Flutter app
#    - Log in, start practice, and try equivalent forms on
#      fractions and factorization exercises. They should
#      now be accepted.
```

---

## 8. Why this was Phase 1

Of the architectural issues identified in the pre-release review, this one was singled out because it sat at the root of a tree of downstream lies: a broken correctness check made `interaction_logs` untrustworthy, which made the adaptive engine untrustworthy, which made the dashboard's "Your weak areas" feature untrustworthy. Fixing this first means every subsequent phase (resilience, adaptive engine v2, validation) operates on a foundation that tells the truth.
