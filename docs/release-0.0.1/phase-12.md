# Phase 12 — Microservices: splitting the monolith

**Branch:** `feat/microservices`
**Status:** Shipped. The Spring Boot monolith is gone; in its place: three
domain services + a gateway, all behind the same public contract.

Like Phases 9–11, this doc teaches the concepts alongside the implementation.
Read it before touching the service boundaries.

---

## 1. The one-sentence summary

The monolith was carved into **auth-service** (:9091), **content-service**
(:9092) and **practice-service** (:9093), fronted by a **gateway** (:9090)
that forwards the monolith's exact routes — so the Flutter app, the FastAPI
service, and the database noticed *nothing*.

```
Flutter ──► gateway :9090 ──┬─ /api/auth/**                       ─► auth-service     :9091 ─┐
                            ├─ /api/courses/**                    ─► content-service  :9092 ─┼─► postgres
                            └─ /api/student/** , /api/exercises/** ─► practice-service :9093 ─┘
                                                                            │
                                                                            ▼
                                                                     FastAPI ai-service :8000 ─► ollama
```

The FastAPI AI service was *already* a microservice — this phase brought the
Java side up to the same shape.

---

## 2. Why these three services (and not five, or two)

Service boundaries should follow **domain seams**, not class counts. The
monolith had three natural ones, visible in its own controller layout:

| Service | Owns (tables) | Serves | Why it's a real seam |
|---|---|---|---|
| **auth-service** | `students` | `/api/auth/**` | Identity has different security needs (public endpoints, password hashing) and different change cadence than everything else. |
| **content-service** | `knowledge_nodes`, `lessons` (+ seed-writes `exercises`) | `/api/courses/**` | The curriculum is read-mostly reference data. It also owns **DataSeeder** — exactly one service seeds, so there's no startup race. |
| **practice-service** | `interaction_logs` (+ runtime-writes `exercises`) | `/api/student/**`, `/api/exercises/**` | The adaptive engine, the two-stage evaluation, the async pool top-up — the write-heavy, AI-coupled heart of the app. The ONLY service that calls FastAPI. |

The **gateway** is not a domain service — it's plumbing. It exists for one
reason: *contract preservation* (§4).

---

## 3. The big decision: one shared database

The textbook says "database per service." We deliberately did **not** do that
(yet). All three services point at the same `mathwise_db`; each service owns
specific tables (the table in §2 is the ownership contract); the JPA
entities/repositories live in a shared **`common`** module.

Why this is the right *first* step and not a cop-out:

1. **The adaptive engine is SQL.** `findRecentTopWeaknessesByStudentId` joins
   `interaction_logs → students/knowledge_nodes` in one JPQL query. With
   database-per-service, those joins become REST calls + client-side merges —
   a rewrite of the engine, for zero user-visible benefit today.
2. **Migration is incremental by design.** "Shared DB, split compute" is the
   standard strangler step: you get independent deployability, scaling and
   fault isolation NOW, and you peel off databases later, one service at a
   time (auth's `students` table is the natural first candidate — nothing
   joins to it except logs).
3. **The risk profile matched the constraint** ("don't break anything"):
   zero data migration, zero contract changes.

What we gave up (and accept): schema coupling (a migration touching a shared
table coordinates across services) and the discipline burden that table
ownership is enforced by convention, not by network isolation.

**Schema management note:** every service still runs `ddl-auto=update`.
That's safe here because they map the *same* entity classes (identical DDL,
additive only) — but it's the next thing to harden (Flyway, one owner per
table's migrations).

---

## 4. Contract preservation — why nothing broke

Three contracts could have broken. Here's what pinned each one:

| Contract | Mechanism | Proof |
|---|---|---|
| **Flutter → backend** | Gateway listens on the monolith's port (9090) and forwards the exact path families, unmodified. Same JSON shapes (the DTOs moved to `common`, not changed). | `GatewayRoutingTest` pins the route table; the compose smoke test drives the real app flow through :9090. |
| **backend → FastAPI** | The calling code (`AiEvaluationService`, `ExerciseGenerationService`) moved into practice-service VERBATIM — same DTOs, same `ai-service.url` property. FastAPI untouched. | `EvaluationFlowIT` stubs the boundary with the same request/response types. |
| **Auth tokens** | JWTs are stateless: auth-service ISSUES, every service VALIDATES with the same `JWT_SECRET` (the `JwtAuthFilter`/`JwtUtil` pair moved to `common`). No service ever calls auth-service at runtime. | `AuthControllerIT` moved to auth-service **unmodified** and stayed green; content/practice ITs mint tokens with the same util the filter verifies. |

The deeper lesson: **the monolith's earlier design decisions are what made
this split cheap.** Stateless JWT (no session affinity), env-var config (no
hardcoded hosts), DTOs decoupled from entities, and an in-process event for
the async work — every one of those became a seam we could cut along.

---

## 5. The Maven reactor

```
backend-springboot/
├── pom.xml            ← mathwise-parent: aggregator + parent (Boot 4.0.3,
│                         testcontainers BOM, jjwt versions, failsafe wiring)
├── mvnw, .mvn/        ← one wrapper for the whole reactor
├── common/            ← plain jar: entities, repositories, DTOs, error
│                         envelope, JwtUtil + JwtAuthFilter
├── auth-service/      ← :9091 (+ its own SecurityConfig with permitAll /api/auth/**)
├── content-service/   ← :9092 (+ DataSeeder)
├── practice-service/  ← :9093 (+ @EnableAsync — without it the pool top-up
│                         silently blocks the student's request thread)
└── gateway/           ← :9090 (Spring Cloud Gateway server-webmvc 5.0.1,
                          pinned HERE so a Spring Cloud issue can't break
                          the other modules)
```

Per-service notes worth remembering:
- Every `*ServiceApplication` declares explicit scan roots
  (`scanBasePackages`, `@EntityScan`, `@EnableJpaRepositories`) because the
  shared code lives outside its package tree. Boot 4 relocation: `@EntityScan`
  is now `org.springframework.boot.persistence.autoconfigure.EntityScan`.
- **SecurityConfig is per-service policy**, not shared: auth-service is the
  only one with public routes and a `PasswordEncoder`; the other two
  authenticate everything.
- The gateway routes are **Java DSL** (`GatewayRouterFunctions.route(...)
  .route(path(...), http()).before(uri(target))`) — compile-time checked,
  env-var targets (`AUTH_SERVICE_URL` etc.) with localhost defaults.

---

## 6. Tests — what protects each seam

`./mvnw -B verify` at the reactor root runs everything (34 tests):

| Module | Tests | What they pin |
|---|---|---|
| auth-service | `AuthControllerIT` (6) — **moved unmodified** | register/login contract, validation envelope, enumeration safety |
| content-service | `CourseControllerIT` (4) — new | the REAL seeded curriculum (8 lessons, difficulty order), lesson detail, 404 envelope, auth required |
| practice-service | 17 unit (moved) + `EvaluationFlowIT` (5) — new | correct answers NEVER call the LLM; hallucinated weakness labels normalise to canonical codes before persisting; cold-start next-exercise; progress aggregation |
| gateway | `GatewayRoutingTest` (2) — new | all four public path families are registered |

Two techniques worth noting:
- **`@MockitoBean RestTemplate`** in `EvaluationFlowIT` replaces the FastAPI
  boundary while the DATABASE stays real (Testcontainers Postgres) — the
  adaptive engine's SQL is exercised for real; only the AI is scripted.
- Each service has its **own** `TestcontainersConfiguration` — services must
  be independently testable; sharing test infra across services would quietly
  re-couple them.

**Testcontainers 2.0.5 upgrade (driveby fix):** modern Docker Desktop engines
enforce `MinAPIVersion 1.40` and answer old API clients (v1.32) with a stub
400 — which is why the IT suite previously only ran in CI. TC 2.x (renamed
artifacts `testcontainers-postgresql`/`-junit-jupiter`, no SELF generic)
fixed local runs.

---

## 7. Running it

```bash
# Everything (postgres, pgadmin, ollama, FastAPI, 3 services, gateway):
docker compose up --build -d

# The app still lives at :9090 — Flutter needs NO change.
curl -s localhost:9090/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"a@b.c","password":"hunter22","display_name":"Ali"}'

# Dev loop for one service on the host (others in compose):
cd backend-springboot && ./mvnw -pl practice-service spring-boot:run
```

Compose wiring to know about: only the gateway publishes a host port; the
services are internal DNS names (`http://auth-service:9091`, …). The same
images run on a laptop and (Phase 11's design) on ECS — the URLs are env vars.

---

## 8. What's deliberately NOT in this phase

- **Database per service** — §3. First candidate when it happens: auth.
- **Kafka** — the pool top-up is still the in-process `@Async @EventListener`,
  now wholly inside practice-service. Phase 13 replaces that glue with a
  broker; producer and worker code stay put.
- **Service discovery / circuit breakers** — env-var URLs and the existing
  RestTemplate timeouts are enough for 4 services on one network. Eureka/
  Resilience4j earn their complexity at a larger scale.
- **Distributed tracing** — with one gateway and three services, logs still
  suffice. Revisit when a request fans out.
- **Flyway** — required before any service schema diverges. Tracked.

---

## 9. Why this was Phase 12

Phase 9 built the tests, 10 automated them, 11 designed the runtime. A
refactor of this size is only safe with all three in place: every carve-out
step ended with the full suite green, and the cut was made along seams that
phases 1–8 had (sometimes accidentally) kept clean. The next phase (13,
Kafka) replaces the last in-process coupling — the async event — with real
infrastructure, and it now has a service topology to land in.
