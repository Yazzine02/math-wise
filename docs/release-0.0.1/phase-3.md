# Phase 3 — Secrets to env vars + local backup

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The problems

### 1.1 JWT signing secret in source code

`JwtUtil.java` had:

```java
private static final String SECRET_STRING = "KifmaGaloSyadna,DrebLkhobzMahdoSkhonOZebdaMahdhaSkhona!";
```

Anyone with read access to the git repo (collaborators, future hires, anyone with a clone) could read the signing key and forge tokens for any user. Worse, the secret is in git history — even rotating it now and pushing a "fix" wouldn't be enough; the old value would remain accessible via `git log -p`.

### 1.2 DB credentials in two places, both checked in

- `application.properties`: `spring.datasource.password=0`
- `docker-compose.yml`: `POSTGRES_PASSWORD: 0`

Same value duplicated in two committed files. If you ever rotated one without the other, the app would silently stop being able to connect to its database.

### 1.3 Service URLs hardcoded

- Spring Boot's `ai-service.url=http://localhost:8000` baked into the JAR — to point at a remote AI service you'd have to rebuild.
- Flutter's API base URL `http://10.0.2.2:9090` hardcoded in two separate Dart files (`api_client.dart` and `auth_service.dart`). The `10.0.2.2` alias only resolves on the Android emulator — the app simply doesn't work on iOS simulator or physical devices.

### 1.4 No authoritative record of current values

If you ever lost track of the DB password or the JWT secret, you had no single place to look. The values were scattered across source files.

---

## 2. The fixes

The strategy is the standard 12-factor pattern, with one extra twist: a `.env` file at the repo root acts simultaneously as the runtime config source for Docker Compose **and** as your local backup of "what are the current values."

### 2.1 Single source of truth: root `.env`

A new file at the repo root contains every secret and URL the project uses. Git-ignored (the `.gitignore` already has `.env`), never pushed, never shown in chat. It's documented in `.env.example` (committed, with placeholder values).

```
/.env             ← gitignored, real values, your backup
/.env.example     ← committed, template
```

Docker Compose auto-loads `.env` from the directory containing `docker-compose.yml`, so `${VAR}` placeholders inside Compose get substituted with no extra setup.

### 2.2 Spring Boot reads everything from env vars, with defaults

`application.properties` no longer contains hardcoded values — it contains `${VAR:default}` references. The defaults match the local Docker setup so a fresh clone still runs out of the box, but any value can be overridden by exporting the corresponding env var:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mathwise_db}
spring.datasource.username=${DB_USER:db_user}
spring.datasource.password=${DB_PASSWORD:0}
jwt.secret=${JWT_SECRET:DEV_ONLY_PLACEHOLDER_REPLACE_VIA_JWT_SECRET_ENV_VAR}
ai-service.url=${AI_SERVICE_URL:http://localhost:8000}
```

The JWT default is deliberately *not* the previous real secret — it's a clearly marked placeholder. Anyone who runs the app without setting `JWT_SECRET` immediately knows they're in dev mode with an insecure key. The real secret lives only in your `.env`.

`JwtUtil` now takes the secret via constructor injection:

```java
public JwtUtil(@Value("${jwt.secret}") String secret) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
}
```

### 2.3 Docker Compose uses `${VAR:-default}` interpolation

Every hardcoded value in `docker-compose.yml` is now a placeholder. Compose's `${VAR:-default}` syntax falls back to a safe value when the env var isn't set, so `docker compose up` works without any setup.

```yaml
environment:
  POSTGRES_USER: ${DB_USER:-db_user}
  POSTGRES_PASSWORD: ${DB_PASSWORD:-0}
  POSTGRES_DB:   ${DB_NAME:-mathwise_db}
```

When `.env` is present, Compose substitutes the real values. When it's not (fresh clone), the defaults kick in.

### 2.4 Flutter reads base URL from `--dart-define`

`api_client.dart`:

```dart
static const String baseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:9090',
);
```

`String.fromEnvironment` is a *compile-time* substitution — the value is baked into the binary at build time. Pass it via `flutter run`:

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.42:9090    # physical device
flutter build apk --dart-define=API_BASE_URL=https://api.mathwise.app
```

The default keeps Android emulator development frictionless.

`auth_service.dart` now references `ApiClient.baseUrl` instead of duplicating the URL, eliminating the previous two-places-to-update bug.

---

## 3. Files changed

| File | Change |
|---|---|
| `.env` (new, **gitignored**) | All current local values — your backup |
| `.env.example` (new, committed) | Template with placeholders and inline documentation |
| `application.properties` | All values now `${VAR:default}` references |
| `JwtUtil.java` | Reads `jwt.secret` via `@Value`, no hardcoded string |
| `docker-compose.yml` | All envs use `${VAR:-default}` interpolation |
| `api_client.dart` | `baseUrl` now `String.fromEnvironment('API_BASE_URL', ...)` |
| `auth_service.dart` | Imports `ApiClient.baseUrl` instead of duplicating the URL |

---

## 4. How to use `.env`

### As a backup / reference
The file is yours. Any time you forget what the DB password or JWT secret is, open `.env` at the repo root. It's git-ignored so it won't follow you across branches or repos by accident, but it's also not in `.git`'s index — it lives on your filesystem only.

### When running `docker compose up`
Nothing extra needed. Compose auto-loads `.env` from the same directory as `docker-compose.yml`. The Postgres container, PGAdmin, and ai-service will all receive the values defined there.

### When running Spring Boot locally (`./mvnw spring-boot:run`)
Spring Boot does **not** auto-load `.env`. Three options:

1. **Rely on defaults** (recommended for local work). `application.properties` falls back to values that match the local Docker setup, so the app boots without exported env vars. The JWT signing key will be the placeholder — fine for development, blocking your-real-secret token forgery is a non-goal locally.

2. **Export from `.env` once per shell**:
   ```bash
   export $(grep -v '^#' .env | xargs)
   ./mvnw spring-boot:run
   ```

3. **Use `direnv`** or your editor's run-config to load `.env` automatically.

### When running Flutter
Pass `--dart-define=API_BASE_URL=...` to `flutter run` (or set it in your IDE's run config). The default works for Android emulator.

### When running `ai-python` directly (without Docker)
The existing `ai-python/.env.example` already documents the FastAPI-specific vars. Either copy values to a local `ai-python/.env` or export them in your shell. Running inside Docker uses the values from `docker-compose.yml`'s `environment:` block (which now pulls from the root `.env`).

---

## 5. Before / after, concrete

### Scenario A: Need to rotate the JWT secret

| Before | After |
|---|---|
| Edit `JwtUtil.java`. Commit. The new value is now also in git history. The old value also remains in git history forever — both compromised. Rebuild and redeploy. | Edit `.env`. Restart. The old value never enters git. New value never enters git either. |

### Scenario B: Run on iPhone simulator

| Before | After |
|---|---|
| Edit `api_client.dart`, change URL to `http://localhost:9090`. Edit `auth_service.dart`, same change. Remember to revert before running on emulator again. | `flutter run --dart-define=API_BASE_URL=http://localhost:9090`. Code unchanged. |

### Scenario C: Onboard a collaborator

| Before | After |
|---|---|
| They clone the repo. They have your DB password, JWT secret, etc. baked into their checkout. | They clone the repo. They `cp .env.example .env` and fill in values. Or they just run with defaults — works for local dev. Your real secrets stay yours. |

---

## 6. Known limitations and follow-ups

1. **`ai-python` still uses its own `.env`.** `python-dotenv` looks for `.env` next to the running script (`ai-python/.env`), not the repo root. Two options for unification: (a) add `python-dotenv`'s `load_dotenv(Path(__file__).parent.parent / ".env", override=False)` to also try the root file, or (b) symlink `ai-python/.env` → `../.env`. Not done in this phase to keep scope tight; `docker compose` is the primary execution path and that already works correctly. The existing `ai-python/.env.example` documents the standalone-run pattern.

2. **No env-var validation at boot.** If `JWT_SECRET` is set to a 5-char string, the app boots fine and crashes at the first `Keys.hmacShaKeyFor` call (insufficient key material for HMAC-SHA256). A `@PostConstruct` validator that asserts minimum lengths would catch this at startup. Reasonable Phase-5 candidate alongside the validation work.

3. **Existing JWT tokens become invalid the first time you change `JWT_SECRET`** (whether through `.env` or otherwise). This is intentional — that's how rotating a signing key works — but users will need to log in again. Token refresh / re-issue is an Auth v2 concern.

4. **The default JWT secret is checked in.** This is fine: it's clearly marked `DEV_ONLY_PLACEHOLDER_REPLACE_VIA_JWT_SECRET_ENV_VAR` and is intended only to let the app boot in fresh-clone development. In any non-throwaway environment, the real value comes from `.env`.

---

## 7. How to verify locally

```bash
# 1. Check that .env is git-ignored
git check-ignore -v .env
# Expect: .gitignore:38:.env  .env

# 2. Check that .env.example is committed but .env is not
git status
# .env should NOT appear. .env.example should be tracked.

# 3. Spring Boot picks up the env var
JWT_SECRET="totally_different_secret_at_least_32_chars_long" \
  ./mvnw spring-boot:run -pl backend-springboot/math-wise-backend
# (Existing tokens will fail to verify — that's the secret rotation working.)

# 4. Spring Boot also boots with defaults (no env vars set)
./mvnw spring-boot:run -pl backend-springboot/math-wise-backend
# (Logs should show "DEV_ONLY_PLACEHOLDER..." being used — expected.)

# 5. Docker compose substitutes from .env
docker compose config | grep -E "(POSTGRES|MODEL_NAME)"
# Expect: values from .env, not the literals "db_user"/"llama3.2:3b".

# 6. Flutter base URL override
flutter run --dart-define=API_BASE_URL=http://localhost:9090
```

---

## 8. Why this was Phase 3

Phase 1 made data trustworthy. Phase 2 made errors trustworthy. Phase 3 makes the *deployment shape* trustworthy: secrets aren't being read out of git, the app is configurable per environment, and there's a single place (`.env`) that records the current state. Together these three phases form the minimum viable foundation any later work (validation, real adaptive engine, frontend hardening) can build on.
