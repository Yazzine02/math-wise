package com.mathwise.backend.event;

/**
 * Fired by {@code StudentProgressService.getNextExercise} when the exercise
 * pool for the chosen target node drops below the minimum size. Listened to
 * by {@code ExercisePoolListener}, which dispatches the actual generation
 * call asynchronously.
 *
 * <p><b>Why an event rather than a direct service call?</b> This is the
 * port/adapter abstraction that lets Phase 13 swap in Kafka without
 * touching producer code. Today the event is delivered in-process via
 * Spring's {@code ApplicationEventPublisher}; tomorrow the listener
 * becomes a {@code @KafkaListener} reading from
 * {@code exercise.pool.low}. {@code StudentProgressService} stays
 * unchanged either way.
 *
 * <p>Carries the {@code nodeCode} string (not the {@code KnowledgeNode}
 * entity) so the consumer can safely re-fetch in its own transaction —
 * detached JPA entities across thread boundaries are a footgun.
 */
public record ExercisePoolLowEvent(String nodeCode) {}
