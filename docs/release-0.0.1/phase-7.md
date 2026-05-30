# Phase 7 — Adaptive engine v3 + smarter LLM diagnosis

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The three concerns

After running the app for real, three behaviours felt off:

1. **Random first exercise, then full lock-in.** A brand-new student got a uniformly random topic on their first exercise (potentially Factorization, before ever seeing Addition). After their first wrong answer, every subsequent adaptive exercise came from that same weak node until it aged out of the 30-day window or stopped being the top weakness. No variety, no review of mastered material.

2. **No "you've fixed it" signal.** A weakness identified on Day 1 remained visible on the dashboard for ~30 days regardless of how much subsequent practice the student did on that topic. There was no offset for correct answers — the count was only ever decremented by recency-window ageing.

3. **LLM misattributed errors to the topic, not the underlying skill.** The example: `Solve for x: 3x - 4 = 11`, student answers `3`. The actual mistake is computing `15 / 3 = 3` (a division slip), but the LLM tended to call this an `ALGEBRA_LINEAR` weakness because the question looked algebraic. The student then got served more algebra questions when the real gap was in arithmetic division.

---

## 2. The fixes

### 2.1 Curriculum cold-start + 70/30 mix (concern 1)

**Cold start** — `pickColdStartNode(studentId)` walks the curriculum graph based on the student's current mastery state:

1. Compute the mastered set (every node where the student's last 3 attempts are all correct).
2. Find the easiest **unmastered** node whose prerequisite is satisfied (prereq is null → it's a root, OR prereq is in the mastered set). Ties on difficulty broken by alphabetical code.
3. If everything is mastered, fall back to a random review pick from attempted nodes.

This unfolds the curriculum naturally as the student progresses:

| Mastered | Next exercise |
|---|---|
| `{}` | `ARITH_ADDITION` (root, diff 1) |
| `{ADDITION}` | `ARITH_SUBTRACTION` (diff 1, prereq mastered) |
| `{ADD, SUB}` | `ARITH_MULTIPLICATION` (diff 2) |
| `{ADD, SUB, MULT}` | `ARITH_DIVISION` (diff 2) |
| `{ADD, SUB, MULT, DIV}` | `FRACTIONS_SIMPLIFY` (diff 3) |

> **Note:** The initial Phase 7 implementation had a bug here — it picked uniformly at random from *previously-attempted* nodes only. A student who'd only touched `ARITH_ADDITION` (which is what cold-start handed them on day one) was stuck on Addition forever because their attempted set was `{ARITH_ADDITION}` — the only choice. Fixed in commit after Phase 7. See `pickColdStartNode` Javadoc for the worked example.

**Exploration mix** — after the adaptive path identifies a target node, with probability `0.30` (`EXPLORATION_RATE`) we swap that target for a previously-attempted **non-weakness** node. The student sees a review/familiar exercise roughly 1 in 3 adaptive sessions, breaking the monotony and giving spaced-repetition-lite over mastered material.

```
WITH probability 0.30:
  attempted_codes = distinct nodes the student has touched
  candidates = attempted_codes \ {weakness_target}
  if candidates non-empty:
      return random pick from candidates    ← exploration
  else:
      return weakness_target                ← only one node touched; can't explore
ELSE:
  return weakness_target                    ← 70% of the time, drill the weakness
```

### 2.2 Mastery signal: N=3 consecutive correct (concern 2)

New service method `isMastered(studentId, nodeCode)`:

```java
List<InteractionLog> recent =
    findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(
        studentId, nodeCode, PageRequest.of(0, 3));

if (recent.size() < 3) return false;
return recent.stream().allMatch(InteractionLog::isCorrect);
```

The query takes the student's last 3 attempts **on that specific tested node** (newest first) and returns true only if all 3 are correct. Khan-Academy-style: simple, defensible, no extra schema.

This signal is then applied in two places:

- `getWeaknessSummary` filters mastered codes out of the dashboard's "weak areas" list.
- `pickAdaptiveTargetNode` removes mastered codes from the failure-count map **before** picking the top weakness, so the adaptive engine stops steering toward topics the student has demonstrably fixed.

Concrete consequence: the moment a student answers a third consecutive question correctly on a topic, that topic disappears from the dashboard and stops being served. No 30-day wait.

One subtlety worth calling out: mastery is measured on the **tested_node** (what the question was about), not on the **ai_identified_weakness_code** (what the LLM blamed). When the LLM says "your linear-equation mistake is actually a division weakness," the adaptive engine starts serving you division exercises (tested_node = ARITH_DIVISION). Getting 3 of those right resolves the ARITH_DIVISION weakness, even though it was originally identified on an algebra question. That's the right pedagogical model: the system gave you the right practice; you demonstrated the underlying skill is solid.

### 2.3 Step-by-step LLM diagnosis with worked example (concern 3)

Two coordinated changes — Spring Boot sends more context, FastAPI uses it.

**Spring Boot** — `MathEvaluationRequestDto` gained two new fields:

- `node_code`: the canonical code of the topic the question is testing
- `prerequisite_codes`: the prereq chain from the seeded graph

`AiEvaluationService` now walks the tested node's `prerequisiteNode` chain (cycle-guarded, max 10 hops) and includes the result in the request.

**FastAPI** — the prompt was rewritten. Three changes that compound:

1. **Topic + prereq context.** The LLM now knows *what kind* of question it's analysing and which skills are presumed prerequisites.
2. **Explicit chain-of-thought instruction.** "REASON STEP BY STEP. Mentally reconstruct the sequence of operations the student most likely performed to reach their answer. Identify the FIRST step where they went wrong."
3. **Worked example as a few-shot demo.** The exact scenario you described — `3x - 4 = 11`, student answers 3 — is in the prompt with the full step-by-step breakdown ending in `weakness_node: ARITH_DIVISION`. That single example does most of the heavy lifting: it shows the LLM both *the kind of reasoning to perform* and *the kind of attribution to make*.

The prompt also adds a clear preference rule: "Prefer a prerequisite code over the topic code itself when the error is purely computational. Only attribute the weakness to the topic itself if the student misunderstood the *method* (e.g. didn't isolate x correctly), not the arithmetic."

---

## 3. Files changed

| File | Change |
|---|---|
| `repository/InteractionLogRepository.java` | Added `findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(...Pageable)` for the mastery check; added `findDistinctAttemptedNodeCodesByStudentId` for the cold-start and exploration paths |
| `service/StudentProgressService.java` | New constants `MASTERY_THRESHOLD = 3`, `EXPLORATION_RATE = 0.30`. New helpers `isMastered`, `pickColdStartNode`, `pickCurriculumStart`, `pickExplorationNode`. Refactored `pickAdaptiveTargetNode` to drop mastered candidates and apply the 70/30 mix. Refactored `getWeaknessSummary` to filter mastered codes |
| `dto/MathEvaluationRequestDto.java` | Added `node_code` and `prerequisite_codes` fields with `@JsonProperty` |
| `service/AiEvaluationService.java` | Walks the tested node's prereq chain and passes it on every call to FastAPI. New helper `collectPrerequisiteChain` |
| `ai-python/main.py` | `MathEvaluationRequest` gained optional `node_code` and `prerequisite_codes` fields. Prompt rewritten with step-by-step instruction + the user's `3x-4=11 → 3` worked example showing ARITH_DIVISION attribution |
| `frontend-flutter/lib/screens/login_screen.dart` | Small type-correctness fix from Phase 6 (`fieldErrors?[]` since the map is nullable on the base class) |
| `frontend-flutter/lib/screens/register_screen.dart` | Same |

---

## 4. Behaviour walkthrough — the three target scenarios

### A. Brand-new student opens the app and taps "Start Practice"

| Before | After |
|---|---|
| Random node (could be Factorization). Student is overwhelmed. | `ARITH_ADDITION` (the curriculum root). Deterministic. |

### B. Student got `3x - 4 = 11` wrong, answered 3

| Before | After |
|---|---|
| LLM said `ALGEBRA_LINEAR`. Dashboard showed "Linear Equations" weakness. Next 10 exercises were all linear-equation problems. | LLM (with the new prompt + worked example) says `ARITH_DIVISION`. Dashboard shows "Division" weakness. Next exercises are mostly division problems — with a 30% chance of an unrelated review topic for variety. |

### C. Student fixes the weakness with 3 correct division answers

| Before | After |
|---|---|
| Division still on the dashboard for ~30 days because the original failure count never decremented. Adaptive engine still served division. | The third consecutive correct division answer triggers `isMastered = true`. Division disappears from the dashboard immediately. Adaptive engine stops steering toward it. If no other weaknesses exist, the engine falls back to exploration over the student's familiar topics. |

### D. Student fails Multiplication once and then never returns to it

| Before | After |
|---|---|
| Multiplication served on every adaptive request for 30 days, even if the student wanted variety. | 70% of adaptive requests serve Multiplication; 30% serve a different familiar topic. Within ~3 sessions the student will see at least one non-Multiplication exercise. |

---

## 5. Known limitations and follow-ups

1. **`isMastered` is a per-request DB query.** Every weakness entry on the dashboard triggers a small "last-3 attempts on this node" query (so up to 3 queries per `/progress` call, up to 1 per candidate in `pickAdaptiveTargetNode`). At the seeded scale this is cheap, but at scale it'd be worth caching mastery state per session.

2. **Exploration is uniform random among attempted nodes.** A smarter exploration would weight toward recently-mastered nodes (true spaced repetition) or weakest-mastered nodes. For now any non-weakness node is equally likely.

3. **The seeded prereq DAG is shallow.** `ALGEBRA_LINEAR`'s explicit prereqs are only `ARITH_SUBTRACTION → ARITH_ADDITION`. Solving `3x - 4 = 11` actually requires multiplication and division too, but they're not in the seeded chain — the LLM has to infer that from context. The prompt's worked example and the "prefer prereq when computational" instruction handle this in practice, but enriching the graph (multiple prereqs per node, i.e. a true DAG instead of a tree) would give the engine more precise data.

4. **No A/B comparison of the new prompt vs. the old.** The improvement is qualitative — based on the worked example tracking the user's reported scenario. A proper evaluation (50+ hand-graded wrong-answer cases, accuracy of `weakness_node` attribution before vs. after) is the right way to validate. Worth doing as a separate "model evaluation" effort.

5. **`isMastered` checks correctness only, not difficulty.** A student who answered 3 easy multiplication questions correctly (`7 × 8`) is treated the same as one who answered 3 hard ones (`13 × 17`). Difficulty-aware mastery would be more accurate but adds complexity.

6. **No surfacing of mastery in the UI.** When a weakness resolves it just disappears — there's no "🎉 You mastered Division!" celebration. Easy UI win in a future polish pass.

---

## 6. How to verify locally

```bash
# 1. Rebuild ai-service (new prompt + extended model)
docker compose build ai-service && docker compose up -d ai-service

# 2. Restart Spring Boot (new DTO fields + service logic)
./mvnw spring-boot:run -pl backend-springboot/math-wise-backend

# 3. Verify the LLM gets the new context — submit the worked-example case:
curl -X POST http://localhost:8000/evaluate-error \
  -H "Content-Type: application/json" \
  -d '{
    "equation": "Solve for x: 3x - 4 = 11",
    "correct_answer": "5",
    "student_answer": "3",
    "node_code": "ALGEBRA_LINEAR",
    "prerequisite_codes": ["ARITH_SUBTRACTION", "ARITH_ADDITION"]
  }'
# Expect (Llama 3.2 8B will be more reliable than 3B):
#   weakness_node: "ARITH_DIVISION"
#   explanation:   something mentioning the division slip 15/3

# 4. Curriculum cold-start
# Create a brand-new student. Tap "Start Practice" without doing anything else.
# Should always get ARITH_ADDITION on the first request.

# 5. Mastery dissolves a weakness
# Fail one division question deliberately. Dashboard shows ARITH_DIVISION.
# Pick the weakness from the dashboard 3 times in a row and answer each correctly.
# Refresh the home screen — Division should be gone.

# 6. 70/30 exploration
# Build a known weakness (one wrong answer is enough). Tap "Start Practice"
# repeatedly — you should see the weakness topic ~7 times out of 10, and a
# different topic ~3 times out of 10.
```

---

## 7. Why this was Phase 7

Phases 1–6 made the system *correct, resilient, configurable, adaptive, validated, and usable*. Phase 7 is the first round of "adaptive engine subtleties" — the kind of behaviour you only discover after using the app for real. The three concerns here aren't bugs; they're places where the algorithm was too simple to match how learning actually works. The curriculum cold-start, the explicit mastery signal, and the chain-of-thought LLM prompt are each small individually, but together they meaningfully change what students experience day to day.
