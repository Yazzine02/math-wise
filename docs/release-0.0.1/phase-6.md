# Phase 6 — Frontend hardening

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The four problems

### 1.1 Errors rendered as raw JSON

After Phases 2 and 5 the backend returns a structured `ErrorResponseDto` envelope:

```json
{"status":503,"code":"AI_SERVICE_UNAVAILABLE","message":"AI service is temporarily unavailable. Please try again.","timestamp":"...","path":"/api/exercises/evaluate"}
```

The Flutter side hadn't caught up. Every domain service threw `Exception('Failed to fetch ...: ${response.body}')`. The screens then displayed `e.toString()`, which is literally that JSON blob, in red text, to the user. Users saw `Exception: Failed to fetch next exercise: {"status":503,...}` as the error message.

### 1.2 No distinction between error types

A `SocketException` (offline), an HTTP 401 (session ended), an HTTP 503 (AI service down), and an HTTP 500 (server bug) all flowed through the same generic `catch (e)` block and produced indistinguishable red error text. Each deserves different UI treatment and possibly different recovery action. The screens had no way to tell them apart.

### 1.3 `fieldErrors` from Phase 5 was unconsumed

The backend now returns per-field validation messages:

```json
{
  "code": "VALIDATION_FAILED",
  "fieldErrors": {"email":"Email must be a valid address","password":"..."}
}
```

The login and register screens still showed a single generic SnackBar with the message text. Users couldn't tell *which* field was the problem; they just saw "Request validation failed."

### 1.4 No auto-logout on session expiry

When the JWT expires, every authenticated request 401s. Nothing in the Flutter code reacted to that — the user kept seeing error banners forever and had to manually find the logout button.

---

## 2. The fixes

### 2.1 A typed exception hierarchy

New file `lib/errors/api_exception.dart`. One base class plus eight subclasses, each carrying enough context that screens can switch on type without parsing strings:

```dart
ApiException                       // base, also used for misc 4xx
├── NetworkException               // offline / DNS / socket / timeout
├── UnauthorizedException          // 401
├── ForbiddenException             // 403
├── NotFoundException              // 404
├── ConflictException              // 409 — EMAIL_ALREADY_EXISTS
├── ValidationException            // 400 + VALIDATION_FAILED + fieldErrors
├── ServerException                // 5xx generic
└── ServiceUnavailableException    // 502/503 (extends ServerException)
```

The `ApiException.fromResponse(http.Response)` factory parses the backend envelope and returns the right subclass. If the body isn't JSON (e.g., a misconfigured endpoint, an unknown intermediary), we fall back to a generic `ApiException` with the raw status code — degraded gracefully, never crashes.

### 2.2 ApiClient does the work once

`ApiClient` got two new helpers — `getJson(path)` and `postJson(path, body)` — that:

1. Inject the JWT (existing behavior).
2. Apply a 75-second client-side timeout (matches the backend's 60s read timeout to FastAPI + grace).
3. Catch `SocketException` / `TimeoutException` / `http.ClientException` and rethrow as `NetworkException`.
4. Decode the JSON body on 2xx success.
5. Throw `ApiException.fromResponse(response)` on any non-2xx.

The domain services (`ExerciseService`, `ProgressService`, `CourseService`) collapsed from:

```dart
final response = await ApiClient.get(path);
if (response.statusCode == 200) {
  return Exercise.fromJson(jsonDecode(response.body));
}
throw Exception('Failed to fetch next exercise: ${response.body}');
```

to:

```dart
final json = await ApiClient.getJson(path);
return Exercise.fromJson(json as Map<String, dynamic>);
```

No manual status-code check, no string-concat error, no missed branch. Each service shrank to a single readable file.

### 2.3 `AuthService` and `AuthProvider` now return `ApiException`

`AuthProvider.login` and `register` used to return `Future<String?>` — null on success, an opaque error string on failure. Now they return `Future<ApiException?>`:

```dart
final error = await authProvider.login(email, password);
// error is null  → success
// error is ValidationException → highlight specific inputs
// error is UnauthorizedException → wrong password message
// error is NetworkException → offline UI
```

`AuthService.login/register` themselves now throw on failure (previously returned the error string). They share the same network-failure-translation block as `ApiClient`, so a `SocketException` mid-login becomes a `NetworkException` like everywhere else.

### 2.4 `ErrorView` widget

`lib/widgets/error_view.dart`. Takes any `ApiException` and renders an appropriate banner:

| Exception | Flavour | Color | Icon |
|---|---|---|---|
| `NetworkException` | "You're offline" | cyan | wifi_off |
| `UnauthorizedException` / `ForbiddenException` | "Session ended" | violet | lock_outline |
| `ServerException` / `ServiceUnavailableException` | "Server problem" | pink | cloud_off |
| anything else | "Something went wrong" | pink | error_outline |

Optional `onRetry` callback wires up a retry button. `ValidationException` is deliberately *not* given its own flavour — those errors belong on the input fields, not in a banner. If a caller still hands a `ValidationException` to `ErrorView`, it gets the generic treatment.

### 2.5 `MwField.errorText`

Added an optional `errorText` parameter to the shared field widget. When set:

- Border switches to pink (overriding even the lime focus glow — you should still see something's wrong while typing).
- An inline error row appears beneath the field: small pink icon + pink message.
- `@JsonInclude(NON_NULL)` semantics: when `errorText` is null, the field renders exactly as before. No regression for non-validation use.

### 2.6 Domain screens consume the typed errors

`home_screen`, `exercise_screen`, `courses_screen`, `lesson_screen` were all updated:

```dart
try {
  ...
} on UnauthorizedException catch (_) {
  if (mounted) await context.read<AuthProvider>().logout();
} on ApiException catch (e) {
  if (mounted) setState(() { _error = e; ... });
}
```

- 401 from any authenticated endpoint triggers `AuthProvider.logout()`, which clears SharedPreferences and notifies the router — the user lands on `/login` automatically.
- Every other error becomes an `ErrorView` rendered inline on the screen, with a Retry button bound to whatever method was just attempted.

The old `_ErrorBlock` inner classes (three of them, all subtly different) were removed.

### 2.7 Auth screens consume `fieldErrors`

`login_screen` and `register_screen` now:

- Destructure the `fieldErrors` map from a `ValidationException` and pass each value into the corresponding `MwField.errorText`.
- Render an `ErrorView` banner *only* for non-validation errors (network / wrong-credentials / email-collision / server). Validation is already communicated per-field; doubling it up as a banner would be noisy.
- The old `SnackBar` pattern (which disappeared after a few seconds and gave users no chance to read the message) is gone.

---

## 3. Files changed

### New files

| File | Purpose |
|---|---|
| `lib/errors/api_exception.dart` | Typed exception hierarchy + JSON envelope parser |
| `lib/widgets/error_view.dart` | Shared error-state widget with type-aware flavours |

### Modified — services

| File | Change |
|---|---|
| `lib/services/api_client.dart` | Added `getJson`/`postJson` helpers, 75s timeout, network-failure → `NetworkException` translation |
| `lib/services/auth_service.dart` | Throws typed `ApiException` instead of returning nullable string |
| `lib/services/exercise_service.dart` | Uses `getJson`/`postJson`. Lost ~10 lines of boilerplate. |
| `lib/services/progress_service.dart` | Same |
| `lib/services/course_service.dart` | Same |

### Modified — state

| File | Change |
|---|---|
| `lib/providers/auth_provider.dart` | `login`/`register` return `Future<ApiException?>` instead of `Future<String?>` |

### Modified — widgets

| File | Change |
|---|---|
| `lib/widgets/app_widgets.dart` | `MwField.errorText` parameter + pink-border-with-inline-message rendering |

### Modified — screens

| File | Change |
|---|---|
| `lib/screens/home_screen.dart` | Uses `ErrorView` + auto-logout on 401. Dropped `_ErrorBlock` |
| `lib/screens/exercise_screen.dart` | Same |
| `lib/screens/courses_screen.dart` | Same, plus `FutureBuilder` snap error handling routed through the new flow |
| `lib/screens/lesson_screen.dart` | Same |
| `lib/screens/login_screen.dart` | Consumes `fieldErrors` from `ValidationException`. Renders `ErrorView` for non-validation. SnackBar removed |
| `lib/screens/register_screen.dart` | Same |

---

## 4. Before / after behaviour

| Scenario | Before | After |
|---|---|---|
| Backend returns 503 AI_SERVICE_UNAVAILABLE on submit | Red text: `Exception: Failed to submit answer: {"status":503,"code":"AI_SERVICE_UNAVAILABLE",...}` | Pink card with `cloud_off` icon, title "Server problem", message text from the envelope, "Try again" button |
| Device goes offline mid-request | Same red JSON blob | Cyan card with `wifi_off`, "You're offline" |
| JWT expires while on Home screen | Red error text, user trapped | Auto-logout, redirect to `/login` |
| Register with bad email | SnackBar saying "Email must be a valid address" (vanishes after 4s) | Email field gets pink border + "Email must be a valid address" inline. Stays visible until corrected. |
| Register with already-used email | SnackBar saying "An account with email ... already exists" | Pink ErrorView banner saying same — but inline, persistent, with Retry button |
| Login with wrong password | SnackBar with "Invalid email or password" | Pink ErrorView banner with same — and stays put until the user changes inputs |

---

## 5. Known limitations and follow-ups

1. **`FutureBuilder` error path is awkward.** `courses_screen` and `lesson_screen` use `FutureBuilder<T>` and have to check `snap.error` types — that wraps the `try/on` pattern in a `Builder` so we can `addPostFrameCallback` for the auto-logout case. Migrating these to plain `StatefulWidget._load()` patterns (like `home_screen`) would be more consistent. Not done in this phase because the `FutureBuilder` works fine; just a style nit.

2. **No global error interceptor.** The auto-logout-on-401 logic is duplicated across four screens. A single `RouteObserver` or a wrapper around `ApiClient` that listens for `UnauthorizedException` and calls `AuthProvider.logout()` from one place would be cleaner. Not done because we don't have DI for `AuthProvider` accessible from `ApiClient` (which is all static) — refactoring that is its own effort.

3. **The `code` field is unused on the client side.** Backend returns stable codes like `EMAIL_ALREADY_EXISTS`, but the Flutter side only uses status + type. A `code`-aware UX (e.g., a "Sign in instead?" link on the register screen when code is `EMAIL_ALREADY_EXISTS`) is a sensible Phase-7 polish.

4. **No retry/backoff on transient failures.** A flaky network momentarily fails → user has to tap Retry. Built-in retry-with-exponential-backoff on `NetworkException` and 5xx would be a nice ergonomic improvement. Not done — premature.

5. **`StudentProvider` for cached in-app state was not implemented.** Mentioned in the original Phase 6 plan but skipped: the existing fetch-on-init pattern is cheap and the data is small enough that re-fetching on navigation isn't a real cost. If we ever add lots of derived/cached state (XP, streaks, recent feedback history), this becomes worth doing.

---

## 6. How to verify locally

```bash
# 1. Restart everything
docker compose up -d
./mvnw spring-boot:run -pl backend-springboot/math-wise-backend
cd frontend-flutter && flutter pub get && flutter run

# 2. Network failure — kill the backend, watch the Flutter screens
docker compose stop ai-service
./mvnw   # kill the local Spring Boot
# Use the app: every fetch should now show the cyan "You're offline" card
# with the message and a Retry button. No raw JSON anywhere.

# 3. Validation — try to register with bad inputs
# Email field: "notanemail"  → email field goes pink with inline message
# Password field: "abc"      → password field goes pink with size message
# Display name: ""           → display name field goes pink
# Submit: all three field errors appear simultaneously, no banner.

# 4. Conflict — register the same email twice
# First registration succeeds.
# Second one: pink ErrorView banner says "An account with email ... already exists".
# Not a SnackBar — banner stays put.

# 5. Auto-logout — manually invalidate the token
# In Postgres, delete the student row used to log in OR rotate JWT_SECRET in .env
# and restart Spring Boot. Reopen the app: any authenticated screen should
# immediately bounce to /login.
```

---

## 7. Why this was Phase 6

Phase 1 made the data trustworthy. Phase 2 made errors trustworthy. Phase 3 made deployment trustworthy. Phase 4 made the adaptive engine real. Phase 5 made the API contract internally consistent. Phase 6 finally lets the *user* see all of that. Every backend improvement we shipped earlier was being undone by the Flutter side serving JSON blobs and generic SnackBars; now the typed exception hierarchy, the per-field validation rendering, the session-expiry auto-logout, and the consistent error UI together close that loop.

`release/0.0.1` is now what it claimed to be: a Proof of Concept that's honest end-to-end.
