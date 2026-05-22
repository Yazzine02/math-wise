package com.mathwise.backend.service;

import com.mathwise.backend.dto.ExerciseDto;
import com.mathwise.backend.dto.WeaknessSummaryDto;
import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.ExerciseRepository;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
public class StudentProgressService {

    /**
     * Rolling window the adaptive engine looks at when computing recent
     * weaknesses. Older interactions are kept in {@code interaction_logs}
     * for audit purposes but are no longer steering exercise selection — a
     * topic the student has since drilled to mastery should age out, and
     * picking it up at the next session as if no time had passed is wrong.
     *
     * <p>30 days is a sensible default for K-12 math: long enough to detect
     * persistent weaknesses, short enough that a corrected topic ages out.
     */
    private static final Duration WEAKNESS_WINDOW = Duration.ofDays(30);

    /**
     * Safety bound on the prerequisite-chain walk. The seeded graph is at most
     * 5 deep; this is purely defensive against malformed data containing a
     * prerequisite cycle.
     */
    private static final int MAX_PREREQ_HOPS = 10;

    private final InteractionLogRepository interactionLogRepository;
    private final KnowledgeNodeRepository knowledgeNodeRepository;
    private final ExerciseRepository exerciseRepository;
    private final KnowledgeNodeResolver nodeResolver;
    private final Random random = new Random();

    public StudentProgressService(InteractionLogRepository interactionLogRepository,
                                   KnowledgeNodeRepository knowledgeNodeRepository,
                                   ExerciseRepository exerciseRepository,
                                   KnowledgeNodeResolver nodeResolver) {
        this.interactionLogRepository = interactionLogRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.exerciseRepository = exerciseRepository;
        this.nodeResolver = nodeResolver;
    }

    /**
     * Top-N recent weakness summary for the dashboard.
     *
     * <p>Uses the same 30-day window the adaptive engine uses, so the dashboard
     * and the {@code Start Practice} button always agree about what's hard
     * right now. Raw AI strings are resolved to canonical nodes via
     * {@link KnowledgeNodeResolver} and re-grouped under the canonical code so
     * historical noise (e.g. the LLM returning {@code "Multiplication"}
     * sometimes and {@code "ARITH_MULTIPLICATION"} other times) collapses into
     * a single entry.
     *
     * <p>{@code @Transactional(readOnly = true)} makes the JPA session lifetime
     * explicit at the boundary instead of relying on
     * {@code spring.jpa.open-in-view}.
     */
    @Transactional(readOnly = true)
    public WeaknessSummaryDto getWeaknessSummary(UUID studentId) {
        LocalDateTime since = LocalDateTime.now().minus(WEAKNESS_WINDOW);
        List<Object[]> rows = interactionLogRepository
                .findRecentTopWeaknessesByStudentId(studentId, since);

        Map<String, Long> countByCode = new LinkedHashMap<>();
        Map<String, String> titleByCode = new HashMap<>();
        for (Object[] row : rows) {
            String rawCode = (String) row[0];
            long count = ((Number) row[1]).longValue();
            Optional<KnowledgeNode> resolved = nodeResolver.resolve(rawCode);
            if (resolved.isEmpty()) continue;
            KnowledgeNode node = resolved.get();
            countByCode.merge(node.getNodeCode(), count, Long::sum);
            titleByCode.put(node.getNodeCode(), node.getTitle());
        }

        List<WeaknessSummaryDto.WeaknessEntry> entries = countByCode.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> new WeaknessSummaryDto.WeaknessEntry(
                        e.getKey(),
                        titleByCode.get(e.getKey()),
                        e.getValue()))
                .toList();
        return new WeaknessSummaryDto(entries);
    }

    public ExerciseDto getNextExercise(UUID studentId) {
        return getNextExercise(studentId, null);
    }

    /**
     * Picks the next exercise the student should see.
     *
     * <ul>
     *   <li><b>Topic-locked path</b> ({@code nodeCode != null}): the caller
     *       has explicitly chosen a topic (e.g. came from a Lesson screen).
     *       We serve a random exercise from that node, no recommendation
     *       logic applied.</li>
     *   <li><b>Adaptive path</b> ({@code nodeCode == null}): we look at the
     *       student's recent failures and walk the prerequisite chain to find
     *       the deepest topic that is still failing. See
     *       {@link #pickAdaptiveTargetNode(UUID)} for the algorithm.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public ExerciseDto getNextExercise(UUID studentId, String nodeCode) {
        KnowledgeNode targetNode;

        if (nodeCode != null && !nodeCode.isBlank()) {
            targetNode = nodeResolver.resolve(nodeCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown node code or title: " + nodeCode));
        } else {
            targetNode = pickAdaptiveTargetNode(studentId);
        }

        List<Exercise> exercises = exerciseRepository.findByKnowledgeNode(targetNode);
        if (exercises.isEmpty()) {
            throw new IllegalStateException("No exercises for node: " + targetNode.getNodeCode());
        }

        Exercise chosen = exercises.get(random.nextInt(exercises.size()));
        return toDto(chosen);
    }

    /**
     * Picks the knowledge node the student should practise next, based on the
     * adaptive algorithm.
     *
     * <ol>
     *   <li>Fetch recent failures (last {@link #WEAKNESS_WINDOW}) and resolve
     *       each raw AI string to a canonical node.</li>
     *   <li>Re-group failure counts by canonical code — this collapses noise
     *       from the LLM returning different strings for the same concept.</li>
     *   <li>Pick the canonical code with the highest count as the "primary
     *       weakness." This is the topic the student is most visibly
     *       struggling with.</li>
     *   <li>Walk the prerequisite chain from the primary weakness via
     *       {@link #findDeepestFailingPrerequisite(KnowledgeNode, Set)}. If
     *       any prereq is also failing, descend to it. Keep descending until
     *       a foundational prereq is solid (or there's no prereq at all).</li>
     *   <li>Cold start (no usable recent history): fall back to a random
     *       knowledge node so the student gets <i>something</i> to do.</li>
     * </ol>
     *
     * <p>The pedagogical principle the prereq walk encodes: a student failing
     * Factorization who is <i>also</i> failing Linear Equations is probably
     * struggling with Factorization because they don't have Linear Equations
     * solid yet. Fixing the foundation should make the symptom resolve.
     */
    private KnowledgeNode pickAdaptiveTargetNode(UUID studentId) {
        LocalDateTime since = LocalDateTime.now().minus(WEAKNESS_WINDOW);
        List<Object[]> rows = interactionLogRepository
                .findRecentTopWeaknessesByStudentId(studentId, since);

        // (canonical_code) -> recent failure count
        Map<String, Long> failureCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Optional<KnowledgeNode> resolved = nodeResolver.resolve((String) row[0]);
            if (resolved.isEmpty()) continue;
            failureCounts.merge(
                    resolved.get().getNodeCode(),
                    ((Number) row[1]).longValue(),
                    Long::sum);
        }

        if (failureCounts.isEmpty()) {
            return pickColdStartNode();
        }

        // Top-of-failures node.
        String topCode = failureCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();  // map is non-empty by the check above

        KnowledgeNode primary = knowledgeNodeRepository.findByNodeCode(topCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Resolved canonical code missing on re-lookup: " + topCode));

        return findDeepestFailingPrerequisite(primary, failureCounts.keySet());
    }

    /**
     * Walks the prerequisite chain starting from {@code start}, descending
     * whenever the current node's prereq is itself in the failing set. Stops
     * when: (a) we hit a root node with no prereq, (b) the current prereq is
     * not in the failing set (foundation is solid), or (c) we've made more
     * than {@link #MAX_PREREQ_HOPS} hops (cycle guard).
     *
     * <p>Worked example with the seeded graph:
     * <pre>
     *   failingCodes = {ALGEBRA_FACTORIZE, ALGEBRA_LINEAR, ARITH_SUBTRACTION}
     *   start        = ALGEBRA_FACTORIZE
     *
     *   ALGEBRA_FACTORIZE  → prereq ALGEBRA_LINEAR (failing)      → descend
     *   ALGEBRA_LINEAR     → prereq ARITH_SUBTRACTION (failing)   → descend
     *   ARITH_SUBTRACTION  → prereq ARITH_ADDITION (NOT failing)  → stop
     *
     *   result: ARITH_SUBTRACTION
     * </pre>
     */
    private KnowledgeNode findDeepestFailingPrerequisite(
            KnowledgeNode start, Set<String> failingCodes) {
        KnowledgeNode current = start;
        Set<String> visited = new HashSet<>();

        for (int hop = 0; hop < MAX_PREREQ_HOPS; hop++) {
            if (!visited.add(current.getNodeCode())) break;  // cycle guard

            KnowledgeNode prereq = current.getPrerequisiteNode();
            if (prereq == null) break;                                 // root reached
            if (!failingCodes.contains(prereq.getNodeCode())) break;   // foundation solid

            current = prereq;
        }
        return current;
    }

    /**
     * Cold start — student has no recent failures we can use. Pick a random
     * knowledge node so they get something to try. A smarter cold-start
     * (e.g. always begin at the easiest difficulty-1 node) is a sensible
     * future refinement.
     */
    private KnowledgeNode pickColdStartNode() {
        List<KnowledgeNode> allNodes = knowledgeNodeRepository.findAll();
        if (allNodes.isEmpty()) {
            throw new IllegalStateException("No knowledge nodes seeded.");
        }
        return allNodes.get(random.nextInt(allNodes.size()));
    }

    private ExerciseDto toDto(Exercise e) {
        ExerciseDto dto = new ExerciseDto();
        dto.setId(e.getId());
        dto.setNodeCode(e.getKnowledgeNode().getNodeCode());
        dto.setNodeTitle(e.getKnowledgeNode().getTitle());
        dto.setQuestionText(e.getQuestionText());
        dto.setCorrectAnswer(e.getCorrectAnswer());
        dto.setDifficultyLevel(e.getDifficultyLevel());
        return dto;
    }
}
