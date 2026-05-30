# Phase 2 — Resilience & data integrity

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The three problems

### 1.1 No transactional boundary on `evaluateStudentAnswer`

After Phase 1, `AiEvaluationService.evaluateStudentAnswer` does three sequential operations:

1. `POST /check-answer` to FastAPI (SymPy check)
2. `POST /evaluate-error` to FastAPI (LLM diagnosis, only when wrong)
3. `interactionLogRepository.save(log)` — write the audit row

There's no `@Transactional` annotation. If step 3 fails (constraint violation, connection-pool exhaustion, foreign key issue, DB temporarily unavailable), the method has already mutated the response object and the controller will happily return it to the client. The student sees a "Correct!" celebration for an answer that was never logged.

The student-facing UI looks fine. The DB is missing a row. Three months from now you look at the analytics and the numbers don't match what people remember happening. Silent data loss is the worst category of bug.

### 1.2 No HTTP timeouts on `RestTemplate`

`RestTemplateConfig` was returning a bare `new RestTemplate()`. Spring's default has **no** connect timeout and **no** read timeout. The thread that called `restTemplate.postForObject(...)` will wait forever if Ollama hangs.

This is a real risk because:
- Llama3.2:3b is small enough to be runnable but slow enough on CPU to hit pathological cases.
- The first request after a cold start takes much longer because the model has to load into memory.
- Ollama itself occasionally hangs under load and needs a restart.

A handful of hung threads × Tomcat's default 200-thread pool = entire backend stops accepting requests. The Flutter app starts seeing connection errors on every endpoint, not just `/api/exercises/evaluate`.

### 1.3 No global error handler

When a controller method threw, Spring's default `BasicErrorController` kicked in. It returns a 500 with a body shaped like:

```json
{
  "timestamp": "...",
  "status": 500,
  "error": "Internal Server Error",
  "trace": "java.lang.IllegalArgumentException: Unknown node code: Multiplication\n\tat ...",
  "message": "Unknown node code: Multiplication",
  "path": "/api/student/next-exercise"
}
```

Two problems:
- The stack trace leaks internal types. Production servers should never do this.
- The shape isn't a contract — different exception types produce different bodies — so the Flutter client can't switch on it reliably. The current frontend code does `throw Exception(response.body)` and displays the whole thing as raw text. Users see Java stack traces.

There's also no distinction between "bad input from the client" (400), "the requested resource doesn't exist" (404), "the AI service is down" (503), and "we have a real server bug" (500). Everything became 500.

---

## 2. The fixes

### 2.1 `@Transactional` on `evaluateStudentAnswer`

One-line annotation, but it changes the semantics meaningfully:

```java
@Transactional
public AiFeedbackDto evaluateStudentAnswer(EvaluateAnswerRequestDto requestDto) { ... }
```

If `interactionLogRepository.save(log)` throws, Spring marks the transaction for rollback and the exception propagates to the controller. The `GlobalExceptionHandler` (see §2.3) maps it to an error response. The client never receives the partially-built `AiFeedbackDto` that was about to be returned.

The external HTTP calls obviously cannot be rolled back — once the LLM has generated a response, it has generated a response. But the audit trail and the client's view of "did this evaluation happen" are now consistent: either both succeed or neither does.

### 2.2 Configured `RestTemplate` timeouts

Replaced `new RestTemplate()` with:

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(60))
            .build();
}
```

Reasoning:
- **Connect timeout 3s.** On a private docker network, 3 seconds is more than enough to establish a TCP connection. If we can't, FastAPI is effectively down — fail fast instead of waiting.
- **Read timeout 60s.** Sized for the worst legitimate case: a cold-start Llama inference. Anything longer is almost certainly a hang, not slow processing. The symbolic-check endpoint completes in <100ms so it has plenty of headroom.

When the timeout fires, `RestTemplate` throws `ResourceAccessException`, which the global handler maps to HTTP 503 + code `AI_SERVICE_UNAVAILABLE`. Thread is released back to the pool.

### 2.3 `GlobalExceptionHandler` + `ErrorResponseDto`

Two new classes:

**`ErrorResponseDto`** — uniform error envelope:

```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "Unknown node code: Multiplication",
  "timestamp": "2026-05-21T14:32:18.421Z",
  "path": "/api/student/next-exercise"
}
```

The `code` field is the stable contract — text in `message` may change between releases, but codes won't. The Flutter client can branch on `code` without parsing English.

**`GlobalExceptionHandler`** — `@RestControllerAdvice` with five handlers:

| Exception | HTTP status | Code | When it fires |
|---|---|---|---|
| `IllegalArgumentException` | 400 | `INVALID_INPUT` | Unknown node code, invalid parameter values |
| `IllegalStateException` | 404 | `RESOURCE_MISSING` | "No exercises seeded", "No knowledge nodes" |
| `ResourceAccessException` | 503 | `AI_SERVICE_UNAVAILABLE` | FastAPI unreachable, connection refused, read timeout |
| `RestClientException` (other) | 502 | `AI_SERVICE_ERROR` | FastAPI returned a 4xx/5xx |
| `ResponseStatusException` | (passthrough) | (status name) | Code that explicitly chose a status, e.g. `CourseController.getLesson` → 404 |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` | Truly unexpected. Stack trace logged server-side, **never** sent over the wire |

All handlers log server-side before returning, so the operator still has the diagnostic info — they're just not leaked to the client.

---

## 3. Files changed

| File | Change |
|---|---|
| `config/RestTemplateConfig.java` | Switched to `RestTemplateBuilder` with explicit connect + read timeouts |
| `service/AiEvaluationService.java` | Added `@Transactional` import and annotation on `evaluateStudentAnswer` |
| `dto/ErrorResponseDto.java` | **New** — uniform error envelope |
| `exception/GlobalExceptionHandler.java` | **New** — `@RestControllerAdvice` mapping exceptions to clean HTTP responses |

The new `exception` package is intentionally separate from `controller` and `service` so future cross-cutting handlers (auth, validation) have a clear home.

---

## 4. Before / after, concrete

### Scenario A: DB write fails after a successful LLM call

| Before | After |
|---|---|
| LLM cost paid. Response returned to client with "Correct!" UI. `interaction_logs` row missing. Adaptive engine misses a real data point. Silent. | LLM cost paid. `@Transactional` rolls back the (empty) transaction. Exception propagates to global handler → 500 + `INTERNAL_ERROR`. Client sees an error, knows to retry. Operator sees full stack trace in logs. |

### Scenario B: Ollama hangs for 5 minutes

| Before | After |
|---|---|
| `restTemplate.postForObject` blocks for 5 minutes. Tomcat thread stuck. Repeat 200 times → server stops responding to any request. | Connection succeeds in <3s. `postForObject` reads from socket for 60s, then `ResourceAccessException` fires. Thread freed. Client receives 503 + `AI_SERVICE_UNAVAILABLE` and shows a retry button. |

### Scenario C: Student requests `/api/student/next-exercise?node_code=Pythagoras`

| Before | After |
|---|---|
| 500 with `IllegalArgumentException: Unknown node code or title: Pythagoras` + Java stack trace as JSON body. Flutter prints the whole string as the error message. | 400 with `{"code": "INVALID_INPUT", "message": "Unknown node code or title: Pythagoras", ...}`. Client can branch on `code` and show a topic-not-found UI. |

### Scenario D: A node really has no exercises (data drift)

| Before | After |
|---|---|
| 500 with `IllegalStateException: No exercises for node: ...` as a stack trace. | 404 with `{"code": "RESOURCE_MISSING", ...}`. Client can show "this topic isn't available yet." |

---

## 5. Known limitations and follow-ups

1. **`AuthController` is bypassed.** Login/register currently return plain `String` bodies with explicit `ResponseEntity.status(BAD_REQUEST).body("...")`. These don't go through the global handler, so the wire format is inconsistent (`ErrorResponseDto` elsewhere, raw string for auth). Phase 5 will fix this by having `AuthController` throw exceptions instead of returning explicit responses.

2. **No validation handler yet.** When Phase 5 adds `@Valid` to request DTOs, `MethodArgumentNotValidException` will start firing — the global handler will need a new method that turns the BindingResult into a structured field-level error response. Handler-method-additive change, no breaking changes to existing handlers.

3. **No correlation IDs.** When the client sees `INTERNAL_ERROR`, operators currently have to find the matching log line by timestamp. A per-request UUID injected into both the response body and the log MDC would make support trivial. Not done here to keep the phase tight.

4. **Timeouts are global.** Both the 50ms symbolic check and the 30s LLM call use the same 60s read timeout. Fine in practice (the short one finishes way before the limit), but a tighter per-endpoint timeout would catch FastAPI bugs faster. Worth revisiting if we add more endpoints.

---

## 6. How to verify locally

```bash
# 1. Restart the backend so the new RestTemplate config applies
cd backend-springboot/math-wise-backend
./mvnw spring-boot:run

# 2. Hit the existing 400-producing path with a junk node_code
curl -i -H "Authorization: Bearer <your_jwt>" \
  'http://localhost:9090/api/student/next-exercise?node_code=DoesNotExist'
# Expect: HTTP/1.1 400, body:
# {"status":400,"code":"INVALID_INPUT","message":"Unknown node code or title: DoesNotExist","timestamp":"...","path":"/api/student/next-exercise"}

# 3. Simulate AI service down by stopping it
docker compose stop ai-service
# Submit any answer through the Flutter app, or:
curl -i -X POST -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"node_code":"ARITH_ADDITION","equation":"What is 1+1?","correct_answer":"2","student_answer":"3"}' \
  'http://localhost:9090/api/exercises/evaluate'
# Expect: HTTP/1.1 503, body with code AI_SERVICE_UNAVAILABLE
# (After at most ~3s, not blocked forever.)

# 4. Restart and verify everything still works end-to-end
docker compose start ai-service
```

---

## 7. Why this was Phase 2

Phase 1 made the data trustworthy. Phase 2 makes the system trustworthy: errors are consistent, slow dependencies can't take the whole backend down, and the audit trail can't silently desynchronise from the client's view of what happened. Together these are the bare minimum the app needs before we can start adding more features (Phase 4) and validation (Phase 5) on top.
