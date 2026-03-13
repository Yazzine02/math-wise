package com.mathwise.backend.dto;

public class AiFeedbackDto {
    private String weakness_node;
    private String explanation;

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
}
