# Rollback Procedure — AI Service

Every release of the AI service is tagged. If a new version misbehaves in production, you can roll back to any previous tag with a single deployment.

## Tag convention

Tags follow the form `v<major>.<minor>.<patch>-<label>`:

| Tag | Branch | What it captures |
|---|---|---|
| `v0.1.0-rag` | `feat/rag-cherry-pick` | Phase 11 RAG cherry-pick (this release) |
| `v0.0.1` | `release/0.0.1` | Phases 1-10 stable baseline |

## Rolling back

### Local development

```bash
git fetch --tags
git checkout v0.0.1            # or any other tag
cd ai-python
pip install -r requirements.txt
# (re-ingest corpus if you're on a tag that has rag/)
python -m rag.ingest
uvicorn main:app --reload
```

### Docker

```bash
docker build -t mathwise-ai:v0.0.1 \
  --build-arg GIT_REF=v0.0.1 \
  ai-python/
docker run --env-file ai-python/.env -p 8000:8000 mathwise-ai:v0.0.1
```

### Production (when a deploy is in place)

Re-deploy the previous tag with whatever orchestrator you're using. The Spring Boot side calls `/check-answer`, `/evaluate-error`, and `/generate-exercises` — all three have been preserved across every tagged release, so rolling the AI service back does NOT require a coordinated Spring Boot rollback.

## Tagging a new release

Once a change is verified end-to-end:

```bash
git tag -a v<new-version> -m "Phase N — short description"
git push origin v<new-version>
```

Then update `ChangeLog.md` with the new entry.

## What to check after rollback

1. `/health` returns `{"status": "ok"}`.
2. `/check-answer` round-trips `1/2` ⇔ `0.5`.
3. `/generate-exercises` returns at least one verified exercise for `ARITH_ADDITION` (the deterministic fallback should always produce content, even with Ollama down).
4. `/evaluate-error` returns the legacy shape `{weakness_node, explanation}` so `AiFeedbackDto` deserializes without errors.

If step 4 fails after rolling forward to `v0.1.0-rag` or later, the corpus may not be ingested. Run `python -m rag.ingest` and retry — the diagnostician degrades gracefully when the corpus is empty, so the explanation will still be produced (just without RAG grounding).
