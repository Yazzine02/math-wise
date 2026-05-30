package com.mathwise.backend.service;

import com.mathwise.backend.dto.ExerciseDto;
import com.mathwise.backend.dto.WeaknessSummaryDto;
import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.InteractionLog;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.ExerciseRepository;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StudentProgressService}.
 *
 * <p><b>What's new compared to the resolver test:</b>
 *
 * <ul>
 *   <li>Five mocks, not one. The service has four repositories,
 *       one resolver, and an event publisher. Each gets a {@code @Mock}.</li>
 *   <li>{@code @Nested} classes — group related tests under the same
 *       concept. The IDE renders them as a tree; assertion failures
 *       point at the right nest. Optional but very readable.</li>
 *   <li>A reusable {@code @BeforeEach} sets up a fixture (the seeded
 *       knowledge graph) that every test in the class can rely on.</li>
 *   <li>{@code lenient().when(...)} — by default, Mockito complains if a
 *       stubbed call isn't actually made (strict stubbing). For shared
 *       fixtures across multiple tests, lenient is the right escape
 *       hatch; for individual test stubs, leave strict on.</li>
 * </ul>
 *
 * <p><b>The pattern you should internalise:</b> one test per behaviour,
 * not one test per method. {@code getNextExercise} is exercised by many
 * tests below, each verifying a different contract: cold-start,
 * weakness-driven, mastery filter, etc.
 */
@ExtendWith(MockitoExtension.class)
class StudentProgressServiceTest {

    @Mock InteractionLogRepository interactionLogRepository;
    @Mock KnowledgeNodeRepository knowledgeNodeRepository;
    @Mock ExerciseRepository exerciseRepository;
    @Mock KnowledgeNodeResolver nodeResolver;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StudentProgressService service;

    private final UUID studentId = UUID.randomUUID();

    // Seeded graph (matches DataSeeder, minus the unrelated nodes).
    private KnowledgeNode addition;
    private KnowledgeNode subtraction;
    private KnowledgeNode multiplication;
    private KnowledgeNode division;

    @BeforeEach
    void setUp() {
        // Build the curriculum graph: addition is the root; subtraction
        // and multiplication depend on it; division depends on multiplication.
        addition = node("ARITH_ADDITION", "Addition", 1, null);
        subtraction = node("ARITH_SUBTRACTION", "Subtraction", 1, addition);
        multiplication = node("ARITH_MULTIPLICATION", "Multiplication", 2, addition);
        division = node("ARITH_DIVISION", "Division", 2, multiplication);
    }

    // ─────────────────────────────────────────────────────────────────────
    // The pickColdStartNode logic was the bug fixed in commit 8e58341 —
    // worth pinning down with tests so it doesn't regress.
    // ─────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Cold-start curriculum progression")
    class ColdStart {

        @Test
        @DisplayName("brand-new student (no attempts) lands on the easiest root node")
        void brand_new_student_gets_addition() {
            // Arrange: empty interaction history, no recent failures.
            when(interactionLogRepository.findRecentTopWeaknessesByStudentId(
                    eq(studentId), any())).thenReturn(List.of());
            when(knowledgeNodeRepository.findAll()).thenReturn(
                    List.of(addition, subtraction, multiplication, division));
            // Mastery check returns "not enough data" for every node.
            stubMasteryEmpty();
            // Exercises exist for addition (we have to return at least one
            // so getNextExercise doesn't throw).
            Exercise stubEx = exercise(addition, "What is 1 + 1?", "2", 1);
            when(exerciseRepository.findByKnowledgeNode(addition)).thenReturn(List.of(stubEx));

            // Act
            ExerciseDto result = service.getNextExercise(studentId, null);

            // Assert
            assertThat(result.getNodeCode()).isEqualTo("ARITH_ADDITION");
        }

        @Test
        @DisplayName("after mastering ARITH_ADDITION, the engine graduates to ARITH_SUBTRACTION")
        void graduates_after_mastering_addition() {
            // No weaknesses currently.
            when(interactionLogRepository.findRecentTopWeaknessesByStudentId(
                    eq(studentId), any())).thenReturn(List.of());
            when(knowledgeNodeRepository.findAll()).thenReturn(
                    List.of(addition, subtraction, multiplication, division));

            // Mastery: addition is mastered (last 3 all correct); others aren't.
            stubMastered(addition);
            stubNotMastered(subtraction);
            stubNotMastered(multiplication);
            stubNotMastered(division);

            // Exercise pool for the expected target.
            Exercise stubEx = exercise(subtraction, "What is 10 - 4?", "6", 1);
            when(exerciseRepository.findByKnowledgeNode(subtraction)).thenReturn(List.of(stubEx));

            // Act
            ExerciseDto result = service.getNextExercise(studentId, null);

            // Assert — subtraction has prereq=addition (mastered),
            // difficulty 1, alphabetically beats multiplication.
            assertThat(result.getNodeCode()).isEqualTo("ARITH_SUBTRACTION");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Weakness path: with live failures, we descend the prereq chain.
    // ─────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Weakness-driven selection + prereq descent")
    class WeaknessPath {

        @Test
        @DisplayName("serves the failing node when its prereq is solid")
        void serves_failing_node_when_prereq_solid() {
            // 5 failures on Division, 0 on its prereq (Multiplication).
            // List.<Object[]>of(...) — explicit type witness is required because
            // Java's overload resolution on List.of() collapses Object[] arguments
            // to the varargs overload and infers List<Object> rather than
            // List<Object[]>. Without the witness, the assignment to the
            // findRecentTopWeaknessesByStudentId() return type fails to compile.
            when(interactionLogRepository.findRecentTopWeaknessesByStudentId(
                    eq(studentId), any())).thenReturn(List.<Object[]>of(
                    new Object[]{"ARITH_DIVISION", 5L}
            ));
            when(nodeResolver.resolve("ARITH_DIVISION")).thenReturn(Optional.of(division));
            when(knowledgeNodeRepository.findByNodeCode("ARITH_DIVISION"))
                    .thenReturn(Optional.of(division));
            stubNotMastered(division);

            Exercise stubEx = exercise(division, "84 / 7", "12", 1);
            when(exerciseRepository.findByKnowledgeNode(division)).thenReturn(List.of(stubEx));

            ExerciseDto result = service.getNextExercise(studentId, null);

            assertThat(result.getNodeCode()).isEqualTo("ARITH_DIVISION");
        }

        @Test
        @DisplayName("filters mastered weaknesses out of the candidate set")
        void mastered_weaknesses_are_skipped() {
            // Both addition and division are in the failing list, but
            // addition has since been mastered. The engine should ignore
            // addition and go to division.
            when(interactionLogRepository.findRecentTopWeaknessesByStudentId(
                    eq(studentId), any())).thenReturn(List.<Object[]>of(
                    new Object[]{"ARITH_ADDITION", 8L},
                    new Object[]{"ARITH_DIVISION", 3L}
            ));
            when(nodeResolver.resolve("ARITH_ADDITION")).thenReturn(Optional.of(addition));
            when(nodeResolver.resolve("ARITH_DIVISION")).thenReturn(Optional.of(division));
            when(knowledgeNodeRepository.findByNodeCode("ARITH_DIVISION"))
                    .thenReturn(Optional.of(division));

            stubMastered(addition);
            stubNotMastered(division);

            Exercise stubEx = exercise(division, "84 / 7", "12", 1);
            when(exerciseRepository.findByKnowledgeNode(division)).thenReturn(List.of(stubEx));

            ExerciseDto result = service.getNextExercise(studentId, null);

            assertThat(result.getNodeCode()).isEqualTo("ARITH_DIVISION");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // The weakness summary endpoint that powers the dashboard cards.
    // ─────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getWeaknessSummary")
    class WeaknessSummary {

        @Test
        @DisplayName("returns top 3 weaknesses sorted by failure count, mastered ones excluded")
        void returns_top_three_minus_mastered() {
            when(interactionLogRepository.findRecentTopWeaknessesByStudentId(
                    eq(studentId), any())).thenReturn(List.<Object[]>of(
                    new Object[]{"ARITH_ADDITION", 9L},      // mastered → drop
                    new Object[]{"ARITH_DIVISION", 5L},
                    new Object[]{"ARITH_MULTIPLICATION", 3L},
                    new Object[]{"ARITH_SUBTRACTION", 1L}
            ));
            when(nodeResolver.resolve("ARITH_ADDITION")).thenReturn(Optional.of(addition));
            when(nodeResolver.resolve("ARITH_DIVISION")).thenReturn(Optional.of(division));
            when(nodeResolver.resolve("ARITH_MULTIPLICATION")).thenReturn(Optional.of(multiplication));
            when(nodeResolver.resolve("ARITH_SUBTRACTION")).thenReturn(Optional.of(subtraction));

            stubMastered(addition);
            stubNotMastered(division);
            stubNotMastered(multiplication);
            stubNotMastered(subtraction);

            WeaknessSummaryDto summary = service.getWeaknessSummary(studentId);

            assertThat(summary.getWeaknesses())
                    .extracting(WeaknessSummaryDto.WeaknessEntry::getNodeCode)
                    .containsExactly("ARITH_DIVISION", "ARITH_MULTIPLICATION", "ARITH_SUBTRACTION");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper builders + reusable stubs. Keeping these at the bottom keeps
    // the test methods focused on the behaviour they're documenting.
    // ─────────────────────────────────────────────────────────────────────

    private KnowledgeNode node(String code, String title, int difficulty, KnowledgeNode prereq) {
        KnowledgeNode n = new KnowledgeNode();
        n.setNodeCode(code);
        n.setTitle(title);
        n.setDifficultyLevel(difficulty);
        n.setPrerequisiteNode(prereq);
        return n;
    }

    private Exercise exercise(KnowledgeNode node, String q, String a, int difficulty) {
        Exercise e = new Exercise();
        e.setKnowledgeNode(node);
        e.setQuestionText(q);
        e.setCorrectAnswer(a);
        e.setDifficultyLevel(difficulty);
        return e;
    }

    private InteractionLog correctAttempt() {
        InteractionLog log = new InteractionLog();
        log.setCorrect(true);
        return log;
    }

    /** Stub the mastery query so that the node has 3 correct attempts in a row. */
    private void stubMastered(KnowledgeNode n) {
        lenient().when(interactionLogRepository
                .findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(
                        eq(studentId), eq(n.getNodeCode()), any(Pageable.class)))
                .thenReturn(List.of(correctAttempt(), correctAttempt(), correctAttempt()));
    }

    /** Stub the mastery query so the node has 0 attempts (not mastered). */
    private void stubNotMastered(KnowledgeNode n) {
        lenient().when(interactionLogRepository
                .findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(
                        eq(studentId), eq(n.getNodeCode()), any(Pageable.class)))
                .thenReturn(List.of());
    }

    /** For tests that don't care about per-node mastery — return empty for everything. */
    private void stubMasteryEmpty() {
        lenient().when(interactionLogRepository
                .findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(
                        any(UUID.class), any(String.class), any(Pageable.class)))
                .thenReturn(List.of());
    }
}
