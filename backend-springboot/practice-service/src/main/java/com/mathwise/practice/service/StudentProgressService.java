package com.mathwise.practice.service;

import com.mathwise.common.dto.ExerciseDto;
import com.mathwise.common.dto.WeaknessSummaryDto;
import com.mathwise.common.entity.Exercise;
import com.mathwise.common.entity.InteractionLog;
import com.mathwise.common.entity.KnowledgeNode;
import com.mathwise.practice.event.ExercisePoolLowEvent;
import com.mathwise.common.repository.ExerciseRepository;
import com.mathwise.common.repository.InteractionLogRepository;
import com.mathwise.common.repository.KnowledgeNodeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
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
     * weaknesses. See phase-4.md.
     */
    private static final Duration WEAKNESS_WINDOW = Duration.ofDays(30);

    /** Safety bound on the prerequisite-chain walk. */
    private static final int MAX_PREREQ_HOPS = 10;

    /**
     * A weakness is considered "rectified" once the student's last
     * {@value} attempts on the tested node are all correct. Khan-Academy-style
     * mastery signal (Phase 7).
     */
    private static final int MASTERY_THRESHOLD = 3;

    /**
     * Probability that the adaptive engine swaps the current weakness target
     * for a different (previously-attempted) node, to provide variety and
     * lightweight spaced repetition over mastered material. 30% means the
     * student sees a non-weakness exercise about 1 in every 3 sessions
     * — enough to break up monotony without diluting remediation.
     */
    private static final double EXPLORATION_RATE = 0.30;

    private final InteractionLogRepository interactionLogRepository;
    private final KnowledgeNodeRepository knowledgeNodeRepository;
    private final ExerciseRepository exerciseRepository;
    private final KnowledgeNodeResolver nodeResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final Random random = new Random();

    public StudentProgressService(InteractionLogRepository interactionLogRepository,
                                   KnowledgeNodeRepository knowledgeNodeRepository,
                                   ExerciseRepository exerciseRepository,
                                   KnowledgeNodeResolver nodeResolver,
                                   ApplicationEventPublisher eventPublisher) {
        this.interactionLogRepository = interactionLogRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.exerciseRepository = exerciseRepository;
        this.nodeResolver = nodeResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Top-N recent weakness summary for the dashboard.
     *
     * <p>Phase 4: uses the 30-day window and resolves raw AI strings to
     * canonical nodes before re-grouping. Phase 7: additionally filters out
     * any node the student has now mastered ({@link #isMastered}), so the
     * dashboard reflects what's <i>currently</i> hard rather than
     * everything that was ever hard.
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
                .filter(e -> !isMastered(studentId, e.getKey()))
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
     *   <li><b>Topic-locked path</b> ({@code nodeCode != null}): respects the
     *       caller's choice unconditionally — no exploration mix.</li>
     *   <li><b>Adaptive path</b>: see
     *       {@link #pickAdaptiveTargetNode(UUID)} for the full algorithm.</li>
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

        // Phase 8: fire-and-forget pool top-up if the pool for this node is
        // running low. ExercisePoolListener picks the event up on a background
        // thread, calls FastAPI's /generate-exercises (which itself falls back
        // to deterministic templates if the LLM is unavailable), and persists
        // the new exercises. The student's current request is unaffected.
        if (exercises.size() < ExerciseGenerationService.MIN_POOL_SIZE) {
            eventPublisher.publishEvent(new ExercisePoolLowEvent(targetNode.getNodeCode()));
        }

        Exercise chosen = exercises.get(random.nextInt(exercises.size()));
        return toDto(chosen);
    }

    /**
     * Adaptive target-node selection (Phase 7 update).
     *
     * <ol>
     *   <li>Fetch recent failures (last {@link #WEAKNESS_WINDOW}) and resolve
     *       raw AI strings to canonical nodes.</li>
     *   <li>Drop any candidate the student has since mastered
     *       ({@link #isMastered}).</li>
     *   <li>If no live weaknesses remain → curriculum-aware cold start
     *       ({@link #pickColdStartNode}).</li>
     *   <li>Otherwise pick the top live weakness, walk the prerequisite chain
     *       to its deepest failing prereq.</li>
     *   <li>With probability {@link #EXPLORATION_RATE} (30%), swap the
     *       weakness target for an exploration pick — a previously-attempted
     *       node that's <i>not</i> the current weakness, to reinforce
     *       mastered material and avoid monotony.</li>
     * </ol>
     */
    private KnowledgeNode pickAdaptiveTargetNode(UUID studentId) {
        LocalDateTime since = LocalDateTime.now().minus(WEAKNESS_WINDOW);
        List<Object[]> rows = interactionLogRepository
                .findRecentTopWeaknessesByStudentId(studentId, since);

        Map<String, Long> failureCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Optional<KnowledgeNode> resolved = nodeResolver.resolve((String) row[0]);
            if (resolved.isEmpty()) continue;
            failureCounts.merge(
                    resolved.get().getNodeCode(),
                    ((Number) row[1]).longValue(),
                    Long::sum);
        }

        // Drop mastered weaknesses BEFORE picking the top one.
        failureCounts.entrySet().removeIf(e -> isMastered(studentId, e.getKey()));

        if (failureCounts.isEmpty()) {
            return pickColdStartNode(studentId);
        }

        String topCode = failureCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();

        KnowledgeNode primary = knowledgeNodeRepository.findByNodeCode(topCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Resolved canonical code missing on re-lookup: " + topCode));

        KnowledgeNode weaknessTarget = findDeepestFailingPrerequisite(primary, failureCounts.keySet());

        // 30% exploration: swap for a non-weakness review node when possible.
        if (random.nextDouble() < EXPLORATION_RATE) {
            KnowledgeNode exploration = pickExplorationNode(studentId, weaknessTarget.getNodeCode());
            if (exploration != null) {
                return exploration;
            }
        }
        return weaknessTarget;
    }

    /**
     * Picks a node for an exploration / review exercise — not the current
     * weakness. Strategy: prefer a previously-attempted node, but excluding
     * the current weakness; fall back to any non-weakness node from the
     * full set. Returns {@code null} if the student has only ever attempted
     * the weakness node (then the caller falls back to the weakness).
     */
    private KnowledgeNode pickExplorationNode(UUID studentId, String weaknessCode) {
        List<String> attempted = interactionLogRepository
                .findDistinctAttemptedNodeCodesByStudentId(studentId);
        List<String> candidates = attempted.stream()
                .filter(code -> !code.equals(weaknessCode))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        String pick = candidates.get(random.nextInt(candidates.size()));
        return knowledgeNodeRepository.findByNodeCode(pick).orElse(null);
    }

    /**
     * Walks the prerequisite chain starting from {@code start}, descending
     * whenever the current node's prereq is itself in the failing set.
     */
    private KnowledgeNode findDeepestFailingPrerequisite(
            KnowledgeNode start, Set<String> failingCodes) {
        KnowledgeNode current = start;
        Set<String> visited = new HashSet<>();

        for (int hop = 0; hop < MAX_PREREQ_HOPS; hop++) {
            if (!visited.add(current.getNodeCode())) break;

            KnowledgeNode prereq = current.getPrerequisiteNode();
            if (prereq == null) break;
            if (!failingCodes.contains(prereq.getNodeCode())) break;

            current = prereq;
        }
        return current;
    }

    /**
     * Cold start — student has no live (un-mastered) recent weaknesses we
     * can use. Hands the student the next exercise that should unlock for
     * them in the curriculum graph.
     *
     * <p>The original Phase 7 implementation picked uniformly at random from
     * <em>previously-attempted</em> nodes, which meant a student who'd only
     * ever touched ARITH_ADDITION (because that's what cold-start gave them
     * on day one) was forever stuck on ARITH_ADDITION — the only choice in
     * their attempted set. That's the bug this method fixes.
     *
     * <p>New behaviour:
     * <ol>
     *   <li>Compute the mastered set: every node where the student's last
     *       {@link #MASTERY_THRESHOLD} attempts are all correct.</li>
     *   <li>Find the easiest UNMASTERED node whose prerequisite is
     *       satisfied (prereq is null → it's a curriculum root, OR prereq
     *       is in the mastered set). This is the natural next step in the
     *       curriculum graph.</li>
     *   <li>Once everything is mastered (the student has cleared the
     *       curriculum), fall back to a random review pick from the
     *       attempted set so they can continue practising without the
     *       engine returning null.</li>
     *   <li>If even the attempted set is empty (truly cold install with
     *       seeded but never-touched nodes), fall back to the curriculum
     *       root.</li>
     * </ol>
     *
     * <p>Worked example (seeded graph, all difficulty values from
     * DataSeeder):
     * <pre>
     *   mastered = {}                    → ARITH_ADDITION (root, diff 1)
     *   mastered = {ADDITION}            → ARITH_SUBTRACTION (diff 1, prereq mastered)
     *   mastered = {ADD, SUB}            → ARITH_MULTIPLICATION (diff 2)
     *   mastered = {ADD, SUB, MULT}      → ARITH_DIVISION (diff 2)
     *   mastered = {ADD, SUB, MULT, DIV} → FRACTIONS_SIMPLIFY (diff 3)
     * </pre>
     */
    private KnowledgeNode pickColdStartNode(UUID studentId) {
        List<KnowledgeNode> allNodes = knowledgeNodeRepository.findAll();
        if (allNodes.isEmpty()) {
            throw new IllegalStateException("No knowledge nodes seeded.");
        }

        Set<String> mastered = new HashSet<>();
        for (KnowledgeNode n : allNodes) {
            if (isMastered(studentId, n.getNodeCode())) {
                mastered.add(n.getNodeCode());
            }
        }

        Optional<KnowledgeNode> next = allNodes.stream()
                .filter(n -> !mastered.contains(n.getNodeCode()))
                .filter(n -> {
                    KnowledgeNode prereq = n.getPrerequisiteNode();
                    return prereq == null || mastered.contains(prereq.getNodeCode());
                })
                .min(Comparator
                        .comparingInt(KnowledgeNode::getDifficultyLevel)
                        .thenComparing(KnowledgeNode::getNodeCode));

        if (next.isPresent()) {
            return next.get();
        }

        // Everything reachable is mastered — congratulations, the student
        // has cleared the curriculum. Fall back to random review.
        List<String> attempted = interactionLogRepository
                .findDistinctAttemptedNodeCodesByStudentId(studentId);
        if (!attempted.isEmpty()) {
            String pick = attempted.get(random.nextInt(attempted.size()));
            return knowledgeNodeRepository.findByNodeCode(pick)
                    .orElseGet(this::pickCurriculumStart);
        }

        return pickCurriculumStart();
    }

    /**
     * Lowest difficulty among nodes with no prerequisite (i.e. roots of the
     * curriculum graph). Deterministic — ties broken by node_code
     * alphabetical order so the entry point is stable.
     */
    private KnowledgeNode pickCurriculumStart() {
        List<KnowledgeNode> all = knowledgeNodeRepository.findAll();
        if (all.isEmpty()) {
            throw new IllegalStateException("No knowledge nodes seeded.");
        }
        return all.stream()
                .filter(n -> n.getPrerequisiteNode() == null)
                .min(Comparator
                        .comparingInt(KnowledgeNode::getDifficultyLevel)
                        .thenComparing(KnowledgeNode::getNodeCode))
                .orElseGet(() -> all.stream()
                        .min(Comparator
                                .comparingInt(KnowledgeNode::getDifficultyLevel)
                                .thenComparing(KnowledgeNode::getNodeCode))
                        .orElseThrow());
    }

    /**
     * Returns true if the student's most recent {@link #MASTERY_THRESHOLD}
     * attempts on this knowledge node (any outcome distribution before that
     * doesn't count) are all correct.
     *
     * <p>If the student has fewer than {@code MASTERY_THRESHOLD} total
     * attempts on this node, returns false — they haven't demonstrated
     * enough recent correctness to call it solid.
     */
    private boolean isMastered(UUID studentId, String nodeCode) {
        List<InteractionLog> recent = interactionLogRepository
                .findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(
                        studentId, nodeCode, PageRequest.of(0, MASTERY_THRESHOLD));
        if (recent.size() < MASTERY_THRESHOLD) return false;
        return recent.stream().allMatch(InteractionLog::isCorrect);
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
