package com.mathwise.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server → client feedback after answer evaluation.
 *
 * <p>Java fields are camelCase (idiomatic, IDE-friendly). The
 * {@code @JsonProperty} annotations preserve the existing snake_case wire
 * format so the Flutter client doesn't need to change in lockstep.
 *
 * <p>{@code correct} is server-authoritative — set by
 * {@code AiEvaluationService} after the SymPy {@code /check-answer} call.
 * Flutter must read this from the response instead of doing its own string
 * comparison (that was the bug Phase 1 fixed).
 */
public class AiFeedbackDto {

    @JsonProperty("weakness_node")
    private String weaknessNode;

    private String explanation;

    @JsonProperty("is_correct")
    private boolean correct;

    public String getWeaknessNode() {
        return weaknessNode;
    }

    public void setWeaknessNode(String weaknessNode) {
        this.weaknessNode = weaknessNode;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }
}
