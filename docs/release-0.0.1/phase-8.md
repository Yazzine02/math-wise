# Phase 8 — AI exercise generation + deterministic fallback

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The problem

The seed data in `DataSeeder` ships with exactly 4 exercises per knowledge node — 32 total across all 8 nodes. A student practising a single topic exhausts the variety in their second session. The adaptive engine's random selection within a node delivers visible repetition very quickly. The cure ("just hand-write more questions") doesn't scale.

What we actually want is an **inexhaustible pool of valid exercises** that grows on demand — without trusting an LLM to be correct.

---

## 2. The design

A two-tier generation pipeline that lives in FastAPI, called from Spring Boot in the background:

```
                    ┌──────────────────────────────────┐
                    │  Student requests next exercise  │
                    └──────────────────────────────────┘
                                    │
                                    ▼
                    ┌──────────────────────────────────┐
                    │  StudentProgressService          │
                    │  pool.size() < MIN_POOL_SIZE ?   │
                    └──────────────────────────────────┘
                              yes │            no
                                  │             │
                                  ▼             ▼
        ┌──────────────────────────────┐   serve from
        │ Publish ExercisePoolLowEvent │   current pool
        │ (in-process; fire and forget)│   (immediate)
        └──────────────────────────────┘
                    │
            user request returns
                    │
                    ▼  (async, separate thread)
        ┌─────────────────────────────────────────┐
        │  ExercisePoolListener  → @Async         │
        │  ExerciseGenerationService.topUpPoolFor │
        └─────────────────────────────────────────┘
                    │
                    ▼  HTTP POST
        ┌─────────────────────────────────────────┐
        │  FastAPI /generate-exercises            │
        │                                         │
        │  Tier 1 — LLM:                          │
        │    1. Per-node prompt asks for          │
        │       {question_text, math_expression,  │
        │        expected_answer, difficulty}     │
        │    2. SymPy parses math_expression and  │
        │       computes the canonical answer     │
        │    3. Reject if SymPy can't verify, if  │
        │       the LLM's claim doesn't match,    │
        │       or if the answer's shape doesn't  │
        │       fit the node (e.g. non-integer    │
        │       result on ARITH_DIVISION)         │
        │                                         │
        │  Tier 2 — Deterministic templates:      │
        │    Runs if 0 LLM candidates pass.       │
        │    Pure Python per-node generators,     │
        │    correct by construction.             │
        └─────────────────────────────────────────┘
                    │
                    ▼
        Spring Boot persists each exercise with
        generated_by_ai = true (LLM) or false (templates).
```

The student's request **never** blocks on the LLM. The user-facing latency stays at "serve from current pool" speed.

---

## 3. Why publish a domain event rather than calling the service directly?

`StudentProgressService` doesn't depend on `ExerciseGenerationService`. It publishes `ExercisePoolLowEvent` to Spring's `ApplicationEventPublisher`. The listener — a separate component in `service/ExercisePoolListener.java` — receives it via `@Async @EventListener` and calls the generation service.

The reason is the eventual Kafka migration in **Phase 13**. When the AI generation becomes its own microservice fed by a Kafka topic (`exercise.pool.low`), this listener becomes a `@KafkaListener` reading from the topic. The publisher in `StudentProgressService` stays unchanged — it always just publishes a domain event, doesn't care about the transport. That's the port-and-adapter discipline.

So: today this is an in-process async dispatcher. Tomorrow it's a distributed log. The producer code is unaware of the change.

---

## 4. Why not just use Kafka now?

Discussed at length in this phase's design conversation. Short version: Kafka here would be over-engineering. The volume is "5 exercises every few minutes per node," not millions per second. Adding a broker, Zookeeper/KRaft, topics, consumer offsets, and the operational burden doesn't solve a problem we actually have. Spring's `@Async` + event publisher gives us identical *semantics* (publisher decoupled from consumer; consumer runs on a separate thread) with zero new infrastructure.

Kafka has a real home in this project — Phase 13, event-sourcing the `InteractionLog` stream — where it fans out to multiple independent consumers. Not here.

---

## 5. SymPy verification per node type

The LLM is treated as a *question writer*, not an *answer authority*. Every candidate gets validated by SymPy before persistence. The validator differs by node type because the math being checked differs:

| Node | Validator | Sanity rule |
|---|---|---|
| `ARITH_ADDITION/SUBTRACTION/MULTIPLICATION` | `simplify(math_expression - expected_answer) == 0` | — |
| `ARITH_DIVISION` | Same, **plus** `canonical.is_integer` | Reject non-integer answers — "1 ÷ 3 = 1/3" isn't an arithmetic-division exercise |
| `FRACTIONS_SIMPLIFY` / `FRACTIONS_ADD_SUB` | `simplify(math_expression - expected_answer) == 0` | SymPy auto-reduces, so the stored answer is always in lowest terms |
| `ALGEBRA_LINEAR` | `sympify(math_expression)` must be `Eq(...)`. `solve(eq, x)` must return exactly one solution matching expected_answer | Reject ambiguous equations |
| `ALGEBRA_FACTORIZE` | `expand(expected_answer - math_expression) == 0` | The factored form, expanded, must equal the original polynomial |

A key detail: **the stored `correct_answer` is SymPy's canonical form, not the LLM's claim**. Even when they agree numerically, we trust SymPy's string representation. Consequence: fractions are always reduced, polynomial signs are always normalised, etc. The student then gets the SymPy-canonical answer when they're tested with these exercises.

---

## 6. The deterministic templates

`ai-python/exercise_templates.py` has one pure-Python generator per node:

```python
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
```

Each generator constructs the problem **starting from the answer** — that's what makes them correct by construction. For factorization we pick two roots and expand to get the polynomial; for linear equations we pick the solution x and reconstruct ax + b = ax + b. No parsing, no LLM, no SymPy round-trip needed.

These functions serve two roles:

1. **In-endpoint fallback.** When the LLM produces zero valid candidates (offline, malformed JSON, all answers wrong), `/generate-exercises` uses the templates instead and returns them with `used_fallback: true`.
2. **Safeguard.** If Ollama is down for a week, the templates keep the app full of exercises forever. Even if the LLM path never runs, the pool stays healthy.

---

## 7. Files

### New

| File | Purpose |
|---|---|
| `ai-python/exercise_templates.py` | 8 deterministic generators + dispatch table |
| `backend-springboot/.../dto/GenerateExercisesRequestDto.java` | Spring → FastAPI request |
| `backend-springboot/.../dto/GenerateExercisesResponseDto.java` | Spring ← FastAPI response (incl. `rejectedCount`, `usedFallback`) |
| `backend-springboot/.../dto/GeneratedExerciseDto.java` | One returned exercise |
| `backend-springboot/.../event/ExercisePoolLowEvent.java` | Domain event (record carrying `nodeCode`) |
| `backend-springboot/.../service/ExerciseGenerationService.java` | Calls FastAPI, persists results, `@Transactional` |
| `backend-springboot/.../service/ExercisePoolListener.java` | `@Async @EventListener` consumer of the event |

### Modified

| File | Change |
|---|---|
| `ai-python/main.py` | New request/response models. Per-node `GENERATION_PROMPTS`. `_verify_arithmetic` / `_verify_linear` / `_verify_factorize`. `POST /generate-exercises` endpoint with LLM → template fallback |
| `entity/Exercise.java` | New `generatedByAi` boolean column with DB-level default (handles existing rows) |
| `repository/ExerciseRepository.java` | New `countByKnowledgeNode` for cheap pool-size checks |
| `MathWiseBackendApplication.java` | `@EnableAsync` — without it `@Async` is a silent no-op |
| `service/StudentProgressService.java` | Injects `ApplicationEventPublisher`. Publishes `ExercisePoolLowEvent` when the chosen target node's pool is below `MIN_POOL_SIZE = 8` |

---

## 8. Configuration knobs

| Constant | Where | Value | What it does |
|---|---|---|---|
| `MIN_POOL_SIZE` | `ExerciseGenerationService.java` | 8 | Triggers pool top-up below this threshold |
| `BATCH_SIZE` | `ExerciseGenerationService.java` | 5 | Exercises requested per LLM call (amortises round-trip) |
| `count` cap | FastAPI `generate_exercises` | 1-20 | Defends against accidental large generations |
| `GENERATION_PROMPTS` per-node | `main.py` | (inline) | The LLM instructions per topic — tweak for quality |

---

## 9. Before / after, concrete

### Scenario A: student exhausts the seeded Subtraction pool

Before Phase 8 — once you've seen the 4 seeded Subtraction exercises, repeats are immediate.

After Phase 8 — the 4th request publishes `ExercisePoolLowEvent` (since `4 < 8`). FastAPI generates 5 more via LLM + SymPy verification. By the time the student asks for their 5th exercise, the pool has 4 + 5 = 9 entries. Repeats become much rarer; over time the pool keeps growing.

### Scenario B: Ollama is down

Before — the existing answer-evaluation flow would error (we mostly fixed this in Phase 2). But generation was never an option, so this didn't apply.

After — `ExerciseGenerationService` catches the failure silently. FastAPI's `/generate-exercises` returns `used_fallback: true` with deterministic-template exercises. The pool keeps growing. The student never sees a difference. Logs record `Topped up X with 5 exercises (rejected 0 LLM candidates, fallback=true)`.

### Scenario C: LLM returns 5 candidates, 2 fail SymPy verification

Real-world expectation with `llama3.2:3b`. Validation rejects the 2 bad ones, persists the 3 good ones, and logs the rejection count. `used_fallback` stays false. The pool grows by 3 instead of 5; the next pool-low event will be slightly sooner.

### Scenario D: LLM returns 5 candidates, all wrong

Validation rejects all 5. FastAPI falls back to templates and returns 5 deterministic exercises with `used_fallback: true`. Same as scenario B.

---

## 10. Known limitations and follow-ups

1. **No per-student exposure tracking.** A student can theoretically see the same exercise twice if the random selection within a node picks it again. With pool growing over time this becomes vanishingly likely, but a `seen_exercises` join table would eliminate it entirely.
2. **Generated exercises pool globally.** All students draw from the same expanding pool. Per-student personalised generation (e.g. exercises shaped around a student's specific misconception) is a future refinement that would benefit from the Phase 7 misconception-library idea.
3. **No retry on LLM failure.** If the LLM returns garbage *and* the templates run, that's fine. But if the LLM call itself errors at the HTTP layer, we don't retry — the next pool-low event will get another shot.
4. **Templates are simple.** Each template generator produces problems within a fixed parameter range. Over time students may notice the pattern. Future work: parameter randomisation strategies (different operand sizes, occasional twists like "the answer is zero").
5. **Migration to Kafka.** The `ExercisePoolLowEvent` → `ExercisePoolListener` glue stays in-process for now. Phase 13 swaps this for a Kafka topic — no producer or consumer code change.

---

## 11. How to verify locally

```bash
# 1. Rebuild ai-service so the new endpoint + exercise_templates is in the image
docker compose build ai-service && docker compose up -d ai-service

# 2. Verify the endpoint directly. Use a small node first to see template output.
curl -sS -X POST http://localhost:8000/generate-exercises \
  -H "Content-Type: application/json" \
  -d '{"node_code":"ARITH_ADDITION","count":3}' | jq .
# Expect: {"exercises":[3 items], "rejected_count":N, "used_fallback":bool}

# 3. Force the deterministic fallback by stopping Ollama
docker compose stop ollama
curl -sS -X POST http://localhost:8000/generate-exercises \
  -H "Content-Type: application/json" \
  -d '{"node_code":"ARITH_DIVISION","count":3}' | jq .
# Expect: 3 exercises, used_fallback: true
docker compose start ollama

# 4. Restart Spring Boot to pick up @EnableAsync + the new service/listener
./mvnw spring-boot:run -pl backend-springboot/math-wise-backend

# 5. Inside the Flutter app, drill into a single topic enough times to drop
# its pool below 8. Watch Spring Boot logs — you should see entries like:
#   INFO  ExerciseGenerationService — Topped up ARITH_SUBTRACTION
#         with 5 exercises (rejected 1 LLM candidates, fallback=false)
# A few moments later, refresh the next-exercise call; the pool has grown.

# 6. Verify the column was added and is being set correctly:
psql -h localhost -U db_user -d mathwise_db -c \
  "SELECT generated_by_ai, COUNT(*) FROM exercises GROUP BY generated_by_ai;"
# Expect: rows for both true and false (true for LLM-generated, false for seed + fallback).
```

---

## 12. Why this was Phase 8

Phases 1–7 made the app's existing 32 exercises feel trustworthy, adaptive, and well-grounded. But "32 exercises" was always going to be the limiting factor for a real product. Phase 8 changes that ceiling. The LLM path makes the pool grow with use; the deterministic templates ensure it never breaks. Combined, the app now has an architecturally-clean answer to "where do exercises come from?" — they come from a pipeline whose worst-case behaviour is "deterministic, correct, slightly boring" and whose best-case behaviour is "fresh, varied, LLM-creative-but-SymPy-verified." That's a substantially more mature foundation for the architectural work on the roadmap (microservices, Kafka, observability) to build on.
