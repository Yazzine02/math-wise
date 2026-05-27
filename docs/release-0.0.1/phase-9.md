# Phase 9 — Testing

**Branch:** `release/0.0.1`
**Status:** Shipped

This phase is structured as a teaching reference. It introduces testing
concepts, shows the patterns we use at each layer, and points at the
concrete test files we wrote so you can read them as worked examples.

---

## 1. Why test

Testing isn't really about catching bugs (it does, but that's a side
effect). The deeper reasons:

1. **Tests are executable documentation.** A passing test for "registration
   with a malformed email returns 400 with field errors" is a clearer
   statement of behaviour than any paragraph in a doc — and it can't
   silently rot.

2. **Tests shape design.** Code that's painful to test is usually code
   that has hidden dependencies or unclear responsibilities. The test
   forces you to surface those before they harden.

3. **Tests give you the confidence to refactor.** You'll spend years
   maintaining a codebase. Without tests, every change is a leap of
   faith. With tests, you can rip out the adaptive engine and rebuild
   it knowing the behaviour stays the same.

A junior developer's instinct is "I'll add tests later." A senior
developer's instinct is "I'll add the test now because that's how I
know I've understood the requirement."

---

## 2. Concepts you must internalise

### The Arrange-Act-Assert pattern

Every test, in every language, has three sections:

```
Arrange:  set up the world (build fixtures, stub mocks)
Act:      call the thing being tested
Assert:   verify the outcome
```

If a test doesn't follow this shape, the test is doing too much. Split
it. The simpler the test, the more honest the failure message when it
breaks three months from now.

### The test pyramid

```
                    ▲
                   /e2e\          ← few, slow, brittle, high-value
                  /─────\
                 / inte- \         ← some, medium speed, real DB
                / gration \
               /───────────\
              /    unit     \      ← many, milliseconds, pure logic
             ─────────────────
```

- **Unit tests** test one class or function with everything else mocked.
  Run in milliseconds. You write hundreds.
- **Integration tests** test a slice of the system with a real database
  or HTTP layer (via Testcontainers / MockMvc). Seconds each. You write
  dozens.
- **End-to-end tests** drive the whole stack: real browser, real backend,
  real DB. Slow, flaky, expensive. You write a handful for the most
  important user journeys.

**Always prefer pushing tests down the pyramid.** A bug that can be
caught by a unit test should be — not by an integration test, not by
a manual click-through.

### Mocks, stubs, real

When testing class A which depends on B and C:

- **Mock B and C** → you test A's logic in isolation. Use Mockito (Java)
  or `unittest.mock` / monkeypatching (Python). Fast.
- **Use real B and C** → you test A's integration with them. Slower but
  more realistic.
- The middle ground (Testcontainers) gives you a real Postgres in a
  Docker container — only for tests. Slow to start (~5s), then fast per
  test. This is the sweet spot for repository / integration tests.

### Anti-patterns to avoid

- **Testing implementation details.** If renaming a private method
  breaks tests, the tests are too coupled. Test outputs, not internal
  steps.
- **Hidden test ordering.** Each test should run in isolation. If test B
  depends on test A having written a row, that row should be in B's
  Arrange section (or a shared `@BeforeEach`/`setUp`).
- **Slow unit tests.** A unit test that takes more than ~100ms is
  probably actually an integration test. Either accept that and move it,
  or find what's making it slow (real I/O? un-mocked dependency?).
- **Tests that don't fail.** If you change the production code and the
  test passes when it shouldn't, the test is verifying nothing.
  Periodically break the production code on purpose to confirm the
  test fails.

---

## 3. What we tested in Phase 9

A deliberately small, representative slice. Phase 9 establishes the
*patterns* — Phase 10 and beyond can build coverage on top of these.

| Layer | File | Type | What it covers |
|---|---|---|---|
| Spring Boot | `KnowledgeNodeResolverTest.java` | Unit (Mockito) | Single-class logic with one mocked collaborator |
| Spring Boot | `StudentProgressServiceTest.java` | Unit (Mockito) | Multi-dependency business logic: mastery, prereq descent, cold-start |
| Spring Boot | `AuthControllerIT.java` | Integration (Testcontainers + MockMvc) | Real Postgres + full Spring context. Happy path + Phase 5 validation + Phase 5 auth error envelope |
| FastAPI | `tests/test_answer_check.py` | Unit (pytest) | SymPy equivalence + string fallback |
| FastAPI | `tests/test_exercise_templates.py` | Unit (pytest) | Each deterministic generator runs N=25 times, output verified by SymPy as an independent oracle |
| Flutter | `test/errors/api_exception_test.dart` | Unit (flutter_test) | All branches of `ApiException.fromResponse` |

---

## 4. Per-layer setup

### Spring Boot

Test starters were already in `pom.xml` from the parent. Phase 9 added:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

These give us `@Testcontainers`, the Postgres module, and JUnit 5
integration. `@ServiceConnection` (Spring Boot 3.1+) auto-wires the
container into Spring's datasource config — no manual
`application-test.properties` needed.

### FastAPI

A new `ai-python/requirements-dev.txt` pulls pytest, `pytest-asyncio`,
and httpx:

```bash
cd ai-python
pip install -r requirements-dev.txt
```

Tests live in `ai-python/tests/`. Each `test_*.py` file is discovered
automatically by pytest.

### Flutter

`flutter_test` was already in `pubspec.yaml` (it ships with Flutter).
Tests live under `frontend-flutter/test/`, mirroring the directory
structure of `lib/`. No extra config needed.

---

## 5. Running the suites

The Spring Boot side is split via Maven naming conventions:

| Phase | Picks up | Runner |
|---|---|---|
| `mvn test` (every build) | `*Test.java` — fast unit tests, no Docker, no DB | Surefire |
| `mvn verify` (full check) | `*Test.java` + `*IT.java` — integration tests that need Docker | Surefire + Failsafe |

This means contributors can run unit tests cheaply on every change
without Docker installed; the integration tests run on CI or when
explicitly asking for `verify`.

```bash
# Spring Boot — unit tests only (~1s, no Docker needed)
cd backend-springboot/math-wise-backend
./mvnw test

# Spring Boot — unit + integration (~30s first time, needs Docker running)
./mvnw verify

# FastAPI
cd ai-python
pip install -r requirements-dev.txt
pytest

# Flutter
cd frontend-flutter
flutter test
```

The first `mvn verify` takes ~30s because Testcontainers pulls the
`postgres:16` image. Subsequent runs reuse the cached image.

### Why no placeholder smoke test

Spring Initializr generated a `MathWiseBackendApplicationTests.contextLoads`
with an empty body. Its purpose was a "does the context load?"
smoke test — but once the project actually has a database, "loading"
requires either a real DB or Testcontainers, and forcing Docker into
the unit-test path defeats the speed of `mvn test`. The same coverage
is provided by `AuthControllerIT` (the canonical integration test
that proves the full context boots against a real Postgres), so the
placeholder was deleted. Don't keep generated placeholders that test
nothing — they create noise and false failures.

---

## 6. What's NOT covered (yet)

Honest list of things you'd want before calling this production-grade:

- **Flutter widget tests.** Rendering `LoginScreen` and asserting that
  `fieldErrors` produce pink borders on the right inputs. Different
  paradigm; deserves its own phase.
- **More integration tests.** AuthControllerIT is one example. The same
  pattern should cover `StudentController`, `AiEvaluationController`,
  `CourseController`. They follow the same shape — extend as needed.
- **Endpoint tests for FastAPI.** `/check-answer` and
  `/generate-exercises` end-to-end via `TestClient`. We have unit
  coverage of the helper functions; endpoint-level smoke tests would be
  a small extension.
- **Property-based fuzzing.** `hypothesis` for Python, `jqwik` for Java.
  Useful for generators where you want to assert invariants hold across
  the input space.
- **Mutation testing.** Tools like Pitest (Java) deliberately mutate
  production code and see if the tests catch it. The gold standard for
  proving your tests are actually doing something.

---

## 7. Patterns to reuse when you write more tests

### Spring Boot — unit test pattern

```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {

    @Mock SomeRepository repo;
    @InjectMocks SomeService service;

    @Test
    void describes_the_behaviour_being_verified() {
        // Arrange
        when(repo.findX(arg)).thenReturn(Optional.of(fixture));

        // Act
        Result result = service.doX(arg);

        // Assert
        assertThat(result).isEqualTo(expected);
        verify(repo).findX(arg);
    }
}
```

### Spring Boot — integration test pattern

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SomeControllerIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void resetDb() { /* cleanup */ }

    @Test
    void scenario_name() throws Exception {
        mockMvc.perform(post("/api/x").contentType(JSON).content(body))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.field").value(expected));
    }
}
```

### Pytest — parametrised test pattern

```python
@pytest.mark.parametrize("input,expected", [
    ("case1", "result1"),
    ("case2", "result2"),
])
def test_function_under_test(input, expected):
    assert function_under_test(input) == expected
```

### Flutter — unit test pattern

```dart
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('SomeClass', () {
    test('describes the behaviour', () {
      final result = SomeClass.doX(input);
      expect(result, expected);
    });
  });
}
```

---

## 8. Why this was Phase 9

Tests are the foundation for everything in the architectural roadmap.
Phase 10 (CI/CD) automates running these. Phase 11 (production
environment) deploys only artefacts that passed them. Phase 12
(microservices split) is *terrifying* without tests — and reasonable
with them. Phase 13 (Kafka) needs integration tests proving the event
flow works end-to-end. Phase 14 (observability) needs tests proving
metrics get emitted.

Without tests, every later phase is a leap of faith. With tests, each
becomes a deliberate, verifiable step.
