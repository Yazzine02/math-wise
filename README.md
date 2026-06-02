# Math-Wise

[![Backend CI](https://github.com/Yazzine02/math-wise/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Yazzine02/math-wise/actions/workflows/backend-ci.yml)
[![AI service CI](https://github.com/Yazzine02/math-wise/actions/workflows/ai-service-ci.yml/badge.svg)](https://github.com/Yazzine02/math-wise/actions/workflows/ai-service-ci.yml)
[![Frontend CI](https://github.com/Yazzine02/math-wise/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/Yazzine02/math-wise/actions/workflows/frontend-ci.yml)

An AI-powered adaptive math tutor. Students practise arithmetic, fractions, and algebra; every answer is **verified symbolically** (SymPy), and every *wrong* answer is **diagnosed by a local LLM grounded in a curated course corpus** (RAG). The app then **adapts which concept to drill next** based on each student's evolving weaknesses — steering them toward what they struggle with, and easing off once they've shown mastery.

---

## Highlights

- **Deterministic correctness** — answers are checked with SymPy, so mathematically-equivalent forms all count (`1/2 ⇔ 0.5`, `(x+2)(x+3) ⇔ x²+5x+6`). The LLM never decides *whether* you're right.
- **RAG-grounded diagnosis** — wrong answers are explained by `llama3.2:3b`, but the prompt is grounded in an 8-topic course corpus retrieved via ChromaDB + sentence-transformers, so explanations paraphrase real pedagogy instead of hallucinating.
- **Adaptive engine** — mastery dissolution (3 consecutive correct retires a weakness), curriculum-aware cold-start, prerequisite-chain descent (a division slip inside an equation is logged as *division*), and a 30% exploration mix for spaced review.
- **Self-replenishing exercise pool** — when a topic runs low, the backend asynchronously asks the LLM for new problems, each SymPy-verified, with deterministic templates as a guaranteed fallback.
- **Production hygiene** — JWT auth, a structured error envelope, an append-only audit trail, Testcontainers integration tests, and CI across all three layers.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│                       Flutter App (mobile)                       │
│      Login · Dashboard · Courses · Exercise · AI Feedback        │
└────────────────────────────┬───────────────────────────────────┘
                             │  HTTP + JWT Bearer token
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                   Spring Boot API   (:9090)                      │
│   Auth · Adaptive engine · Progress · Courses · Audit log        │
└──────┬──────────────────────────────────────────┬──────────────┘
       │ JPA                                       │ HTTP (internal)
       ▼                                           ▼
┌────────────┐                  ┌────────────────────────────────────────────┐
│ PostgreSQL │ (:5432)          │            FastAPI AI service  (:8000)       │
└────────────┘                  │  /check-answer    → SymPy equivalence        │
                                │  /evaluate-error  → RAG + structured LLM     │
                                │  /generate-exercises → LLM + verify + fallback│
                                │  /health          → liveness probe           │
                                │       │                    ▲                 │
                                │       │ Ollama API         │ ChromaDB        │
                                └───────┼────────────────────┼─────────────────┘
                                        ▼                    ▼
                              ┌──────────────────┐  ┌──────────────────┐
                              │ Ollama (:11434)  │  │  Vector store     │
                              │   llama3.2:3b    │  │  (course corpus)  │
                              └──────────────────┘  └──────────────────┘
```

The layers are decoupled: the Flutter app only talks to Spring Boot, Spring Boot is the only caller of FastAPI, and FastAPI is the only caller of Ollama.

---

## Tech Stack

| Layer | Stack |
|-------|-------|
| **Frontend** | Flutter / Dart 3 (SDK ^3.11.1) · Provider · go_router · http · shared_preferences · google_fonts |
| **Backend** | Java 21 · Spring Boot 4.0.3 · Spring Data JPA · Spring Security + JWT (`jjwt`) · PostgreSQL · Testcontainers |
| **AI service** | Python 3.11 · FastAPI 0.135.1 · SymPy · ChromaDB · sentence-transformers · Ollama (`llama3.2:3b`) |
| **Infra / CI** | Docker Compose · GitHub Actions (3 pipelines) · Dependabot |

---

## Repository Structure

```
math-wise/
├── docker-compose.yml          # PostgreSQL, PGAdmin, Ollama, FastAPI
├── frontend-flutter/           # Flutter mobile app
├── backend-springboot/         # Spring Boot REST API + business logic
└── ai-python/                  # FastAPI AI service
    ├── main.py                 # endpoints + SymPy checks + generation
    ├── exercise_templates.py   # deterministic exercise generators (fallback)
    ├── adapters/               # AI engine adapters (local Ollama / cloud)
    ├── evaluation/             # structured diagnostician (RAG-aware prompt)
    ├── models/                 # internal Pydantic schemas + node taxonomy
    ├── rag/                    # ChromaDB retriever, ingest script, config
    ├── corpus/                 # 8 markdown course files (one per concept)
    ├── docs/                   # ChangeLog + rollback procedure
    └── tests/                  # pytest suites
```

---

## Layer 1 — Flutter Frontend

**Directory:** `frontend-flutter/` · **State:** Provider · **Routing:** go_router · **HTTP:** `http` + JWT in SharedPreferences

### Screen flow

```
Login / Register
      │  (JWT stored in SharedPreferences)
      ▼
  HomeScreen (dashboard)              CoursesScreen ──► LessonScreen
  ├── student name                    (theory + worked examples per topic)
  ├── top-3 live weaknesses
  └── Start Practice
        │
        ▼
  ExerciseScreen ──► (submit) ──► FeedbackScreen
  fetch adaptive next                correct / incorrect banner
  exercise, type answer              AI explanation + weakness chip
                                     Next Exercise / Back to Dashboard
```

### Key files

| File | Purpose |
|------|---------|
| `lib/main.dart` | App entry, MultiProvider setup, theme |
| `lib/routing/app_router.dart` | GoRouter config + auth-guard redirect |
| `lib/providers/auth_provider.dart` | Auth state (`isAuthenticated`, `displayName`) |
| `lib/providers/dashboard_signal.dart` | Lightweight signal to refresh the dashboard after a submission |
| `lib/services/api_client.dart` | Shared HTTP helper — injects JWT, maps errors to typed exceptions |
| `lib/services/auth_service.dart` · `exercise_service.dart` · `progress_service.dart` · `course_service.dart` | API calls per domain |
| `lib/errors/api_exception.dart` | Typed exception hierarchy parsed from the backend's error envelope |
| `lib/screens/` | `home` · `exercise` · `feedback` · `login` · `register` · `courses` · `lesson` |
| `lib/widgets/` · `lib/theme/` | Reusable widgets, error view, wordmark, dark "bold playful" theme |

### Auth guard
The router's `redirect` watches `AuthProvider`. Navigation is blocked until SharedPreferences has been checked; unauthenticated users are sent to `/login`, and authenticated users can't reach `/login`.

### API base URL
The app targets `http://10.0.2.2:9090` (the Android emulator's alias for `localhost`). Change `baseUrl` in `lib/services/api_client.dart` for a physical device.

---

## Layer 2 — Spring Boot Backend

**Directory:** `backend-springboot/math-wise-backend/` · **Java 21** · **Spring Boot 4.0.3** · **Port 9090** · **PostgreSQL** · **JWT (HMAC-SHA256)**

### API endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | — | Register a new student, returns a JWT |
| POST | `/api/auth/login` | — | Authenticate, returns a JWT |
| GET | `/api/student/next-exercise?node_code=` | ✓ | Adaptive next exercise (or topic-locked when `node_code` given) |
| GET | `/api/student/progress` | ✓ | Top-3 *live* (un-mastered) weakness areas |
| POST | `/api/exercises/evaluate` | ✓ | Submit an answer → correctness + AI feedback |
| GET | `/api/courses` | ✓ | List lessons (one per concept, ordered by difficulty) |
| GET | `/api/courses/{nodeCode}` | ✓ | Lesson detail: intro, theory, examples, tip |

### Two-stage answer evaluation

`POST /api/exercises/evaluate` runs `AiEvaluationService.evaluateStudentAnswer`:

1. **Symbolic check (always)** — calls FastAPI `/check-answer`. SymPy decides correctness and accepts equivalent forms. Authoritative.
2. **LLM diagnosis (only when wrong)** — calls FastAPI `/evaluate-error`, passing the tested node *and its prerequisite chain* so the model can attribute the slip to the right underlying skill. Correct answers skip the LLM entirely (saving 5–30s).
3. **Normalise** — the AI-returned weakness is resolved to one of the 8 canonical node codes (`KnowledgeNodeResolver`), falling back to the tested node if the model returns something unknown.
4. **Persist** — an `InteractionLog` row is written inside a transaction, keeping the audit trail and the client response consistent.

### Adaptive engine

`StudentProgressService` selects the next concept to practise:

- **Weakness window** — only failures from the last 30 days count.
- **Mastery dissolution** — once a student's last **3** attempts on a node are all correct, it's considered mastered and drops off both the dashboard and the weakness pool.
- **Cold start (curriculum-aware)** — a student with no live weaknesses is handed the easiest *un-mastered* node whose prerequisite is already satisfied, so they progress through the graph (`ADDITION → SUBTRACTION → MULTIPLICATION → DIVISION → FRACTIONS → …`) instead of being stuck on one topic.
- **Prerequisite descent** — for an active weakness, the engine walks the prerequisite chain to the *deepest still-failing* prerequisite and drills that.
- **Exploration (30%)** — occasionally serves a previously-attempted non-weakness node for lightweight spaced repetition.

### Exercise generation (self-replenishing pool)

When a node's exercise pool drops below `MIN_POOL_SIZE` (8), `StudentProgressService` publishes an `ExercisePoolLowEvent`. `ExercisePoolListener` handles it **on a background thread** (the student's request never blocks): it calls FastAPI `/generate-exercises`, which asks the LLM for candidates, **SymPy-verifies each one**, and falls back to deterministic templates if the LLM is unavailable or produces nothing valid. Verified problems are persisted for future sessions.

### Database schema

All tables extend `BaseEntity` (`id` UUID, `created_at`, `updated_at`, `is_active` soft-delete flag).

| Table | Notes |
|-------|-------|
| `students` | `email` (unique), BCrypt `password`, `display_name` |
| `knowledge_nodes` | `node_code` (unique), `title`, `difficulty_level` (1–5), `prerequisite_node_id` (self-FK → forms the curriculum graph) |
| `exercises` | FK → node, `question_text`, `correct_answer`, `difficulty_level` |
| `lessons` | FK → node, `intro`, `theory`, `examples`, `tip`, `estimated_minutes` |
| `interaction_logs` | Append-only ledger: FK student + tested node, `original_equation`, `student_input`, `is_correct`, `ai_identified_weakness_code`, `ai_explanation` |

### Seeded data

On startup, `DataSeeder` (idempotent) populates the **8 knowledge nodes** that form the curriculum graph, their exercises, and their lessons:

| Code | Title | Difficulty |
|------|-------|-----------|
| `ARITH_ADDITION` | Addition | 1 |
| `ARITH_SUBTRACTION` | Subtraction | 1 |
| `ARITH_MULTIPLICATION` | Multiplication | 2 |
| `ARITH_DIVISION` | Division | 2 |
| `FRACTIONS_SIMPLIFY` | Simplifying Fractions | 3 |
| `FRACTIONS_ADD_SUB` | Adding & Subtracting Fractions | 3 |
| `ALGEBRA_LINEAR` | Linear Equations | 4 |
| `ALGEBRA_FACTORIZE` | Factorization | 5 |

### Security & errors

- `JwtAuthFilter` runs before Spring's auth filter on every request: it validates the JWT, loads the `Student`, and sets it as the security principal. Controllers read it via `@AuthenticationPrincipal Student`.
- `SecurityConfig` makes `/api/auth/**` public and everything else authenticated.
- The JWT secret is read from the `JWT_SECRET` env var (a dev-only placeholder lives in `application.properties`).
- `GlobalExceptionHandler` returns a consistent JSON envelope (`status`, `code`, `message`, `timestamp`, `path`, `fieldErrors`) that the Flutter `ApiException` hierarchy parses.

---

## Layer 3 — FastAPI AI Service

**Directory:** `ai-python/` · **Python 3.11** · **FastAPI 0.135.1** · **LLM:** Ollama `llama3.2:3b`

A modular service that combines deterministic math (SymPy) with a small LLM kept honest by retrieval and validation.

### Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | Liveness probe (no LLM / DB touch) |
| POST | `/check-answer` | **SymPy** symbolic-equivalence check. Returns `is_correct` + `used_symbolic_check`. Falls back to trimmed string compare on unparseable input. |
| POST | `/evaluate-error` | Diagnoses a wrong answer: retrieves relevant corpus excerpts (RAG), builds a structured prompt, calls the LLM, validates the returned weakness code. Returns `{ weakness_node, explanation }`. |
| POST | `/generate-exercises` | Generates problems for a node: LLM candidates → SymPy verification → deterministic-template fallback. |

### RAG pipeline (`rag/`)

- **Corpus** — 8 markdown files in `corpus/`, one per knowledge node, written in the curriculum's vocabulary (with common error patterns).
- **Ingest** — `python -m rag.ingest` chunks each file, embeds it with `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, and stores it in a persistent ChromaDB collection (idempotent via per-file hashing).
- **Retrieve** — at diagnosis time, the retriever pulls the top-k excerpts for the relevant node and injects them into the prompt. **Graceful degradation:** any failure (no store, empty collection, embedding error) returns `[]`, so the service still answers — just without RAG grounding.

### Diagnosis & generation safeguards

- `evaluation/diagnostician.py` builds a structured prompt: the SymPy verdict + RAG excerpts + the closed list of valid weakness codes, demanding JSON-only output. The response is **post-validated** — a hallucinated code falls back to the topic node (with a warning) so the backend never receives a code it can't resolve.
- Generation never trusts the LLM's arithmetic: every candidate is re-derived/verified with SymPy before it can enter the pool.

### Adapter pattern (`adapters/ai_adapters.py`)

```
AIEngineAdapter (ABC)
├── LocalModelAdapter  → Ollama at MODEL_URL/api/generate
└── CloudAPIAdapter    → any OpenAI-compatible API
```

The active adapter is chosen at startup from `AI_MODE`, with fail-fast validation of required env vars.

### Environment variables

| Variable | Required for | Description |
|----------|--------------|-------------|
| `AI_MODE` | always | `local` or `cloud` |
| `MODEL_URL` | `local` | Ollama base URL, e.g. `http://localhost:11434` (the adapter appends `/api/generate`) |
| `MODEL_NAME` | `local` | e.g. `llama3.2:3b` |
| `CLOUD_API_KEY` / `CLOUD_API_URI` | `cloud` | Bearer token + endpoint for the cloud API |

Copy `.env.example` to `.env` for standalone runs (Docker Compose supplies these directly).

---

## Infrastructure

`docker-compose.yml` defines four services (sensible defaults via `${VAR:-default}`, so `docker compose up` works without a `.env`):

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `postgres` | postgres:16 | 5432 | Main database |
| `pgadmin` | dpage/pgadmin4 | 5050 | Database GUI (`admin@mathwise.com` / `admin`) |
| `ollama` | ollama/ollama | 11434 | Local LLM runtime |
| `ai-service` | `./ai-python` | 8000 | FastAPI AI service |

Spring Boot runs **outside** Docker (`./mvnw`) on port 9090 and connects to `localhost:5432`.

---

## Getting Started

### Prerequisites
Docker + Docker Compose · Java 21 + Maven · Flutter SDK ^3.11.1 · an Android emulator or device.

```bash
# 1. Infrastructure (Postgres, PGAdmin, Ollama, FastAPI)
docker compose up -d

# 2. Pull the model (first run only — ~2 GB)
docker exec -it mathwise-ollama ollama pull llama3.2:3b

# 3. (Recommended) populate the RAG vector store from the course corpus.
#    Skip it and diagnosis still works — just without RAG grounding.
docker exec -it mathwise-ai python -m rag.ingest

# 4. Backend — creates tables + seeds nodes/exercises/lessons on first run
cd backend-springboot/math-wise-backend
./mvnw spring-boot:run

# 5. Frontend
cd frontend-flutter
flutter pub get
flutter run
```

> **Before deploying:** set a real `JWT_SECRET` (32+ chars) and change the dev database password (`0`) in both `docker-compose.yml` and `application.properties`.

---

## Testing & CI

| Layer | Command | Coverage |
|-------|---------|----------|
| **Backend** | `./mvnw verify` | Surefire unit tests + Failsafe integration tests (`AuthControllerIT`) against a **real Postgres via Testcontainers** |
| **AI service** | `pip install -r requirements-dev.txt && pytest` | SymPy answer check, deterministic generators, diagnostician (mocked LLM + post-validation), RAG retriever (mocked Chroma + graceful degradation) |
| **Frontend** | `flutter analyze && flutter test` | Static analysis + `api_exception` parsing tests |

All three run on every push/PR via GitHub Actions (`.github/workflows/`), with Dependabot keeping dependencies current.

---

## Data Flow — submitting an answer

```
1. Flutter → POST /api/exercises/evaluate   { node_code, equation, correct_answer, student_answer }
             Authorization: Bearer <jwt>

2. JwtAuthFilter      → validates JWT, loads Student as principal

3. AiEvaluationService
   a. POST /check-answer  (FastAPI/SymPy)  → is_correct?
   b. if WRONG: POST /evaluate-error  (FastAPI)
        → RAG retrieves corpus excerpts for the node
        → structured prompt → llama3.2:3b → { weakness_node, explanation }
        → weakness normalised to a canonical node code
      if CORRECT: LLM skipped
   c. persist InteractionLog (audit trail)
   d. return { is_correct, weakness_node, explanation } to Flutter

4. FeedbackScreen     → banner + explanation + weakness chip
5. Next GET /next-exercise → the new log feeds the adaptive engine,
   steering toward the (still-unmastered) weak topic
```

---

## Development Notes

- **Correctness is symbolic, not string-based.** SymPy (`/check-answer`) treats `2/3 ⇔ 4/6`, `0.5 ⇔ 1/2`, and `(x+2)(x+3) ⇔ x²+5x+6` as equal — the LLM is never the judge of right/wrong.
- **The small model is fenced in.** RAG grounds its *content*, the structured prompt fixes its *shape*, and post-validation guarantees a resolvable weakness code. It also never sees correct answers (the backend short-circuits those).
- **The audit trail is append-only.** `interaction_logs` rows are never updated or deleted (only `is_active` toggled), making them a reliable basis for the adaptive engine and future analytics.
- **Secrets:** `JWT_SECRET` and the DB password are env-driven with dev placeholders — replace them before any real deployment. CORS is currently permissive and should be restricted in production.
</content>
