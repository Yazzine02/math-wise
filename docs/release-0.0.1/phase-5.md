# Phase 5 — DTO consistency + Bean Validation

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The three problems

### 1.1 Ugly Java naming in `AiFeedbackDto`

The class used raw `snake_case` Java field names — `weakness_node`, `is_correct` — instead of using `@JsonProperty` to bridge between idiomatic camelCase Java fields and the snake_case JSON wire format. The getters that came out the other side were `getWeakness_node()` and `isIs_correct()`. Three problems with this:

- Non-idiomatic Java — every other DTO in the codebase used `@JsonProperty` correctly.
- IDE refactoring tools struggle with mixed-case identifiers. Renaming the field via a "Refactor" action produced inconsistent results.
- Every caller had to write the awkward getter names, spreading the ugliness through the service layer.

### 1.2 No validation on incoming requests

There were no Bean Validation annotations anywhere on the API surface. Concrete consequences:

- `POST /api/auth/register` with `{"email": "not-an-email", "password": "", "display_name": ""}` succeeded. A junk row was inserted into `students` — empty display name, an empty-string-BCrypt-hash password, and an unparseable email.
- `POST /api/exercises/evaluate` with missing `node_code` failed deep inside `AiEvaluationService` (NullPointerException → 500). The error told the user nothing useful.
- All input sanitisation was implicit, ad-hoc, scattered across services. Easy to forget; impossible to enumerate.

### 1.3 `AuthController` was the last "raw string" holdout (Phase 2 deferred work)

Every other endpoint in the app returned the structured `ErrorResponseDto` via the global handler set up in Phase 2. `AuthController` was different — its failures came back as plain `String` bodies:

```java
return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already exists");
return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email or password");
```

Two wire formats on the same API. The Flutter client had to special-case the auth path or risk displaying a JSON blob as the error message. Also: both errors used 400 (Bad Request) which is wrong — "email exists" is 409 (Conflict), "wrong credentials" is 401 (Unauthorized).

---

## 2. The fixes

### 2.1 Idiomatic Java fields + `@JsonProperty` for wire compat

`AiFeedbackDto` now has camelCase Java fields with explicit JSON property names:

```java
@JsonProperty("weakness_node")
private String weaknessNode;

private String explanation;

@JsonProperty("is_correct")
private boolean correct;       // bean convention: boolean prefix elided

public String getWeaknessNode() { ... }
public boolean isCorrect() { ... }
```

The wire format is **unchanged** — the Flutter client reads `weakness_node` and `is_correct` from the JSON as before. The only thing that moved is the Java surface, which is now consistent with every other DTO. `AiEvaluationService` was updated to call `getWeaknessNode()` and `setCorrect(...)` accordingly.

### 2.2 Bean Validation on every request DTO

Added `spring-boot-starter-validation` to `pom.xml`. Annotated the three request DTOs:

| DTO | Field | Constraint |
|---|---|---|
| `LoginRequestDto` | email | `@NotBlank`, `@Email` |
| | password | `@NotBlank` |
| `RegisterRequestDto` | email | `@NotBlank`, `@Email` |
| | password | `@NotBlank`, `@Size(min=8, max=128)` |
| | displayName | `@NotBlank`, `@Size(max=50)` |
| `EvaluateAnswerRequestDto` | nodeCode | `@NotBlank` |
| | equation | `@NotBlank`, `@Size(max=500)` |
| | correctAnswer | `@NotBlank` |
| | studentAnswer | `@NotBlank`, `@Size(max=500)` |

Each constraint has an explicit `message` so the client gets a precise per-field reason, not the framework default.

Controllers got `@Valid` added to their `@RequestBody` parameters. Without `@Valid` the constraints are silently ignored — easy to forget, easy to miss in review. We added it on all three: `AuthController.register`, `AuthController.login`, `AiEvaluationController.evaluateError`.

### 2.3 `MethodArgumentNotValidException` handler with field-level errors

When validation fails, Spring throws `MethodArgumentNotValidException`. The new handler in `GlobalExceptionHandler` turns the binding result into a `Map<String, String>` of field → first-violation-message and stuffs it into a new optional `fieldErrors` field on `ErrorResponseDto`.

Example wire response when a client sends invalid registration data:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-05-22T10:14:32Z",
  "path": "/api/auth/register",
  "fieldErrors": {
    "email": "Email must be a valid address",
    "password": "Password must be between 8 and 128 characters",
    "displayName": "Display name is required"
  }
}
```

The Flutter client can iterate over `fieldErrors` and highlight the offending inputs precisely instead of showing one concatenated message. `fieldErrors` is `@JsonInclude(NON_NULL)` so it doesn't appear on the wire for non-validation errors — the existing response shape is unchanged everywhere else.

### 2.4 Custom auth exceptions + status codes

Two new RuntimeExceptions:

- `EmailAlreadyExistsException` → mapped to **409 Conflict** + code `EMAIL_ALREADY_EXISTS`. The request was well-formed but conflicts with current server state.
- `InvalidCredentialsException` → mapped to **401 Unauthorized** + code `INVALID_CREDENTIALS`. Credentials supplied but didn't authenticate.

`AuthController.register` now throws `EmailAlreadyExistsException(email)` instead of building a `ResponseEntity` by hand. `AuthController.login` throws `InvalidCredentialsException()` for both "unknown email" and "wrong password" — deliberately the same exception, to prevent account-enumeration attacks. The exception message is the same in both cases; an attacker can't distinguish "this email isn't registered" from "this email is registered but the password is wrong."

### 2.5 Flutter `auth_service` parses the new envelope

`AuthService.login` and `AuthService.register` previously returned `response.body` as the error message — fine when the body was `"Email already exists"`, ugly when it became the full `ErrorResponseDto` JSON. Added a small `_extractErrorMessage` helper:

```dart
static String _extractErrorMessage(String body) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map && decoded['message'] is String) {
      return decoded['message'] as String;
    }
  } catch (_) { /* not JSON */ }
  return body;
}
```

JSON body → extract `message` field. Non-JSON body → fall back to raw string. The existing `Future<String?>` API stays unchanged — screens that show the error don't need to know anything moved. Proper typed exceptions across all services come in Phase 6.

---

## 3. Files changed

| File | Change |
|---|---|
| `pom.xml` | Added `spring-boot-starter-validation` |
| `dto/AiFeedbackDto.java` | Renamed fields to camelCase, added `@JsonProperty` annotations |
| `dto/LoginRequestDto.java` | Added `@NotBlank`, `@Email` |
| `dto/RegisterRequestDto.java` | Added `@NotBlank`, `@Email`, `@Size` |
| `dto/EvaluateAnswerRequestDto.java` | Added `@NotBlank`, `@Size` |
| `dto/ErrorResponseDto.java` | Added optional `fieldErrors` map + second constructor |
| `controller/AuthController.java` | Removed raw-string error returns. Throws custom exceptions. Added `@Valid` |
| `controller/AiEvaluationController.java` | Added `@Valid` on the request body parameter |
| `service/AiEvaluationService.java` | Updated to use the renamed getters/setters |
| `exception/EmailAlreadyExistsException.java` | **New** — 409 Conflict mapping |
| `exception/InvalidCredentialsException.java` | **New** — 401 Unauthorized mapping (single type for both unknown-email and wrong-password to avoid enumeration) |
| `exception/GlobalExceptionHandler.java` | Three new handlers: `MethodArgumentNotValidException`, `EmailAlreadyExistsException`, `InvalidCredentialsException` |
| `frontend-flutter/lib/services/auth_service.dart` | New `_extractErrorMessage` helper to parse the JSON error envelope |

---

## 4. Behaviour comparison

### Scenario A: register with invalid email and short password

| Before | After |
|---|---|
| 200 OK, user created with malformed email and empty/short password hash. Garbage in `students` table. | 400 Bad Request + `VALIDATION_FAILED` with `fieldErrors: {email: "Email must be a valid address", password: "Password must be between 8 and 128 characters"}`. No DB write. |

### Scenario B: register with existing email

| Before | After |
|---|---|
| 400 Bad Request, body `"Email already exists"` (raw string). Wrong status code (should be 409). | 409 Conflict + `EMAIL_ALREADY_EXISTS` with structured envelope including the email in the message. |

### Scenario C: login with wrong password

| Before | After |
|---|---|
| 400 Bad Request, body `"Invalid email or password"` (raw string). Wrong status code (should be 401). | 401 Unauthorized + `INVALID_CREDENTIALS` with structured envelope. Same response as "unknown email" — no account enumeration. |

### Scenario D: evaluate with empty `student_answer`

| Before | After |
|---|---|
| Reached the SymPy endpoint, which returned `is_correct: false` (empty string doesn't match anything). Falsely logged as a wrong-answer attempt in `interaction_logs`, polluting adaptive signal. | 400 Bad Request + `VALIDATION_FAILED` with `fieldErrors: {studentAnswer: "student_answer is required"}` before any backend work happens. No DB write. |

### Scenario E: existing valid request

| Before | After |
|---|---|
| 200 OK with `AiFeedbackDto` | 200 OK with `AiFeedbackDto`. **Wire format identical** — Java fields renamed but `@JsonProperty` preserves snake_case keys. No client change needed. |

---

## 5. Known limitations and follow-ups

1. **Existing junk `students` rows are not cleaned up.** If you registered with malformed input before this phase, those rows still exist. The validation only blocks future bad input. A one-off SQL cleanup would handle existing data; not done here to keep the phase tight.

2. **Flutter screens still display the unwrapped string.** `auth_service.dart` now parses out the `message` field, but the rest of the Flutter codebase (exercise/progress/course services) still throws `Exception(response.body)` directly. Users will see the full JSON envelope as an error message on those flows. Cleanup is Phase 6 (typed exception hierarchy + consistent error rendering widget).

3. **No `fieldErrors` consumption on the Flutter side yet.** The backend now returns precise field-level errors, but the Flutter UI displays only `message` ("Request validation failed"). Per-field highlighting is a Phase 6 frontend job.

4. **Password complexity is just length.** `@Size(min=8)` is the floor — no required digit / symbol / mixed case enforcement. Reasonable for a POC; can layer a `@Pattern` constraint when real users arrive.

5. **No CSRF protection on the auth endpoints.** Mobile-only API + JWT in the `Authorization` header means CSRF risk is low, but adding `@PostMapping` + bearer-token validation everywhere is correct hygiene. Auth v2 work.

---

## 6. How to verify locally

```bash
# Restart Spring Boot to pick up validation + new handlers
./mvnw spring-boot:run -pl backend-springboot/math-wise-backend

# A) Validation: invalid registration body
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"email":"nope","password":"hi","display_name":""}' \
  http://localhost:9090/api/auth/register

# Expect: 400, body contains
#   "code":"VALIDATION_FAILED"
#   "fieldErrors": { "email":"...", "password":"...", "displayName":"..." }

# B) Email collision
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"email":"existing@user.com","password":"hunter22","display_name":"X"}' \
  http://localhost:9090/api/auth/register
# (Run twice; first succeeds 200, second:)
# Expect: 409, "code":"EMAIL_ALREADY_EXISTS"

# C) Wrong password
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"email":"existing@user.com","password":"wrong-password"}' \
  http://localhost:9090/api/auth/login
# Expect: 401, "code":"INVALID_CREDENTIALS"

# D) Wire format unchanged for happy path — evaluate endpoint
curl -X POST -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"node_code":"ARITH_ADDITION","equation":"1+1?","correct_answer":"2","student_answer":"2"}' \
  http://localhost:9090/api/exercises/evaluate
# Expect: {"weakness_node":null,"explanation":null,"is_correct":true}
# Note: keys are still snake_case despite the Java refactor.
```

---

## 7. Why this was Phase 5

Phases 1-4 fixed the deep architectural issues: data trustworthiness, error consistency, deployment configurability, the adaptive engine. Phase 5 is the smaller polish layer that closes the API contract: every endpoint validates its inputs, every error comes back in the same envelope, and the Java code follows its own conventions. The remaining frontend work to fully consume these improvements is Phase 6.
