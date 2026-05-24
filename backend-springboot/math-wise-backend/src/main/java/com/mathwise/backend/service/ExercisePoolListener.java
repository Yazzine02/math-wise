package com.mathwise.backend.service;

import com.mathwise.backend.event.ExercisePoolLowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link ExercisePoolLowEvent} and asynchronously dispatches the
 * generation call to FastAPI via {@link ExerciseGenerationService}.
 *
 * <p>The user-facing request that published the event returns immediately;
 * Spring's async executor handles this method on a separate thread so the
 * HTTP round-trip to FastAPI (potentially 5-30s for LLM generation) never
 * blocks the student.
 *
 * <p><b>Migration note:</b> Phase 13 of the architectural roadmap replaces
 * this in-process {@code @EventListener} with a {@code @KafkaListener}
 * reading from the {@code exercise.pool.low} topic. The producer site in
 * {@code StudentProgressService} and the work site in
 * {@code ExerciseGenerationService} both stay unchanged — only this glue
 * file moves.
 */
@Component
public class ExercisePoolListener {

    private static final Logger log = LoggerFactory.getLogger(ExercisePoolListener.class);

    private final ExerciseGenerationService generationService;

    public ExercisePoolListener(ExerciseGenerationService generationService) {
        this.generationService = generationService;
    }

    @Async
    @EventListener
    public void onPoolLow(ExercisePoolLowEvent event) {
        try {
            generationService.topUpPoolFor(event.nodeCode());
        } catch (Exception ex) {
            // Generation is best-effort. Swallow exceptions so a flaky LLM
            // never corrupts the executor or hides bugs in adjacent listeners.
            log.warn("Pool top-up failed for {}: {}", event.nodeCode(), ex.getMessage());
        }
    }
}
