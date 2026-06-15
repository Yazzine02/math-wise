# Math-Wise

[![Backend CI](https://github.com/Yazzine02/math-wise/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Yazzine02/math-wise/actions/workflows/backend-ci.yml)
[![AI service CI](https://github.com/Yazzine02/math-wise/actions/workflows/ai-service-ci.yml/badge.svg)](https://github.com/Yazzine02/math-wise/actions/workflows/ai-service-ci.yml)
[![Frontend CI](https://github.com/Yazzine02/math-wise/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/Yazzine02/math-wise/actions/workflows/frontend-ci.yml)

An AI-powered adaptive math tutor, built as a **microservices platform**. Students practise arithmetic, fractions, and algebra; every answer is **verified symbolically** (SymPy), every *wrong* answer is **diagnosed by a local LLM grounded in a curated course corpus** (RAG), and the app **adapts which concept to drill next** based on each student's evolving weaknesses.

The backend is split into a **Spring Cloud Gateway + three domain services** sharing a PostgreSQL database, plus a separate **FastAPI AI service** in front of a local **Ollama** model. A **Flutter** app is the client. The whole platform runs with one `docker compose up`.

---

## Highlights

- **Microservices** — an API gateway fronts three independently-deployable Spring Boot services (auth, content, practice); the gateway preserves the exact public API so the client is unaware of the split.
- **Deterministic correctness** — answers are checked with SymPy, so equivalent forms all count (`1/2 ⇔ 0.5`, `(x+2)(x+3) ⇔ x²+5x+6`). The LLM never decides *whether* you're right.
- **RAG-grounded diagnosis** — wrong answers are explained by `llama3.2:3b`, grounded in an 8-topic corpus retrieved via ChromaDB + sentence-transformers, so explanations paraphrase real pedagogy instead of hallucinating.
- **Adaptive engine** — mastery dissolution (3 consecutive correct retires a weakness), curriculum-aware cold-start, prerequisite-chain descent, and a 30% exploration mix for spaced review.
- **Self-replenishing exercise pool** — when a topic runs low the practice service asynchronously asks the LLM for new problems, each SymPy-verified, with deterministic templates as a guaranteed fallback.
- **Production hygiene** — stateless JWT auth validated independently by each service, a uniform error envelope, an append-only audit trail, Testcontainers integration tests, and CI across all layers.

---

## Architecture

```
                          ┌──────────────────────────────────┐
                          │        Flutter app (mobile)       │
                          │ Login · Dashboard · Courses ·     │
                          │ Exercise · AI Feedback            │
                          └────────────────┬─────────────────┘
                                           │ HTTP + JWT — every call hits :9090
                                           ▼
                          ┌──────────────────────────────────┐
                          │      API Gateway   (:9090)        │  Spring Cloud Gateway
                          │  path-based routing (no auth)     │  = the public contract
                          └───┬───────────────┬───────────┬───┘
            /api/auth/**      │   /api/courses/**          │  /api/student/**
                             ┌┘                │           └┐ /api/exercises/**
                             ▼                 ▼            ▼
                 ┌────────────────┐  ┌────────────────┐  ┌─────────────────────┐
                 │ auth-service   │  │ content-service│  │ practice-service     │
                 │ :9091          │  │ :9092          │  │ :9093                │
                 │ register/login │  │ courses,       │  │ adaptive engine,     │
                 │ JWT issuance   │  │ lessons,       │  │ evaluation, exercise │
                 │                │  │ DataSeeder     │  │ generation           │
                 │ owns: students │  │ owns: nodes,   │  │ owns: interaction_   │
                 │                │  │ lessons        │  │ logs                 │
                 └───────┬────────┘  └───────┬────────┘  └───┬──────────────┬───┘
                         │                   │               │              │ HTTP
                         └─────────┬─────────┴───────────────┘              ▼
                                   ▼  JDBC (shared DB, table ownership)  ┌──────────────────────────┐
                          ┌──────────────────┐                          │ FastAPI ai-service :8000 │
                          │ PostgreSQL :5432 │                          │ /check-answer  (SymPy)   │
                          └──────────────────┘                          │ /evaluate-error (RAG+LLM)│
                                                                        │ /generate-exercises      │
                                                                        └────────┬─────────┬───────┘
                                                              Ollama API         │         │ ChromaDB
                                                                                 ▼         ▼
                                                                       ┌──────────────┐ ┌──────────────┐
                                                                       │ Ollama :11434│ │ vector store │
                                                                       │ llama3.2:3b  │ │ (corpus)     │
                                                                       └──────────────┘ └──────────────┘
```

**Key boundaries**
- The Flutter app talks **only** to the gateway (:9090). The gateway is a dumb router — it adds no auth, it just forwards each path family to the owning service.
- **JWT is stateless**: auth-service *issues* tokens; every service *validates* them locally with the same secret. No service calls auth-service at runtime.
- **Shared database, table ownership** — all services use one Postgres; each owns a documented set of tables (this is migration step 1; database-per-service is the future evolution).
- Only **practice-service** calls the FastAPI AI service; only FastAPI calls Ollama.

---

## Tech stack

| Layer | Technology | Role |
|-------|-----------|------|
| **Client** | Flutter / Dart 3 (SDK ^3.11.1) | Cross-platform mobile app |
| | Provider | State management (`ChangeNotifier`) |
| | go_router | Declarative routing + auth guard |
| | http · shared_preferences | REST calls · JWT persistence |
| **Gateway** | Spring Cloud Gateway (server-webmvc 5.0.1) | Path-based reverse proxy on :9090 |
| **Services** | Java 21 · Spring Boot 4.0.3 | auth / content / practice services |
| | Spring Web MVC | REST controllers |
| | Spring Data JPA / Hibernate | Persistence |
| | Spring Security + `jjwt` 0.12.5 | Stateless JWT (HMAC-SHA256), BCrypt |
| | Maven multi-module reactor | One build, shared `common` library |
| **AI service** | Python 3.11 · FastAPI 0.135.1 | SymPy checking, RAG diagnosis, generation |
| | SymPy | Deterministic answer verification |
| | ChromaDB · sentence-transformers | RAG retrieval over the course corpus |
| | Ollama · `llama3.2:3b` | Local LLM (pluggable cloud adapter) |
| **Data** | PostgreSQL 16 | Shared relational store |
| **Infra / CI** | Docker + Docker Compose | One-command local platform |
| | GitHub Actions · Dependabot | CI for all three layers |

---

## Repository structure

```
math-wise/
├── docker-compose.yml          # the whole platform: db, ai, ollama, 3 services + gateway
├── frontend-flutter/           # Flutter mobile app (client)
├── ai-python/                  # FastAPI AI service (SymPy + RAG + generation)
│   ├── main.py · exercise_templates.py
│   ├── adapters/ · evaluation/ · models/ · rag/ · corpus/ · tests/
└── backend-springboot/         # Maven reactor (the microservices)
    ├── pom.xml                 # parent + aggregator (Boot 4.0.3, BOMs, failsafe)
    ├── mvnw, .mvn/             # one wrapper for the whole reactor
    ├── common/                 # shared jar: entities, repositories, DTOs,
    │                           #   error envelope, JwtUtil + JwtAuthFilter
    ├── auth-service/           # :9091  students, register/login, JWT issuance
    ├── content-service/        # :9092  courses, lessons, DataSeeder
    ├── practice-service/       # :9093  adaptive engine, evaluation, generation
    └── gateway/                # :9090  Spring Cloud Gateway (public entry point)
```

---

## The backend services

### Gateway (:9090)
Spring Cloud Gateway forwards the monolith's exact route families to the owning service (targets are env-var driven, defaulting to localhost):

| Path pattern | → Service |
|--------------|-----------|
| `/api/auth/**` | auth-service (:9091) |
| `/api/courses/**` | content-service (:9092) |
| `/api/student/**`, `/api/exercises/**` | practice-service (:9093) |

It carries no security state — JWTs pass through and each service validates them.

### auth-service (:9091) — owns `students`
`POST /api/auth/register` and `/api/auth/login`. The only service with **public** endpoints and a `PasswordEncoder` (BCrypt). Issues HMAC-SHA256 JWTs; login is enumeration-safe (same error for unknown email vs. wrong password).

### content-service (:9092) — owns `knowledge_nodes`, `lessons`
`GET /api/courses` (lessons ordered by difficulty) and `GET /api/courses/{nodeCode}` (intro, theory, examples, tip). Hosts **DataSeeder**, the single seeding owner — it idempotently populates the 8-node curriculum graph + exercises + lessons on first boot.

### practice-service (:9093) — owns `interaction_logs`
The write-heavy, AI-coupled heart of the app. The only service that calls FastAPI.

- **Two-stage evaluation** (`POST /api/exercises/evaluate`): (1) call FastAPI `/check-answer` — SymPy decides correctness, authoritative; (2) **only when wrong**, call `/evaluate-error` for an LLM diagnosis (passing the node + its prerequisite chain). The returned weakness is normalised to a canonical node code; an `InteractionLog` row is persisted transactionally. Correct answers skip the LLM entirely.
- **Adaptive engine** (`GET /api/student/next-exercise`, `GET /api/student/progress`): 30-day weakness window; **mastery dissolution** (last 3 attempts all correct → retired); **curriculum-aware cold-start** (easiest un-mastered node whose prerequisite is satisfied); prerequisite-chain descent; 30% exploration.
- **Self-replenishing pool**: when a node's pool drops below 8, an `@Async` event triggers FastAPI `/generate-exercises` (LLM candidates → SymPy-verified → deterministic-template fallback) on a background thread.

### common (shared library)
JPA entities (all extending `BaseEntity`: UUID PK, `created_at`/`updated_at`, `is_active` soft-delete), Spring Data repositories (incl. the JPQL weakness aggregation), wire DTOs, the `GlobalExceptionHandler` uniform error envelope, and the JWT primitives. Shared because of the shared-database topology.

### Database & seeded curriculum
The 8 knowledge nodes form a prerequisite graph (`prerequisite_node_id` self-FK):

| Code | Title | Difficulty | Prerequisite |
|------|-------|-----------|--------------|
| `ARITH_ADDITION` | Addition | 1 | — |
| `ARITH_SUBTRACTION` | Subtraction | 1 | Addition |
| `ARITH_MULTIPLICATION` | Multiplication | 2 | Addition |
| `ARITH_DIVISION` | Division | 2 | Multiplication |
| `FRACTIONS_SIMPLIFY` | Simplifying Fractions | 3 | Division |
| `FRACTIONS_ADD_SUB` | Adding & Subtracting Fractions | 3 | Simplifying Fractions |
| `ALGEBRA_LINEAR` | Linear Equations | 4 | Subtraction |
| `ALGEBRA_FACTORIZE` | Factorization | 5 | Linear Equations |

---

## The AI service (FastAPI, :8000)

Combines deterministic math with a small LLM fenced by retrieval and validation.

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Liveness probe |
| `POST /check-answer` | SymPy symbolic-equivalence check (authoritative) |
| `POST /evaluate-error` | RAG retrieval → structured prompt → LLM → weakness-code post-validation |
| `POST /generate-exercises` | LLM candidates → SymPy verify → deterministic-template fallback |

- **RAG** (`rag/`): 8 markdown corpus files (one per node) embedded with `paraphrase-multilingual-MiniLM-L12-v2` into ChromaDB; `python -m rag.ingest` builds the store. Retrieval **degrades gracefully** to `[]` on any failure — the service still answers.
- **Adapter pattern** (`adapters/`): `AI_MODE=local` → Ollama, `AI_MODE=cloud` → any OpenAI-compatible API. Flips with one env var, no code change.
- **Five layers of model control**: SymPy verdict → RAG grounding → structured prompt → JSON contract → post-validation. The LLM never decides correctness and its arithmetic is never trusted (generated exercises are re-derived by SymPy).

---

## Service ports

| Service | Port | Exposed to host? |
|---------|------|-------------------|
| gateway | 9090 | ✅ (the only public entry point) |
| auth-service | 9091 | internal |
| content-service | 9092 | internal |
| practice-service | 9093 | internal |
| ai-service (FastAPI) | 8000 | ✅ |
| Ollama | 11434 | ✅ |
| PostgreSQL | 5432 | ✅ |
| PGAdmin | 5050 | ✅ (`admin@mathwise.com` / `admin`) |

---

## Getting started

### Prerequisites
- **Docker + Docker Compose** (runs the entire backend platform)
- **Flutter SDK ^3.11.1** + an Android emulator or device (for the client)
- *(optional, for backend dev outside Docker)* JDK 21 — the Maven wrapper (`./mvnw`) is checked in

### 1. Bring up the whole platform
```bash
# Builds the 4 Java service images + starts Postgres, PGAdmin, Ollama, FastAPI,
# auth/content/practice services and the gateway. First build pulls deps (~few min).
docker compose up --build -d
```
On first boot, content-service's `DataSeeder` creates the schema and seeds the 8-node curriculum + exercises + lessons.

### 2. Pull the LLM model (first run only, ~2 GB)
```bash
docker exec -it mathwise-ollama ollama pull llama3.2:3b
```

### 3. (Recommended) populate the RAG vector store
```bash
# Skip it and diagnosis still works — just without RAG grounding.
docker exec -it mathwise-ai python -m rag.ingest
```

### 4. Run the Flutter client
```bash
cd frontend-flutter
flutter pub get
flutter run
```
The app targets `http://10.0.2.2:9090` (the Android-emulator alias for the host's gateway port). For a physical device, set `--dart-define=API_BASE_URL=http://<your-LAN-ip>:9090`.

### Smoke-test the API (through the gateway)
```bash
TOKEN=$(curl -s localhost:9090/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"a@b.c","password":"hunter22","display_name":"Ali"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
curl -s localhost:9090/api/courses -H "Authorization: Bearer $TOKEN"
curl -s localhost:9090/api/student/next-exercise -H "Authorization: Bearer $TOKEN"
```

### Backend dev loop (one service on the host)
```bash
cd backend-springboot
./mvnw -pl practice-service spring-boot:run     # others stay in compose
```

> **Before deploying:** set a real `JWT_SECRET` (32+ chars, identical across all services) and change the dev DB password (`0`). CORS is permissive by default.

---

## Running the tests

| Layer | Command | What runs |
|-------|---------|-----------|
| **Backend (all services)** | `cd backend-springboot && ./mvnw -B verify` | Unit tests (Surefire) + integration tests (Failsafe) across every module — `AuthControllerIT`, `CourseControllerIT`, `EvaluationFlowIT`, `GatewayRoutingTest` — using a **real Postgres via Testcontainers** (needs Docker running). |
| **AI service** | `cd ai-python && pip install -r requirements-dev.txt && pytest` | SymPy answer check, deterministic generators, diagnostician (mocked LLM), RAG retriever (mocked Chroma). |
| **Frontend** | `cd frontend-flutter && flutter analyze && flutter test` | Static analysis + `api_exception` parsing tests. |

All three run on every push/PR via GitHub Actions; the backend workflow builds the full reactor.

---

## Data flow — submitting an answer

```
Flutter ─POST /api/exercises/evaluate─► gateway :9090 ─► practice-service :9093
  { node_code, equation, correct_answer, student_answer } + Bearer JWT
        │
        ▼  practice-service:
   1. JwtAuthFilter validates the token locally, loads the Student
   2. POST /check-answer  → FastAPI/SymPy → is_correct?
   3. if WRONG → POST /evaluate-error → FastAPI:
        RAG excerpts → structured prompt → llama3.2:3b → { weakness_node, explanation }
        → weakness normalised to a canonical code
      if CORRECT → LLM skipped
   4. persist InteractionLog (append-only audit row)
   5. return { is_correct, weakness_node, explanation }
        │
        ▼
Flutter FeedbackScreen → banner + explanation + weakness chip
Next GET /api/student/next-exercise → the new log feeds the adaptive engine
```

---

## Design notes

- **The microservice split was cheap because the monolith was built for it.** Stateless JWT (no session affinity), env-var config (no hardcoded hosts), DTOs decoupled from entities, and an in-process event for async work — each became a seam to cut along. The gateway preserving port 9090 means the Flutter client never changed.
- **Correctness is symbolic, not string-based** — SymPy is the sole judge of right/wrong; the LLM only explains, and is fenced by five independent layers.
- **Everything degrades gracefully** — LLM down → SymPy still grades + deterministic templates still generate; RAG down → ungrounded-but-valid explanations; the AI adapter swaps local↔cloud via one env var.
- **The audit trail is append-only** — `interaction_logs` rows are never updated/deleted, which is exactly what the adaptive engine aggregates over.

See `docs/release-0.0.1/` for the phase-by-phase build history (phase-12 documents this microservices split; phase-11 the cloud-deployment design).
