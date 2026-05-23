package com.mathwise.backend.service;

import com.mathwise.backend.dto.ExerciseDto;
import com.mathwise.backend.dto.WeaknessSummaryDto;
import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.InteractionLog;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.ExerciseRepository;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
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
     * can use.
     *
     * <p>Curriculum-aware strategy (Phase 7): if the student has never
     * attempted ANY exercise we serve the lowest-difficulty root node
     * (in the seeded graph: ARITH_ADDITION). Otherwise the student has
     * cleared their backlog — they're free to roam, so we pick randomly
     * from the nodes they've already touched.
     */
    private KnowledgeNode pickColdStartNode(UUID studentId) {
        List<String> attempted = interactionLogRepository
                .findDistinctAttemptedNodeCodesByStudentId(studentId);

        if (attempted.isEmpty()) {
            return pickCurriculumStart();
        }

        // Returning student with no live weaknesses — pick from familiar
        // territory rather than restarting them at Addition.
        String pick = attempted.get(random.nextInt(attempted.size()));
        return knowledgeNodeRepository.findByNodeCode(pick)
                .orElseGet(this::pickCurriculumStart);
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
