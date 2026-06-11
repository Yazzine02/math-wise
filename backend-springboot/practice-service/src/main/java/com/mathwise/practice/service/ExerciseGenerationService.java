package com.mathwise.practice.service;

import com.mathwise.common.dto.GenerateExercisesRequestDto;
import com.mathwise.common.dto.GenerateExercisesResponseDto;
import com.mathwise.common.dto.GeneratedExerciseDto;
import com.mathwise.common.entity.Exercise;
import com.mathwise.common.entity.KnowledgeNode;
import com.mathwise.common.repository.ExerciseRepository;
import com.mathwise.common.repository.KnowledgeNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Calls FastAPI's {@code POST /generate-exercises} and persists the returned
 * exercises. Runs in a separate thread via {@code ExercisePoolListener},
 * so the user-facing request that originally triggered the top-up never
 * blocks on the LLM round-trip.
 */
@Service
public class ExerciseGenerationService {

    /**
     * Once the pool for a node drops below this, the next user request
     * targeting that node will publish an {@code ExercisePoolLowEvent} and
     * trigger background generation. Higher values mean eager top-up (more
     * LLM calls); lower values mean lazier top-up but a risk of running out.
     */
    public static final int MIN_POOL_SIZE = 8;

    /** Batch size per LLM call. Amortises the round-trip cost. */
    private static final int BATCH_SIZE = 5;

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationService.class);

    private final RestTemplate restTemplate;
    private final ExerciseRepository exerciseRepository;
    private final KnowledgeNodeRepository knowledgeNodeRepository;

    @Value("${ai-service.url}")
    private String aiServiceUrl;

    public ExerciseGenerationService(RestTemplate restTemplate,
                                      ExerciseRepository exerciseRepository,
                                      KnowledgeNodeRepository knowledgeNodeRepository) {
        this.restTemplate = restTemplate;
        this.exerciseRepository = exerciseRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
    }

    /**
     * Tops up the exercise pool for {@code nodeCode} if it's still below
     * {@link #MIN_POOL_SIZE} at the time this method actually runs. Designed
     * to be called from {@code ExercisePoolListener.onPoolLow(...)} on a
     * background thread.
     *
     * <p>The method is {@code @Transactional} (a new transaction, since the
     * background thread doesn't inherit the publisher's transaction). It's
     * silent on every failure mode — generation is a best-effort top-up; if
     * it fails this turn the next student request will retry.
     */
    @Transactional
    public void topUpPoolFor(String nodeCode) {
        Optional<KnowledgeNode> nodeOpt = knowledgeNodeRepository.findByNodeCode(nodeCode);
        if (nodeOpt.isEmpty()) {
            log.warn("Pool top-up requested for unknown node: {}", nodeCode);
            return;
        }
        KnowledgeNode node = nodeOpt.get();

        // Re-check the pool size now that we're in our own transaction — a
        // concurrent request may have already filled it.
        long currentCount = exerciseRepository.countByKnowledgeNode(node);
        if (currentCount >= MIN_POOL_SIZE) {
            return;
        }

        GenerateExercisesResponseDto response;
        try {
            response = restTemplate.postForObject(
                    aiServiceUrl + "/generate-exercises",
                    new GenerateExercisesRequestDto(nodeCode, BATCH_SIZE),
                    GenerateExercisesResponseDto.class
            );
        } catch (Exception ex) {
            log.warn("Exercise generation request failed for {}: {}",
                    nodeCode, ex.getMessage());
            return;
        }

        if (response == null || response.getExercises() == null
                || response.getExercises().isEmpty()) {
            log.warn("Exercise generation returned no exercises for {}", nodeCode);
            return;
        }

        for (GeneratedExerciseDto gen : response.getExercises()) {
            Exercise e = new Exercise();
            e.setKnowledgeNode(node);
            e.setQuestionText(gen.getQuestionText());
            e.setCorrectAnswer(gen.getCorrectAnswer());
            e.setDifficultyLevel(gen.getDifficultyLevel());
            e.setActive(true);
            // When usedFallback is true these came from the deterministic
            // templates, NOT the LLM — flag them accordingly so analytics
            // can tell the two sources apart.
            e.setGeneratedByAi(!response.isUsedFallback());
            exerciseRepository.save(e);
        }

        log.info("Topped up {} with {} exercises (rejected {} LLM candidates, fallback={})",
                nodeCode,
                response.getExercises().size(),
                response.getRejectedCount(),
                response.isUsedFallback());
    }
}
