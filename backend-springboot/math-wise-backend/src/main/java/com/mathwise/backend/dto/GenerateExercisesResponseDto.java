package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Spring Boot ← FastAPI response from {@code POST /generate-exercises}.
 *
 * <p>{@code usedFallback} signals that the LLM path produced zero valid
 * candidates (either it was unavailable, returned malformed JSON, or every
 * candidate failed SymPy verification) and the templates kicked in instead.
 * Spring Boot uses this to set {@code Exercise.generatedByAi} correctly on
 * each saved row.
 */
public class GenerateExercisesResponseDto {

    private List<GeneratedExerciseDto> exercises;

    @JsonProperty("rejected_count")
    private int rejectedCount;

    @JsonProperty("used_fallback")
    private boolean usedFallback;

    public List<GeneratedExerciseDto> getExercises() { return exercises; }
    public void setExercises(List<GeneratedExerciseDto> exercises) { this.exercises = exercises; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public boolean isUsedFallback() { return usedFallback; }
    public void setUsedFallback(boolean usedFallback) { this.usedFallback = usedFallback; }
}
