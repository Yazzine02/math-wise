package com.mathwise.backend.dto;

public class AiFeedbackDto {
    private String weakness_node;
    private String explanation;
    // Server-authoritative correctness — set by AiEvaluationService after the
    // SymPy /check-answer call. Flutter must read this from the response
    // instead of doing its own string comparison.
    private boolean is_correct;

    public String getWeakness_node() {
        return weakness_node;
    }

    public void setWeakness_node(String weakness_node) {
        this.weakness_node = weakness_node;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isIs_correct() {
        return is_correct;
    }

    public void setIs_correct(boolean is_correct) {
        this.is_correct = is_correct;
    }
}
