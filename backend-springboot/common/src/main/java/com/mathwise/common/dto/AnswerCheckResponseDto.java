package com.mathwise.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Returned by FastAPI's {@code /check-answer} endpoint.
 * {@code usedSymbolicCheck} is informational — when false, FastAPI couldn't
 * parse the input as a math expression and fell back to plain string equality.
 */
public class AnswerCheckResponseDto {

    @JsonProperty("is_correct")
    private boolean correct;

    @JsonProperty("used_symbolic_check")
    private boolean usedSymbolicCheck;

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public boolean isUsedSymbolicCheck() { return usedSymbolicCheck; }
    public void setUsedSymbolicCheck(boolean usedSymbolicCheck) { this.usedSymbolicCheck = usedSymbolicCheck; }
}
