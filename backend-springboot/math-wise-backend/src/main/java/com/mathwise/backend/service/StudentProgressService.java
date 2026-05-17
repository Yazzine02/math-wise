package com.mathwise.backend.service;

import com.mathwise.backend.dto.ExerciseDto;
import com.mathwise.backend.dto.WeaknessSummaryDto;
import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.ExerciseRepository;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class StudentProgressService {

    private final InteractionLogRepository interactionLogRepository;
    private final KnowledgeNodeRepository knowledgeNodeRepository;
    private final ExerciseRepository exerciseRepository;
    private final Random random = new Random();

    public StudentProgressService(InteractionLogRepository interactionLogRepository,
                                   KnowledgeNodeRepository knowledgeNodeRepository,
                                   ExerciseRepository exerciseRepository) {
        this.interactionLogRepository = interactionLogRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.exerciseRepository = exerciseRepository;
    }

    public WeaknessSummaryDto getWeaknessSummary(UUID studentId) {
        List<Object[]> rows = interactionLogRepository.findTopWeaknessesByStudentId(studentId);
        List<WeaknessSummaryDto.WeaknessEntry> entries = new ArrayList<>();
        int limit = Math.min(rows.size(), 3);
        for (int i = 0; i < limit; i++) {
            Object[] row = rows.get(i);
            String nodeCode = (String) row[0];
            long count = ((Number) row[1]).longValue();
            String title = knowledgeNodeRepository.findByNodeCode(nodeCode)
                    .map(KnowledgeNode::getTitle)
                    .orElse(nodeCode);
            entries.add(new WeaknessSummaryDto.WeaknessEntry(nodeCode, title, count));
        }
        return new WeaknessSummaryDto(entries);
    }

    public ExerciseDto getNextExercise(UUID studentId) {
        return getNextExercise(studentId, null);
    }

    /**
     * If {@code nodeCode} is provided, returns a random exercise from that specific node
     * (used when the student is practising a specific course topic).
     * If null, falls back to adaptive selection based on weakness history.
     */
    public ExerciseDto getNextExercise(UUID studentId, String nodeCode) {
        KnowledgeNode targetNode = null;

        if (nodeCode != null && !nodeCode.isBlank()) {
            targetNode = knowledgeNodeRepository.findByNodeCode(nodeCode)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown node code: " + nodeCode));
        } else {
            List<Object[]> weaknesses = interactionLogRepository.findTopWeaknessesByStudentId(studentId);
            if (!weaknesses.isEmpty()) {
                String weakestCode = (String) weaknesses.get(0)[0];
                targetNode = knowledgeNodeRepository.findByNodeCode(weakestCode).orElse(null);
            }
            if (targetNode == null) {
                List<KnowledgeNode> allNodes = knowledgeNodeRepository.findAll();
                if (allNodes.isEmpty()) throw new IllegalStateException("No knowledge nodes seeded.");
                targetNode = allNodes.get(random.nextInt(allNodes.size()));
            }
        }

        List<Exercise> exercises = exerciseRepository.findByKnowledgeNode(targetNode);
        if (exercises.isEmpty()) throw new IllegalStateException("No exercises for node: " + targetNode.getNodeCode());

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
