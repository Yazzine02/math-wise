# Phase 11 — Continuous Delivery & Cloud Deployment (theoretical, AWS)

**Branch:** `release/0.0.1`
**Status:** **Theoretical / design phase — not shipped.** This is a paper
design: it maps the local Docker-Compose stack onto AWS, makes the
architectural decisions explicit, and explains the trade-offs. No
infrastructure is provisioned and no IaC is written here (that would be a
Phase 11b implementation phase).

Like Phases 9 and 10, treat this as a reference manual — here, for "how
would MathExp actually run in the cloud, and what did we decide and why."

> Phase 10 stopped at **CI** (every change is built and tested). Phase 11
> is the **CD** half: every green build becomes a *deployable artifact*,
> and we design where that artifact runs. We deliberately stop at
> *Continuous Delivery* (deploy is a human-approved step), not *Continuous
> Deployment* (auto-to-prod), because a solo project wants a hand on the
> lever.

---

## 1. What we're actually deploying

The first job of any deployment design is to classify each component by
**state** and **resource profile**, because those two properties decide
everything downstream (can it scale horizontally? does it need a volume?
does it need a GPU?).

| Component | Today (Compose) | State | Scales horizontally? | Special needs |
|---|---|---|---|---|
| **Flutter app** | emulator | client | n/a (it's the client) | store distribution, a backend URL |
| **Spring Boot API** | host `./mvnw` | **stateless** (JWT) | ✅ trivially | none |
| **FastAPI AI service** | container | **stateless** | ✅ trivially | reaches the LLM + the vector store |
| **Ollama / `llama3.2:3b`** | container | model weights on disk | ⚠️ hard (GPU) | **GPU (or slow CPU)** — the crux |
| **PostgreSQL** | container + volume | **stateful** | ✗ (managed instead) | durable storage, backups |
| **ChromaDB** | inside FastAPI image | **stateful-ish** | ✗ | a persistent volume *or* re-ingest |
| **PGAdmin** | container | dev tool | — | **dropped in prod** |

Two facts fall out of this table and drive the whole design:
1. **The two API tiers are stateless** — because authentication is JWT
   (Phase: security) with no server-side session. That means they're the
   *easy* part: put N copies behind a load balancer and you're done.
2. **The hard parts are the LLM and the two stateful stores.** Almost all
   of the design effort below is about those three boxes.

---

## 2. The target architecture (AWS)

We target **AWS** with **ECS Fargate** as the container runtime (§4
explains why Fargate over EKS). One picture:

```
                        Internet
                           │  HTTPS
                           ▼
                   Route 53  (DNS: api.mathexp.app)
                           │
                           ▼
              ┌───────────────────────────┐   ── public subnets (2 AZs) ──
              │ Application Load Balancer  │   TLS terminated here (ACM cert)
              └─────────────┬─────────────┘
   ══════════════════════ VPC ════════════════════════════════════════════
                           │  only Spring Boot is reachable from the ALB
                           ▼                ── private subnets (2 AZs) ──
                 ┌────────────────────┐
                 │ ECS Fargate        │  Spring Boot API  (≥2 tasks, autoscaled)
                 │  service: api      │
                 └───┬────────────┬───┘
        Cloud Map    │            │  JDBC (SG-restricted)
        (internal    │            ▼
         DNS)        │     ┌──────────────────┐
                     │     │ RDS PostgreSQL   │  Multi-AZ, automated backups
                     ▼     └──────────────────┘
                 ┌────────────────────┐
                 │ ECS Fargate        │  FastAPI AI service (≥2 tasks)
                 │  service: ai       │
                 └───┬────────────┬───┘
          AI_MODE    │            │  vector store
          decides ──►│            ▼
                     │     ┌──────────────────┐
                     │     │ ChromaDB (EFS)   │  or pgvector on RDS (§7)
                     │     └──────────────────┘
        ┌────────────┴───────────────┐
        ▼                            ▼
  AI_MODE=cloud                AI_MODE=local
  ┌───────────────────┐    ┌──────────────────────────┐
  │ Managed LLM API   │    │ GPU tier: ECS/EKS on g-   │
  │ (Bedrock / OpenAI │    │ class instances, self-    │
  │  / Together /...)  │    │ hosted Ollama (§6, Path B)│
  └───────────────────┘    └──────────────────────────┘

  Supporting: ECR (images) · Secrets Manager (JWT_SECRET, DB creds, API keys)
              CloudWatch (logs + metrics) · IAM (least-privilege task roles)
```

The **security posture in one sentence:** the ALB is the only thing with a
public face; everything else — FastAPI, RDS, ChromaDB, the LLM tier — lives
in private subnets and is reachable only through tightly-scoped security
groups.

---

## 3. Service-by-service mapping (Compose → AWS)

| Local (`docker-compose.yml`) | AWS | Why |
|---|---|---|
| `spring-boot` (host) | **ECS Fargate service** behind an **ALB** | stateless → N tasks; ALB does TLS + health checks + round-robin |
| `ai-service` (FastAPI) | **ECS Fargate service**, private | stateless; only Spring Boot calls it (via Cloud Map) |
| `postgres` | **RDS for PostgreSQL** (Multi-AZ) | never self-run a prod DB: managed backups, failover, patching, encryption |
| `ollama` | **pluggable** — managed API *or* GPU compute | see §6, the central decision |
| ChromaDB store | **EFS volume** on the FastAPI task *or* **pgvector on RDS** | see §7 |
| `pgadmin` | **dropped** | use an SSM Session-Manager tunnel to RDS when you need a console; never expose a DB GUI publicly |
| (images) | **ECR** | private registry the ECS tasks pull from |
| `.env` files | **Secrets Manager / SSM Parameter Store** | see §8 |
| Flutter emulator | **app stores / TestFlight** | the client isn't "deployed" server-side; CI builds it (§9) |

**Fargate, specifically:** Fargate is "serverless containers" — you hand
AWS a task definition (image + CPU/memory + env + secrets) and it runs the
container without you managing any EC2 hosts. For two small stateless
services that's exactly the right amount of abstraction.

---

## 4. Why ECS Fargate (and when you'd pick EKS instead)

| Option | Verdict for MathExp |
|---|---|
| **ECS Fargate** ✅ | No control plane to manage, no nodes to patch, pay per task-second. Two services + a load balancer is *well* within ECS's comfort zone. Right default. |
| **EKS (Kubernetes)** | More powerful, more portable (matches the Phase 12 microservices ambitions), but you inherit cluster ops, an EKS control-plane bill (~$73/mo) and a steeper learning curve. Overkill until there are many services. |
| **EC2 + Docker by hand** | Cheapest in raw compute, most expensive in toil (you patch the OS, you wire systemd, you build your own rollout). Not worth it. |
| **App Runner / Elastic Beanstalk** | Even simpler than Fargate, but less control over networking/private service-to-service. Fine for the API alone; awkward once you need private Cloud Map wiring. |

The honest framing: **start on Fargate; the day Phase 12 splits the
backend into many services, re-evaluate EKS.** The container images don't
change between the two — only the orchestration around them does.

---

## 5. The Flutter client — not a server, but it has a deploy story

The mobile app isn't deployed to AWS at all; it's compiled and distributed
through the app stores. But it has two cloud touchpoints:

1. **Its backend URL.** The app already reads it at build time:
   `--dart-define=API_BASE_URL=https://api.mathexp.app`. So the prod build
   simply points at the ALB's HTTPS domain. No code change — the design
   anticipated this (Phase: frontend, the `ApiClient.baseUrl`).
2. **Distribution.** CI builds the signed APK/IPA; release goes to Google
   Play / TestFlight (manually or via a tool like fastlane). HTTPS is
   mandatory (both stores reject cleartext), which the ACM cert on the ALB
   provides for free.

---

## 6. The LLM tier — the decision that defines this phase

This is the single hardest box to put in the cloud, and MathExp was built
to make it a *configuration* choice rather than a code change: the
`AIEngineAdapter` factory keys off the `AI_MODE` env var (Phase: AI
service). So the design keeps **both paths live and recommends a default.**

### Path A — Managed LLM API  *(recommended default for cloud)*
Set `AI_MODE=cloud` and point `CLOUD_API_URI` / `CLOUD_API_KEY` at a hosted
OpenAI-compatible endpoint — **Amazon Bedrock** (most native to AWS),
or OpenAI / Together / Groq / Anthropic.

| | |
|---|---|
| ✅ Pros | No GPU to provision, patch, or pay for idle. Scales elastically (it's someone else's problem). Fast first-token. The cheapest way to *start*. |
| ⚠️ Cons | Per-token cost that grows with usage. Data leaves your VPC (a privacy/compliance consideration for student data). An external dependency in your critical path. |

Recommended because it **deletes the entire GPU-ops burden**, and because
MathExp's graceful-degradation design (below) means the per-request risk is
bounded.

### Path B — Self-hosted Ollama on AWS
Set `AI_MODE=local` and run Ollama on GPU compute — an ECS service or EKS
node group on `g4dn`/`g5` instances, with the model weights on an EBS/EFS
volume.

| | |
|---|---|
| ✅ Pros | Fully private — no student data leaves your infra. Zero per-token cost. Total control over the model. |
| ⚠️ Cons | A `g4dn.xlarge` is ~**$380/mo on-demand even when idle** (spot is cheaper but interruptible). GPUs don't "scale to zero" gracefully — cold-starting a model on a new instance is slow. You own the patching, the autoscaling, the model storage. This single box can dominate the entire bill (§10). A CPU-only fallback exists but a 3B model on CPU is 20–40 s/response — within the backend's 60 s `RestTemplate` read-timeout, but a poor UX. |

### Why "both" is the right answer
- `AI_MODE` flips between A and B with **zero code change** — the adapter
  pattern was designed for exactly this portability.
- **Graceful degradation backs it up:** even if the LLM tier is entirely
  down, `/check-answer` (SymPy) still grades every answer, and
  `/generate-exercises` still falls back to the deterministic templates.
  The app stays *useful* without any LLM — so the LLM tier can be the
  cheap managed option without putting the core experience at risk.
- **Recommendation:** launch on **Path A (Bedrock)**. Keep Path B
  documented and one env-var away, to be switched on only if a data-privacy
  requirement forces inference back in-house.

---

## 7. The vector store (ChromaDB)

ChromaDB persists embeddings to a `chroma_store/` directory — which a
Fargate task loses on every restart. Two ways to fix that:

| Option | How | Trade-off |
|---|---|---|
| **EFS-backed Chroma** *(minimal change)* | Mount an **EFS** filesystem into the FastAPI task at the `chroma_store` path; run `python -m rag.ingest` as a one-off ECS task after each deploy. | No code change. EFS is a shared, durable NFS volume that survives task churn. Slight latency vs. local disk — irrelevant for a 10-chunk corpus. |
| **pgvector on RDS** *(consolidation play)* | Drop ChromaDB; store embeddings in the RDS Postgres you already run, via the `pgvector` extension. | Fewer moving parts (one stateful store instead of two), one backup story. **Requires a code change** — swap the Chroma client in `rag/retriever.py`/`ingest.py` for a pgvector query. |

**Recommendation:** EFS-backed Chroma for the first cloud cut (zero code
change), and note pgvector as the cleaner long-term consolidation once the
team is comfortable changing the retrieval layer. The corpus is tiny, so
this is a *persistence + where-it-lives* decision, never a scaling one.

---

## 8. Secrets & configuration

Every value that lives in `.env` today maps to a managed secret/parameter,
injected into the ECS task definitions at launch (never baked into images,
never in git — same discipline as the existing `.gitignore`d `.env`).

| Local env var | AWS home | Notes |
|---|---|---|
| `JWT_SECRET` | **Secrets Manager** | the one that lets an attacker forge any user's token — top priority |
| `DB_USER` / `DB_PASSWORD` / `DB_NAME` | **Secrets Manager** (RDS-managed secret) | RDS can own + rotate this automatically |
| `AI_SERVICE_URL` | **Cloud Map** internal DNS | replaces the Compose hostname `ai-service`; e.g. `http://ai.mathexp.internal:8000` |
| `AI_MODE` / `MODEL_URL` / `MODEL_NAME` | SSM Parameter Store | non-secret config |
| `CLOUD_API_KEY` / `CLOUD_API_URI` | **Secrets Manager** | only when `AI_MODE=cloud` |
| `API_BASE_URL` (Flutter) | build-time `--dart-define` | the ALB domain |

**Service discovery** deserves a sentence: in Compose, Spring Boot finds
FastAPI by the service name `ai-service` on the Docker network. In AWS that
becomes **ECS Service Connect / Cloud Map**, which gives each service a
stable internal DNS name inside the VPC — so the only thing that changes is
the value of `ai-service.url`.

---

## 9. From CI to CD — extending the Phase 10 pipeline

Phase 10 left three green workflows (`backend`, `ai-service`, `frontend`).
CD adds a *deploy* workflow that runs **after** they pass:

```
merge to release/main
   └─ (Phase 10 CI: build + test, must be green)
        └─ deploy job:
             1. configure AWS creds via OIDC   (no static keys — see below)
             2. docker build  backend + ai-service
             3. docker push   → ECR (tagged with the git SHA)
             4. aws ecs update-service --force-new-deployment   (rolling)
             5. run the one-off ingest task (refresh the vector store)
```

Three things worth calling out, two of which Phase 10 explicitly foreshadowed:

- **OIDC, not static keys.** GitHub Actions federates into an AWS IAM role
  via OIDC and mints short-lived credentials per job — so there's no
  `AWS_SECRET_ACCESS_KEY` sitting in repo secrets to leak. (Phase 10 §7
  flagged this as the Phase 11 approach.)
- **Image build + push** is the job Phase 10 §12 deliberately deferred to
  "where it has a logical home" — here.
- **Spring Boot needs a Dockerfile.** Today it runs on the host via
  `./mvnw`; the FastAPI service already has one. Adding a multi-stage
  Dockerfile for the backend is the single biggest *code* (well, config)
  prerequisite for this whole phase.

**Database migrations:** the app currently runs `spring.jpa.hibernate.ddl-auto=update`,
which is fine for dev but unsafe for prod (it can't do destructive or
ordered changes predictably). The hardening step is to switch to **Flyway**
or **Liquibase** versioned migrations run on deploy. Noted, not done.

**Rollouts & rollback:** ECS does rolling deploys behind the ALB's health
checks (new tasks must pass health checks before old ones drain), and a bad
deploy rolls back by re-deploying the previous image tag — which is exactly
what the AI service's `RollbackTag.md` procedure already describes, now
mechanized.

---

## 10. Cost sketch (rough, monthly, us-east-1)

Orders of magnitude, not a quote — the point is *what dominates*.

| Item | Path A (managed LLM) | Path B (self-hosted GPU) |
|---|---|---|
| ALB | ~$16 | ~$16 |
| Fargate — api + ai (2 tasks each, small) | ~$40–70 | ~$40–70 |
| RDS `db.t4g.micro` Multi-AZ | ~$30 | ~$30 |
| EFS (vector store) | a few $ | a few $ |
| NAT Gateway (private-subnet egress) | ~$32 | ~$32 |
| **LLM** | **per-token usage** (small at low traffic) | **~$380 (g4dn.xlarge on-demand), idle or not** |
| **Ballpark** | **~$120–150 + usage** | **~$500+** |

The lesson is stark and it's the whole reason §6 recommends Path A: **a
single always-on GPU is larger than the entire rest of the stack
combined.** Spot instances and scale-to-zero help, but the moment you need
*reliable, low-latency* self-hosted inference, the GPU is the dominant line
item. Managed inference converts that fixed cost into a usage-proportional
one — ideal for a project that's mostly idle.

---

## 11. What stays the same (the architecture anticipated the cloud)

The most reassuring part of this design is how *little* of the application
changes. That's not luck — earlier phases made cloud-shaped decisions:

| Decision (earlier phase) | Why it pays off now |
|---|---|
| **Stateless JWT auth** | API tiers scale to N instances with zero session/sticky-routing work |
| **The `AI_MODE` adapter** | the hardest cloud problem (the LLM) is a config flip, not a rewrite |
| **Everything reads from env vars** | maps 1:1 onto Secrets Manager / SSM; no hardcoded hosts |
| **Decoupled services over HTTP** | each becomes its own ECS service + Cloud Map name with no contract change |
| **Graceful degradation everywhere** | lets us pick the *cheap* managed LLM without risking the core UX |
| **UUID PKs + soft deletes** | safe to run across replicas; no merge/enumeration surprises |

The actual code/config deltas to go live are small and enumerable:
1. a Dockerfile for Spring Boot,
2. swap the Compose service name for the Cloud Map endpoint (one env var),
3. mount EFS for the vector store (or migrate to pgvector),
4. Flyway migrations (hardening),
5. the deploy workflow.

---

## 12. Hardening checklist (open items, deliberately not "done")

These are the gap between "works" and "production-grade." Listed so they're
visible, not because Phase 11 implements them:

- [ ] **CORS** — currently `*` (Phase: security). Restrict to the app's origin / none (it's a native client).
- [ ] **Flyway/Liquibase** migrations instead of `ddl-auto=update`.
- [ ] **WAF** on the ALB (rate-limiting, basic OWASP rules).
- [ ] **ECR image scanning** + Trivy/CodeQL in CI (Phase 10 §12 noted this).
- [ ] **RDS encryption at rest** + automated snapshot retention + a tested restore.
- [ ] **Least-privilege IAM** task roles (each service gets only the secrets it needs).
- [ ] **VPC Flow Logs** + CloudWatch alarms (5xx rate, task health, RDS CPU).
- [ ] **Secret rotation** for `JWT_SECRET` (with a rotation-tolerant verification window).
- [ ] **Token refresh** — the 24 h JWT has no refresh flow ("Auth v2").

---

## 13. Why this is Phase 11 (and what's next)

Phase 10 automated *verification* (CI). Phase 11 designs *delivery* (CD) and
the runtime it delivers to. The ordering matters: you don't design a
deployment pipeline until the thing being deployed is provably good, and you
don't write IaC until you've decided the architecture it encodes — which is
exactly what this document is for.

The roadmap from here:
- **Phase 11b** (implementation) — turn this design into Terraform + the
  Spring Boot Dockerfile + the deploy workflow.
- **Phase 12** (microservices split) — once there are many services,
  re-evaluate EKS over Fargate; this VPC/ALB/Cloud Map skeleton is the
  foundation it grows on.
- **Phase 13** (Kafka event backbone) — the in-process `@Async`
  exercise-generation listener (Phase: backend) becomes a real queue;
  in the cloud that's **MSK** or **SQS/EventBridge**, and the producer/worker
  code is already structured so only the glue moves.

Without Phase 11's decisions written down, 11b would be a pile of `.tf`
files encoding choices nobody made on purpose. With them, the implementation
is mechanical — which is the whole point of designing before building.
