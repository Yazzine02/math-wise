package com.mathwise.backend.service;

import com.mathwise.backend.dto.ExerciseDto;
import com.mathwise.backend.dto.WeaknessSummaryDto;
import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.ExerciseRepository;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class StudentProgressService {

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
     * Builds the dashboard's "weak areas" list.
     *
     * <p>Older rows in {@code interaction_logs} may have a free-form title in
     * {@code ai_identified_weakness_code} (e.g. "Multiplication") instead of
     * the canonical code. We resolve each raw value to a real node, re-group
     * counts under the canonical code, and silently drop anything that cannot
     * be resolved — this way the UI only ever shows entries it can later use
     * to drive {@link #getNextExercise(UUID, String)}.
     */
    public WeaknessSummaryDto getWeaknessSummary(UUID studentId) {
        List<Object[]> rows = interactionLogRepository.findTopWeaknessesByStudentId(studentId);

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
     * If {@code nodeCode} is provided, returns a random exercise from that node
     * (used when the student is practising a specific course topic). Accepts
     * either a canonical code ("ARITH_MULTIPLICATION") or a title
     * ("Multiplication") — the resolver handles both, so older data and any
     * LLM slips don't crash the request.
     *
     * <p>If null, falls back to adaptive selection based on weakness history.
     */
    public ExerciseDto getNextExercise(UUID studentId, String nodeCode) {
        KnowledgeNode targetNode = null;

        if (nodeCode != null && !nodeCode.isBlank()) {
            targetNode = nodeResolver.resolve(nodeCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown node code or title: " + nodeCode));
        } else {
            List<Object[]> weaknesses = interactionLogRepository.findTopWeaknessesByStudentId(studentId);
            for (Object[] row : weaknesses) {
                Optional<KnowledgeNode> resolved = nodeResolver.resolve((String) row[0]);
                if (resolved.isPresent()) {
                    targetNode = resolved.get();
                    break;
                }
            }
            if (targetNode == null) {
                List<KnowledgeNode> allNodes = knowledgeNodeRepository.findAll();
                if (allNodes.isEmpty()) throw new IllegalStateException("No knowledge nodes seeded.");
                targetNode = allNodes.get(random.nextInt(allNodes.size()));
            }
        }

        List<Exercise> exercises = exerciseRepository.findByKnowledgeNode(targetNode);
        if (exercises.isEmpty()) {
            throw new IllegalStateException("No exercises for node: " + targetNode.getNodeCode());
        }

        Exercise chosen = exercises.get(random.nextInt(exercises.size()));
        return toDto(chosen);
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
