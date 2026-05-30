# Phase 10 — CI/CD (Continuous Integration with GitHub Actions)

**Branch:** `release/0.0.1`
**Status:** Shipped (CI). CD intentionally deferred to Phase 11 as a
theoretical learning phase.

This phase is structured like Phase 9 — it teaches the concepts and
documents the implementation alongside. Treat it as your reference manual
for how CI works on this project and how to extend it.

---

## 1. Why CI matters (in two paragraphs)

Without CI, the only thing standing between a broken build and the rest
of the team is the individual developer's discipline — "did you remember
to run the tests?" That works for a solo project at the start. It does
not survive contact with two contributors, a Friday afternoon, or a
multi-day feature branch.

CI moves the question from "did the developer remember?" to "did the
machine confirm?". Every push, every PR, the same exact suite runs in
the same exact environment, and the result is visible on the PR page
as a green check or a red cross. Reviewers stop wasting cycles
mentally simulating "would the tests pass?" — they can see the answer
above the merge button.

---

## 2. CI vs CD vs CI/CD

Three terms, often conflated. Worth disentangling once:

| Term | What it means | Phase 10? |
|---|---|---|
| **Continuous Integration (CI)** | Every change is automatically built and tested. | ✅ This phase. |
| **Continuous Delivery (CD)** | Every green CI run produces a deployable artefact. Production deploy is manual. | Phase 11 (theoretical). |
| **Continuous Deployment (CD)** | Same as above, but production deploys also automatic. | Out of scope. |

"CI/CD" is just the umbrella term for the pipeline that goes
code-change → tested → built → released. We're doing the first half here.

---

## 3. The mental model of a GitHub Actions workflow

A **workflow** is a YAML file in `.github/workflows/`. The structure:

```yaml
name: <name visible in the Actions UI>

on:                  # ── WHEN does this run?
  <event>:
    <filters>

concurrency:         # ── optional, cancels stale runs
  group: <key>
  cancel-in-progress: true

jobs:                # ── WHAT runs
  <job_id>:
    runs-on: <runner image>
    permissions:     # ── token scopes (defense in depth)
      <scope>: <level>
    steps:
      - name: <step>
        uses: <reusable-action>     # third-party building blocks
      - name: <step>
        run: <shell command>        # arbitrary shell
```

### Events that can trigger a workflow

| Event | When it fires | We use it? |
|---|---|---|
| `push` | Commit pushed to specified branches | ✅ |
| `pull_request` | PR opened / updated against specified branches | ✅ |
| `schedule` | Cron — e.g. nightly builds | No |
| `workflow_dispatch` | Manual button in the GHA UI | No (could add for ops) |
| `release` | When a release is published | Phase 11 candidate |
| `workflow_run` | When another workflow finishes | Phase 11 candidate |
| `repository_dispatch` | External webhook from anywhere | No |

### Jobs vs Steps

A **job** runs on its own runner (a fresh VM). Steps inside a job run
sequentially, sharing the runner's filesystem. Multiple jobs can run
in parallel by default (use `needs:` to serialise).

We have one job per workflow because there's nothing to parallelise yet
— our slow step (the tests themselves) can't be split without
fragmenting the suite. If we add a "build Docker image" job in Phase 11,
it would `needs: test` to run only after tests pass.

### Runners

`ubuntu-latest` is the default GitHub-hosted VM. It comes with Docker,
Git, OpenJDK 11/17/21, Python 3.x, Node, Go, Rust, and many more
preinstalled. The full inventory of what's available is in the
[runner-images repo](https://github.com/actions/runner-images).

You can also run jobs on `macos-latest`, `windows-latest`, or even
self-hosted runners (your own machine, as a GitHub-registered worker).
For this project, `ubuntu-latest` is right for all three workflows
because none of them need a Mac or Windows toolchain.

---

## 4. Path filters — the single most-impactful optimisation

Without path filters, every workflow runs on every push. A Flutter-only
PR triggers the backend CI, wasting 3 minutes pulling the Maven cache
to find out... nothing changed in Java code.

With `paths:` in the `on:` block, the workflow runs only when matching
files changed:

```yaml
on:
  push:
    paths:
      - 'backend-springboot/**'
      - '.github/workflows/backend-ci.yml'
```

Always include the workflow's own filename in its own `paths:` list.
Otherwise, editing the workflow's YAML doesn't trigger it (you can't
test changes to the workflow without going through hoops).

**The corner case to know about**: if a PR touches only `docs/**`, none
of the three workflows will trigger. That means the PR has no required
status checks to wait for, so it can be merged immediately. For a
personal project that's fine — docs PRs don't need CI. For a large
team, you'd add a tiny "always-pass" workflow that runs on every PR
and serves as the universal merge gate.

---

## 5. Caching — what makes runs fast

Dependency downloads dominate CI time. A cold Maven build pulls 200
JARs, ~30s. A cold pip install pulls a hundred wheels, ~20s. A cold
Flutter SDK download is ~60s.

GitHub Actions provides a per-repo cache (10 GB total, evicted by LRU).
Most language-setup actions hook into it automatically:

| Workflow | Cache option | What's cached |
|---|---|---|
| `setup-java` | `cache: maven` | `~/.m2/repository` |
| `setup-python` | `cache: pip` + `cache-dependency-path` | pip wheel cache |
| `subosito/flutter-action` | `cache: true` | Flutter SDK + pub cache |

The cache key is derived from the lockfile (`pom.xml`, `requirements.txt`,
`pubspec.lock`). When the lockfile changes, the cache key changes,
and the cache is rebuilt automatically.

**Worth noting**: cache misses on the first run are expected. Don't
panic when your first push of the workflow takes a few minutes — the
second run will be ~10× faster.

---

## 6. Concurrency control

The `concurrency:` block in each workflow:

```yaml
concurrency:
  group: backend-ci-${{ github.ref }}
  cancel-in-progress: true
```

If you push 3 commits in 30 seconds, this cancels the first 2 runs as
soon as the next one starts. Net effect: you only ever wait for the
**latest** commit's CI, and you don't burn 3× the runner minutes.

Each workflow has its own group (`backend-ci-<branch>`,
`ai-service-ci-<branch>`, etc.) so cancelling backend-ci doesn't kill
an unrelated frontend-ci run on the same branch.

---

## 7. Secrets — what they are and what's NOT used in Phase 10

Secrets are encrypted key-value pairs scoped to the repository (or org).
You set them in **Settings → Secrets and variables → Actions**. They
appear in the runner as `${{ secrets.<NAME> }}` and are masked in logs.

**Phase 10 needs zero secrets**, deliberately. Building and testing
doesn't require any credentials. Secrets become necessary in Phase 11:

| Phase 11 secret (when we get there) | Used for |
|---|---|
| `GHCR_TOKEN` or just `GITHUB_TOKEN` | Pushing Docker images to GitHub Container Registry |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (or OIDC) | Terraform plan/apply |
| `SOPS_AGE_KEY` | Decrypting secrets in the infra repo |

Rule of thumb: if a secret would let an attacker do real damage, prefer
**OIDC federation** over long-lived static keys. GitHub Actions can
mint short-lived AWS/GCP credentials per-job without ever storing the
keys. Worth knowing when Phase 11 lands.

---

## 8. Branch protection — the human gate

Workflows running green is only half the story. The other half is
**making green a requirement for merge**. That setting lives in the
GitHub web UI, not in code:

> **Settings → Branches → Add branch protection rule**
>
> Branch name pattern: `main`
>
> Tick:
>   - ☑ Require a pull request before merging
>   - ☑ Require status checks to pass before merging
>     - Search for and select: `Test (JUnit + Testcontainers)`,
>       `pytest`, `Analyze + test`
>   - ☑ Require branches to be up to date before merging *(optional, prevents merge-time conflicts)*
>   - ☑ Do not allow bypassing the above settings *(or you'll forget you set them)*

Phase 10 doesn't enforce branch protection from code (it can't) — but
the workflows above expose the three required checks. Configure
protection once and the gate is permanent.

---

## 9. The three workflows we have

### `backend-ci.yml`

| Concept | Where you see it |
|---|---|
| Event trigger with path filter | `on:` block, `paths:` lists `backend-springboot/**` |
| Concurrency cancellation | `concurrency:` block |
| Permissions hardening | `permissions: contents: read` |
| Default working dir | `defaults.run.working-directory` |
| Maven caching | `setup-java` step with `cache: maven` |
| Real Postgres in CI | `./mvnw verify` runs Failsafe + Testcontainers; GHA runners have Docker pre-installed |
| Test report artefacts | `actions/upload-artifact@v4` with `if: always()` |

### `ai-service-ci.yml`

Same shape, narrower scope. Tests pure Python logic (SymPy answer
check, deterministic exercise templates) without ever talking to an
LLM — the LLM is mocked at call sites in the test files.

### `frontend-ci.yml`

Three steps: `pub get` → `analyze` → `test`. The `subosito/flutter-action`
handles SDK installation + caching. `flutter analyze` is non-negotiable
(it's our linter, our type checker, and our dead-code finder rolled
into one).

---

## 10. Dependabot

`.github/dependabot.yml` configures automatic dependency-update PRs.
Four ecosystems are watched:

- `maven` (backend)
- `pip` (ai-python)
- `pub` (frontend)
- `github-actions` (the workflow YAML files themselves)

Each PR runs through the relevant CI workflow above, so a breaking
update fails its own PR and you simply close it. Minor/patch updates
are grouped to reduce PR noise; major bumps come in their own PR
because they deserve more scrutiny.

---

## 11. How to read a failed run

1. Open the **Actions** tab on GitHub.
2. Click the failed run.
3. Click the failed job.
4. The failing step has a red ✗ — expand it. The last 50 lines of output
   usually contain the cause.
5. If the failure is environmental (cache corruption, runner flake),
   click **Re-run failed jobs** in the top right.
6. If the failure is real, reproduce locally with the same command the
   workflow ran (every step shows the exact shell command in its
   header).

### Common failure modes you'll see

| Symptom | Usually means |
|---|---|
| `Test failed: ...` | Real test failure — read the test name and stack trace |
| `Caused by: ... could not be resolved` | Dependency unavailable. Maven Central was flaky, or a typo in pom.xml |
| `Connection refused: localhost:5432` | Test tried to talk to a real DB it didn't start. Check that the test uses Testcontainers |
| `ERROR: failed to solve: ...` | Docker build failure — usually a Dockerfile issue |
| Cache miss every time | `cache-dependency-path` doesn't point at the right lockfile |

---

## 12. What's NOT in Phase 10 (deliberately)

- **Docker image build + push** — deferred to Phase 11 where it has a
  logical home alongside the (theoretical) deployment design.
- **Code coverage reporting** — would integrate with Codecov / Coveralls.
  Useful but adds an external account dependency.
- **Security scanning beyond Dependabot** — GitHub CodeQL, Trivy
  image scanning. Worth adding once Phase 11 has images to scan.
- **Automatic semver tagging / releases** — would use a release-please
  or semantic-release action. Useful if there's a downstream consumer.
- **PR title / commit message linting** — useful at team scale, overkill
  for a single dev.
- **Meta workflow that aggregates all three into one required check** —
  unnecessary for 3 workflows; required-status-check on each is fine.

---

## 13. How to use the suite (cheat sheet)

```bash
# After cloning, run the same things locally to mirror CI:

# Backend — unit tests only (no Docker)
cd backend-springboot/math-wise-backend && ./mvnw test

# Backend — full CI parity (needs Docker for Testcontainers)
cd backend-springboot/math-wise-backend && ./mvnw verify

# AI service
cd ai-python && pip install -r requirements-dev.txt && pytest -v

# Frontend
cd frontend-flutter && flutter analyze && flutter test
```

When you open a PR, the three GHA workflows kick off automatically.
Watch them in the **Actions** tab or right at the bottom of the PR
page. Green ✓ to merge, red ✗ to fix.

---

## 14. Why this was Phase 10

Phase 9 set up tests. Phase 10 automates them. The pattern
"changes → tests run automatically → results visible on the PR" is the
foundation everything else in the architectural roadmap rests on:

- **Phase 11** (theoretical CD) needs CI to prove the image is good
  before "shipping" it.
- **Phase 12** (microservices split) is a refactor — refactors without
  CI are leaps of faith.
- **Phase 13** (Kafka event backbone) introduces integration points
  that benefit massively from automated test coverage.

Without Phase 10, every later phase is a coin flip. With it, each is
a verifiable step forward.
