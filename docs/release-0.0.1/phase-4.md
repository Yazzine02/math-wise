# Phase 4 — Adaptive engine v2

**Branch:** `release/0.0.1`
**Status:** Shipped

---

## 1. The two problems

### 1.1 No recency: old mistakes drag forever

`StudentProgressService.getNextExercise` aggregated wrong answers via `InteractionLogRepository.findTopWeaknessesByStudentId`, which was an all-time `GROUP BY` over `interaction_logs`. The query joined every failure the student had ever recorded.

Concrete consequence: a student who failed Multiplication 8 times in February, drilled it deliberately in March, and now (in April) has Multiplication mostly correct still appeared as a Multiplication-weak student to the adaptive engine. The system kept recommending them Multiplication. Their actual current weakness — Factorization, say — was masked by the larger historical pile.

Learning is recency-biased. What the student is failing *this week* matters far more than what they failed three months ago.

### 1.2 The prerequisite graph was inert

`KnowledgeNode.prerequisiteNode` is a self-referencing `@ManyToOne` already seeded with real pedagogical relationships:

```
ARITH_ADDITION
   ├── ARITH_SUBTRACTION
   │       └── ALGEBRA_LINEAR
   │               └── ALGEBRA_FACTORIZE
   └── ARITH_MULTIPLICATION
           └── ARITH_DIVISION
                   └── FRACTIONS_SIMPLIFY
                           └── FRACTIONS_ADD_SUB
```

The adaptive algorithm never traversed it. It treated each topic as an isolated bag of exercises. A student failing Factorization who was *also* failing Linear Equations got recommended Factorization, again and again, because Factorization had the highest failure count. The fact that Linear Equations was the underlying gap — and that fixing Linear Equations would have made Factorization clarify on its own — was invisible to the algorithm.

In the architectural critique this was called out as "decoration" — defined in entities but never queried. Phase 4 finally puts it to work.

---

## 2. The fixes

### 2.1 Rolling 30-day window

New repository method:

```java
@Query("SELECT i.aiIdentifiedWeaknessCode, COUNT(i) as cnt " +
       "FROM InteractionLog i " +
       "WHERE i.student.id = :studentId " +
       "  AND i.isCorrect = false " +
       "  AND i.aiIdentifiedWeaknessCode IS NOT NULL " +
       "  AND i.createdAt > :since " +
       "GROUP BY i.aiIdentifiedWeaknessCode " +
       "ORDER BY cnt DESC")
List<Object[]> findRecentTopWeaknessesByStudentId(
    @Param("studentId") UUID studentId,
    @Param("since") LocalDateTime since);
```

The service computes `since = now() - 30 days` on each call. Older rows stay in the audit table — we never delete history — but they don't influence what comes next.

The old all-time query (`findTopWeaknessesByStudentId`) is deleted, not deprecated. After the service refactor it had no callers, and keeping it would tempt someone to use it later.

### 2.2 Prerequisite-descent algorithm

`StudentProgressService.pickAdaptiveTargetNode(studentId)` now does:

```
1. recent_failures := query last 30 days of failures, grouped by code
2. if recent_failures is empty → return random node (cold start)
3. primary_weakness := code with highest failure count
4. current := primary_weakness
5. while current has a prerequisite AND that prerequisite is also in recent_failures:
       current := prerequisite of current
6. return current
```

The loop is bounded (`MAX_PREREQ_HOPS = 10`) and has a `visited` set as a cycle guard. The seeded graph is 5 deep at most, so under normal conditions the loop exits because either the chain ran out (root reached) or a prereq is solid.

### 2.3 `@Transactional(readOnly = true)`

Both `getWeaknessSummary` and `getNextExercise` now have explicit read-only transaction boundaries. Two reasons:

1. The prereq-descent walk dereferences `KnowledgeNode.getPrerequisiteNode()`, a lazy association. Inside an open transaction this triggers a SELECT; outside one it throws `LazyInitializationException`. We were getting away with it before because `spring.jpa.open-in-view=true` kept a session open for the entire HTTP request — but relying on OSIV is fragile, and explicit is better.
2. Hibernate can apply read-only optimisations when the transaction is declared as such.

---

## 3. Worked examples

### Example A: pure top-level weakness, no foundational issue

```
Student's recent failures:
  ARITH_MULTIPLICATION: 8
  ALGEBRA_LINEAR:       3

Algorithm:
  primary_weakness = ARITH_MULTIPLICATION
  ARITH_MULTIPLICATION's prereq is ARITH_ADDITION
  ARITH_ADDITION is NOT in failing set → stop
  return ARITH_MULTIPLICATION
```

The student is genuinely just bad at Multiplication. We don't descend.

### Example B: deep foundational gap

```
Student's recent failures:
  ALGEBRA_FACTORIZE:  5
  ALGEBRA_LINEAR:     4
  ARITH_SUBTRACTION:  2

Algorithm:
  primary_weakness = ALGEBRA_FACTORIZE
  ALGEBRA_FACTORIZE → prereq ALGEBRA_LINEAR (failing)     → descend
  ALGEBRA_LINEAR    → prereq ARITH_SUBTRACTION (failing)  → descend
  ARITH_SUBTRACTION → prereq ARITH_ADDITION (NOT failing) → stop
  return ARITH_SUBTRACTION
```

The student was being served Factorization exercises forever under the old algorithm. Now we route them back to Subtraction, the actual problem.

### Example C: cold start

```
Student's recent failures: (none)

Algorithm:
  recent_failures is empty
  return random node
```

Same as before — no usable data, pick something. A smarter cold-start (always begin at the easiest difficulty-1 node) is in the follow-ups list.

### Example D: stale topic ages out

```
Day 0:   student fails Multiplication 8 times.
Day 30:  student aces Multiplication 20 times. recent_failures still has it.
Day 60:  student aces everything. recent_failures is empty (the Day 0 failures
         have aged out of the 30-day window). Algorithm falls back to
         cold start.
```

Under the previous algorithm, day 60 would still have Multiplication in the top weaknesses (8 all-time failures) and would keep serving it. Now it correctly recognises that Multiplication is no longer a current weakness.

---

## 4. Files changed

| File | Change |
|---|---|
| `repository/InteractionLogRepository.java` | Added `findRecentTopWeaknessesByStudentId(studentId, since)`. Removed the now-unused `findTopWeaknessesByStudentId`. |
| `service/StudentProgressService.java` | Added `WEAKNESS_WINDOW` (30d) and `MAX_PREREQ_HOPS` (10) constants. Refactored `getWeaknessSummary` and `getNextExercise` around the windowed query. Extracted `pickAdaptiveTargetNode` and `findDeepestFailingPrerequisite` helpers. Added `@Transactional(readOnly = true)` to both public methods. |

---

## 5. Behaviour comparison

| Scenario | Before | After |
|---|---|---|
| Student failed Multiplication 8 times in February, now in April | Still served Multiplication | Multiplication has aged out — algorithm uses current weaknesses |
| Student failing Factorization (5) + Linear Equations (4) | Served Factorization repeatedly | Walks down to Linear Equations |
| Student failing Factorization (5) + Linear (4) + Subtraction (2) | Served Factorization | Walks all the way down to Subtraction |
| Brand-new student | Random node | Unchanged: random node |
| Student with strong recent perfect record | Could still serve old failed topic | Cold-start path: random node |

---

## 6. Known limitations and follow-ups

1. **Cold start is still random.** "Random" is fine when we know nothing, but for a brand-new student it would be better to always start at the easiest node (`ARITH_ADDITION`, difficulty 1) and progress. Tracking forward via the prereq graph instead of backward. Worth doing in a future release; not in scope for `0.0.1`.

2. **No mastery model.** If a student has answered the last 20 Addition questions correctly, the engine has no representation of that. Today we just rely on Addition not being in the failing set. A proper mastery model (Bayesian Knowledge Tracing, even a simple EWMA over correctness) would let us *push* the student toward harder topics, not just *avoid* the failing one. Substantial future work.

3. **Recency window is global.** 30 days for every student, every topic. A topic the student practises every day probably has a shorter effective decay; an abstract topic seen weekly should have a longer one. Per-topic or per-student windows are a refinement.

4. **Prereq descent uses presence, not magnitude.** If Factorization has 50 failures and its prereq Linear Equations has 1, the algorithm still descends to Linear Equations. A magnitude-aware version ("only descend if the prereq has at least 25% of the parent's failure count") would prevent over-descending on noise. Easy refinement.

5. **The graph is shallow.** 8 nodes, 5 deep at most. The prereq-descent algorithm scales gracefully to deeper graphs, but the seeded curriculum is small. Real-world deployment would need a much richer knowledge graph — that's a curriculum-design problem, not a code problem.

---

## 7. How to verify locally

```bash
# 1. Restart Spring Boot to pick up the new queries
./mvnw spring-boot:run -pl backend-springboot/math-wise-backend

# 2. Set up a student. Log in, get a JWT.

# 3. Simulate the "deep foundational gap" example by submitting
#    incorrect answers, in this order:
#      - 2× wrong answers on ARITH_SUBTRACTION exercises
#      - 4× wrong answers on ALGEBRA_LINEAR exercises
#      - 5× wrong answers on ALGEBRA_FACTORIZE exercises
#    (The LLM's weakness_node response, after the Phase-1 normalisation,
#     should reliably match the tested node.)

# 4. Then call:
curl -H "Authorization: Bearer <jwt>" \
  'http://localhost:9090/api/student/next-exercise'

# Expected: the returned exercise has node_code = ARITH_SUBTRACTION
# (descended from FACTORIZE → LINEAR → SUBTRACTION).

# 5. Also verify the weakness summary uses the recency window:
curl -H "Authorization: Bearer <jwt>" \
  'http://localhost:9090/api/student/progress'
# Expected: same 3 topics, but the failure_count reflects only the
# last 30 days. (To see ageing in action, you'd need to manipulate
# created_at values in interaction_logs directly via psql.)
```

---

## 8. Why this was Phase 4

Phase 1 made the data trustworthy. Phase 2 made errors trustworthy. Phase 3 made deployment configuration trustworthy. Phase 4 finally pulls the trigger on the value proposition: the app's *adaptive* nature is no longer a marketing claim that the algorithm fails to back up. There's a real, explainable, pedagogically defensible rule for what gets shown next. Phases 5 and 6 will be incremental polish from here.
