# AI Service ChangeLog

Chronological record of substantive changes to the FastAPI/AI service. Each entry pairs with a git tag (see `RollbackTag.md`) so a deploy can be rolled back to any previous version with a single checkout.

---

## Phase 11 — RAG cherry-pick (2026-05-30)

**Branch:** `feat/rag-cherry-pick` (forked from `release/0.0.1`)
**Tag:** `v0.1.0-rag`

**Added**
- Modular layout: `adapters/`, `models/`, `evaluation/`, `rag/`.
- `rag/` infrastructure: ChromaDB persistent vector store + sentence-transformers embedding model (paraphrase-multilingual-MiniLM-L12-v2). Lazy singleton, graceful degradation to `[]` on any failure.
- `rag/ingest.py` script: paragraph-aware chunking, md5 hash-based idempotency, metadata filter by knowledge node code.
- 8-node corpus under `corpus/`: one file per node in the canonical taxonomy (ARITH_*, FRACTIONS_*, ALGEBRA_*).
- `adapters/ai_adapters.py` with ABC enforcement, fail-fast env validation, strict `AI_MODE` check.
- `evaluation/diagnostician.py` — structured prompt with RAG excerpt injection, hallucinated-code fallback with WARN log.
- `/health` endpoint for Spring Boot startup probing.
- `models/schemas.py` — single source of truth for `VALID_WEAKNESS_NODES`.

**Changed**
- `/evaluate-error` internal pipeline now: verification → RAG retrieval → structured prompt → LLM → post-validation. External wire format (`{weakness_node, explanation}`) is preserved verbatim so Spring Boot's `AiFeedbackDto` requires no change.
- Inline adapter classes moved out of `main.py`. The public method name `evaluate_student_error(prompt)` is preserved so `_try_llm_generation` (Phase 8) continues to work unchanged.
- `.env.example` now documents each variable; `MODEL_URL` is the base URL (the adapter appends `/api/generate`).

**Preserved**
- `/check-answer` (Phase 1) — unchanged.
- `/generate-exercises` (Phase 8) — unchanged.
- `exercise_templates.py` deterministic fallback — unchanged.
- All Phase 9 tests — unchanged.
- 8-node taxonomy mirroring `DataSeeder.java`.

**Dependencies added**
- `chromadb==0.5.23`
- `sentence-transformers==3.3.1` (pulls torch ~2 GB on first install)

**Ingest the corpus before first run:**
```bash
cd ai-python
python -m rag.ingest
```

---

## Phase 10 — CI/CD baseline (2026-05-27)

GitHub Actions for backend (Java 21 / Maven / Testcontainers), AI service (Python / pytest), and frontend (Flutter analyze + test). Dependabot configured for all three layers. README status badges added.

## Phase 9 — Test suite split (2026-05-22)

Unit/integration split across the three layers. Spring Boot Failsafe wired for integration tests. Phase 9 placeholder smoke test removed.

## Phase 8 — Exercise generation (2026-05-15)

Two-tier generation: LLM produces candidates, SymPy verifies each, deterministic templates as fallback when 0 LLM candidates pass. Async event publishing on the Spring Boot side (`ExercisePoolLowEvent`).

## Phase 7 — Step-by-step diagnostic prompt (2026-05-08)

`/evaluate-error` now reasons about which step in the student's mental computation went wrong; weakness codes attribute to prerequisite skills (e.g. `ARITH_DIVISION` for a slip inside a linear equation) rather than the topic itself.

## Phase 1 — SymPy deterministic check (2026-04-25)

`/check-answer` endpoint added — fast (sub-50ms) symbolic equivalence check. Routed before the LLM diagnosis so correct answers skip the LLM entirely.
