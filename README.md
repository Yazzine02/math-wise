# Math-Wise

An AI-powered mobile app that helps students get better at mathematics. The app identifies weaknesses by analysing incorrect answers with a local LLaMA model, then adapts which exercise to give next based on what the student struggles with most.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Flutter App (Mobile)                     │
│   Login / Register → Dashboard → Exercise → AI Feedback        │
└────────────────────────────┬────────────────────────────────────┘
                             │  HTTP + JWT Bearer token
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Boot Backend   (port 9090)                  │
│   Auth  ·  Exercise selection  ·  Progress tracking  ·  DB     │
└──────────────────┬──────────────────────────────────────────────┘
                   │  HTTP (internal)
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│              FastAPI AI Service   (port 8000)                   │
│   Receives wrong answer → calls Ollama → returns diagnosis      │
└──────────────────┬──────────────────────────────────────────────┘
                   │  HTTP (Ollama API)
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│              Ollama  (port 11434)  —  llama3.2:3b               │
└─────────────────────────────────────────────────────────────────┘
```

The three services are completely decoupled. Spring Boot is the only layer the Flutter app talks to. Spring Boot is the only layer that calls FastAPI. FastAPI is the only layer that calls Ollama.

---

## Repository Structure

```
math-wise/
├── docker-compose.yml          # PostgreSQL, PGAdmin, Ollama, FastAPI
├── frontend-flutter/           # Flutter mobile app
├── backend-springboot/         # Spring Boot REST API + business logic
└── ai-python/                  # FastAPI service that wraps Ollama
```

---

## Layer 1 — Flutter Frontend

**Directory:** `frontend-flutter/`  
**Language:** Dart 3 · Flutter SDK ^3.11.1  
**State management:** Provider  
**Navigation:** go_router  
**HTTP:** dart:http + SharedPreferences (JWT storage)

### Screen Flow

```
LoginScreen / RegisterScreen
         │
         ▼  (JWT stored in SharedPreferences)
    HomeScreen  (Dashboard)
    ├── Shows student's name
    ├── Shows top 3 AI-identified weak areas with failure counts
    └── "Start Practice" button
              │
              ▼
       ExerciseScreen
       ├── Fetches adaptive next exercise from backend
       ├── Displays the question
       └── Student types answer → Submit
                 │
                 ▼
         FeedbackScreen
         ├── Correct / Incorrect banner
         ├── AI explanation of the mistake
         ├── Identified weakness topic
         ├── "Next Exercise" → back to ExerciseScreen
         └── "Back to Dashboard" → HomeScreen
```

### Key Files

| File | Purpose |
|------|---------|
| `lib/main.dart` | App entry, Provider setup |
| `lib/routing/app_router.dart` | GoRouter config, auth guard |
| `lib/providers/auth_provider.dart` | Auth state (isAuthenticated, displayName) |
| `lib/services/api_client.dart` | Shared HTTP helper — injects JWT on every request |
| `lib/services/auth_service.dart` | Login / register / logout API calls |
| `lib/services/exercise_service.dart` | Fetch next exercise, submit answer |
| `lib/services/progress_service.dart` | Fetch weakness summary |
| `lib/models/exercise.dart` | Exercise data model |
| `lib/models/ai_feedback.dart` | AI feedback data model |
| `lib/models/weakness_summary.dart` | Weakness summary data model |
| `lib/screens/home_screen.dart` | Dashboard with weakness cards |
| `lib/screens/exercise_screen.dart` | Exercise question + answer input |
| `lib/screens/feedback_screen.dart` | AI feedback result display |
| `lib/screens/login_screen.dart` | Login form |
| `lib/screens/register_screen.dart` | Registration form |

### Auth Guard

The router has a `redirect` function that watches `AuthProvider`. Until `isInitialized` is true (SharedPreferences checked), navigation is blocked. Unauthenticated users are always redirected to `/login`. Authenticated users cannot reach `/login`.

### API Base URL

The Flutter app targets `http://10.0.2.2:9090` — Android emulator's alias for `localhost`. Change this in `lib/services/api_client.dart` if running on a physical device.

---

## Layer 2 — Spring Boot Backend

**Directory:** `backend-springboot/math-wise-backend/`  
**Language:** Java 21  
**Framework:** Spring Boot 4.0.3  
**Database:** PostgreSQL via Spring Data JPA  
**Port:** 9090  
**Security:** JWT (HMAC-SHA256, 1-day expiry) via `jjwt 0.12.5`

### API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | No | Register a new student |
| POST | `/api/auth/login` | No | Authenticate, get JWT |
| GET | `/api/student/next-exercise` | Yes | Get adaptive next exercise |
| GET | `/api/student/progress` | Yes | Get top 3 weakness areas |
| POST | `/api/exercises/evaluate` | Yes | Submit answer + get AI feedback |

### Request / Response Shapes

**POST `/api/auth/register`**
```json
{ "email": "ali@example.com", "password": "secret", "display_name": "Ali" }
→ { "token": "<jwt>", "email": "ali@example.com", "display_name": "Ali" }
```

**POST `/api/auth/login`**
```json
{ "email": "ali@example.com", "password": "secret" }
→ { "token": "<jwt>", "email": "ali@example.com", "display_name": "Ali" }
```

**GET `/api/student/next-exercise`**  
*(Requires: `Authorization: Bearer <token>`)*
```json
{
  "id": "uuid",
  "node_code": "ALGEBRA_LINEAR",
  "node_title": "Linear Equations",
  "question_text": "Solve for x: 3x - 4 = 11.",
  "correct_answer": "5",
  "difficulty_level": 4
}
```

**POST `/api/exercises/evaluate`**  
*(Requires: `Authorization: Bearer <token>`)*
```json
{
  "node_code": "ALGEBRA_LINEAR",
  "equation": "Solve for x: 3x - 4 = 11.",
  "correct_answer": "5",
  "student_answer": "3"
}
→ { "weakness_node": "ARITH_SUBTRACTION", "explanation": "..." }
```

**GET `/api/student/progress`**  
*(Requires: `Authorization: Bearer <token>`)*
```json
{
  "weaknesses": [
    { "node_code": "ARITH_SUBTRACTION", "node_title": "Subtraction", "failure_count": 4 },
    { "node_code": "FRACTIONS_SIMPLIFY", "node_title": "Simplifying Fractions", "failure_count": 2 }
  ]
}
```

### Database Schema

All tables extend a `BaseEntity` that provides: `id` (UUID), `created_at`, `updated_at`, `is_active` (soft-delete flag).

**`students`**
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| email | VARCHAR | Unique |
| password | VARCHAR | BCrypt hash |
| display_name | VARCHAR | |

**`knowledge_nodes`**  
Represents a math concept. Nodes form a prerequisite graph (e.g., Factorization requires Linear Equations).
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| node_code | VARCHAR | Unique identifier e.g. `ALGEBRA_LINEAR` |
| title | VARCHAR | Human-readable name |
| difficulty_level | INT | 1 (easy) – 5 (hard) |
| prerequisite_node_id | UUID | FK → knowledge_nodes (nullable) |

**`exercises`**  
Concrete math problems linked to a knowledge node.
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| knowledge_node_id | UUID | FK → knowledge_nodes |
| question_text | VARCHAR(1000) | The question shown to the student |
| correct_answer | VARCHAR | Expected answer |
| difficulty_level | INT | |

**`interaction_logs`**  
Append-only ledger of every answer attempt. Never deleted — is_active flag used for soft deletes if needed.
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| student_id | UUID | FK → students |
| tested_node_id | UUID | FK → knowledge_nodes |
| original_equation | VARCHAR(500) | The question text |
| student_input | VARCHAR | What the student typed |
| is_correct | BOOLEAN | |
| ai_identified_weakness_code | VARCHAR | Node code returned by AI (nullable) |
| ai_explanation | VARCHAR(1000) | Explanation returned by AI (nullable) |

### Seeded Data

On startup, `DataSeeder` runs (idempotent — skips if rows already exist) and populates:

**8 Knowledge Nodes:**
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

4 exercises per node = **32 exercises total**.

### Adaptive Exercise Selection

`StudentProgressService.getNextExercise()` implements a simple but effective adaptive algorithm:

1. Query `interaction_logs` for the student's incorrect answers, grouped by `ai_identified_weakness_code`, ordered by failure count descending.
2. Pick the knowledge node with the most failures as `targetNode`.
3. If the student has no history yet, pick a random knowledge node (cold start).
4. Pick a random exercise from `ExerciseRepository` for that `targetNode`.

This means the more a student struggles with a topic, the more the system steers them back to it.

### Security

- All endpoints under `/api/exercises/**` and `/api/student/**` require a valid JWT in the `Authorization: Bearer` header.
- `JwtAuthFilter` runs before Spring's `UsernamePasswordAuthenticationFilter` on every request. It extracts the email from the JWT, loads the `Student` from the database, and sets it as the `Authentication` principal in `SecurityContextHolder`.
- Controllers access the authenticated student via `@AuthenticationPrincipal Student student`.

### Key Files

| File | Purpose |
|------|---------|
| `entity/Student.java` | Student JPA entity |
| `entity/KnowledgeNode.java` | Math concept node entity |
| `entity/Exercise.java` | Exercise entity |
| `entity/InteractionLog.java` | Answer attempt audit log entity |
| `security/JwtUtil.java` | JWT generation and parsing |
| `security/JwtAuthFilter.java` | Per-request JWT authentication filter |
| `security/SecurityConfig.java` | Security chain — public vs. protected routes |
| `config/DataSeeder.java` | Seeds knowledge nodes and exercises on startup |
| `service/AiEvaluationService.java` | Calls FastAPI, persists InteractionLog |
| `service/StudentProgressService.java` | Weakness analysis + adaptive exercise selection |
| `controller/AuthController.java` | Register / login endpoints |
| `controller/AiEvaluationController.java` | POST /api/exercises/evaluate |
| `controller/StudentController.java` | GET /api/student/progress and next-exercise |

---

## Layer 3 — FastAPI AI Service

**Directory:** `ai-python/`  
**Language:** Python 3.11  
**Framework:** FastAPI 0.135.1  
**LLM runtime:** Ollama (llama3.2:3b by default)

### How it works

The service has one endpoint: `POST /evaluate-error`. It receives a math problem, the correct answer, and the student's wrong answer, constructs a structured prompt, and sends it to the LLM. The LLM is instructed to return a JSON object identifying:
- `weakness_node` — the underlying math concept the student is struggling with (e.g., `"ARITH_SUBTRACTION"`)
- `explanation` — a tutor-style explanation of what went wrong

### Adapter Pattern

The service uses a simple adapter pattern so you can switch between a local Ollama model and a remote cloud API without changing any business logic.

```
AIEngineAdapter (abstract)
├── LocalModelAdapter  → talks to Ollama at MODEL_URL/api/generate
└── CloudAPIAdapter    → talks to any OpenAI-compatible API
```

The active adapter is chosen at startup based on the `AI_MODE` environment variable.

### Environment Variables

| Variable | Required for | Description |
|----------|-------------|-------------|
| `AI_MODE` | Always | `"local"` or `"cloud"` |
| `MODEL_URL` | `AI_MODE=local` | Ollama base URL e.g. `http://localhost:11434` |
| `MODEL_NAME` | `AI_MODE=local` | Model tag e.g. `llama3.2:3b` |
| `CLOUD_API_KEY` | `AI_MODE=cloud` | Bearer token for cloud API |
| `CLOUD_API_URI` | `AI_MODE=cloud` | Full endpoint URL of cloud API |

Copy `.env.example` to `.env` and fill in your values.

### Prompt Design

```
You are an expert math tutor. Analyze the student's incorrect answer.
Equation: <question>
Correct Answer: <correct>
Student Answer: <student>

Return ONLY a JSON object with two keys:
  "weakness_node" (string) and "explanation" (string).
```

The `format: "json"` flag is passed to Ollama to enforce JSON output.

---

## Infrastructure

### Docker Compose

`docker-compose.yml` defines four services:

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `postgres` | postgres:16 | 5432 | Main database |
| `pgadmin` | dpage/pgadmin4 | 5050 | Database GUI |
| `ollama` | ollama/ollama | 11434 | Local LLM runtime |
| `ai-service` | ./ai-python (Dockerfile) | 8000 | FastAPI AI service |

Spring Boot runs outside Docker (started manually with `./mvnw`) and connects to `localhost:5432`.

**PGAdmin credentials:**  
URL: http://localhost:5050  
Email: `admin@mathwise.com`  
Password: `admin`

---

## Getting Started

### Prerequisites

- Docker + Docker Compose
- Java 21 + Maven
- Flutter SDK ^3.11.1
- Android emulator or physical device

### 1. Start the infrastructure

```bash
docker compose up -d
```

### 2. Pull the LLM model (first run only — ~2 GB download)

```bash
docker exec -it mathwise-ollama ollama pull llama3.2:3b
```

### 3. Start the Spring Boot backend

```bash
cd backend-springboot/math-wise-backend
./mvnw spring-boot:run
```

On first start, Hibernate creates all tables and `DataSeeder` seeds 8 knowledge nodes and 32 exercises. You can verify in PGAdmin at http://localhost:5050.

### 4. Run the Flutter app

```bash
cd frontend-flutter
flutter pub get
flutter run
```

The app targets `http://10.0.2.2:9090` (Android emulator alias for localhost). If running on a physical device, update `baseUrl` in `lib/services/api_client.dart` to your machine's local IP.

---

## Data Flow — Full Request Lifecycle

Here is what happens when a student submits an answer:

```
1. Flutter: POST /api/exercises/evaluate
   Body: { node_code, equation, correct_answer, student_answer }
   Header: Authorization: Bearer <jwt>

2. Spring Boot — JwtAuthFilter:
   Extracts email from JWT → loads Student from DB →
   sets Student as Authentication principal

3. Spring Boot — AiEvaluationController:
   Receives request → calls AiEvaluationService

4. Spring Boot — AiEvaluationService:
   a. Forwards { equation, correct_answer, student_answer }
      to FastAPI POST /evaluate-error
   b. FastAPI builds prompt → calls Ollama llama3.2:3b
   c. LLM returns { weakness_node, explanation }
   d. Spring Boot computes isCorrect (string equality, trimmed)
   e. Saves InteractionLog to PostgreSQL:
      { student, testedNode, equation, studentAnswer,
        isCorrect, aiIdentifiedWeaknessCode, aiExplanation }
   f. Returns AiFeedback to Flutter

5. Flutter — FeedbackScreen:
   Displays result banner + AI explanation + weakness label
```

On the next call to `GET /api/student/next-exercise`, the new `InteractionLog` row is included in the weakness aggregation, steering the adaptive algorithm toward the identified weak topic.

---

## Development Notes

- **JWT secret** is hardcoded in `JwtUtil.java` for development convenience. Move it to an environment variable (`application.properties` → `${JWT_SECRET}`) before any deployment.
- **Database password** (`0`) is a dev-only placeholder. Change it in `docker-compose.yml` and `application.properties` together.
- **Answer comparison** uses case-insensitive string equality. For fraction answers like `2/3` vs `0.667` or algebra answers like `(x+2)(x+3)` vs `(x+3)(x+2)`, a smarter comparison (symbolic math library) would improve accuracy in a future iteration.
- **CORS** is currently set to allow all origins (`*`). Restrict to your Flutter app's origin in production.
- The `InteractionLog` table is append-only by design — rows are never updated or deleted, only `is_active` is toggled for soft deletes. This makes it a reliable audit trail and supports future analytics features.
