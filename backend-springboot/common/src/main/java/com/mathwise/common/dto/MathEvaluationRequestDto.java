package com.mathwise.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Spring Boot → FastAPI: the payload sent to {@code /evaluate-error}.
 *
 * <p>Phase 7: extended with the {@code node_code} of the tested topic and the
 * walk of its formal prerequisites. The LLM uses this context to reconstruct
 * the student's likely steps and attribute the weakness to the specific
 * prerequisite skill the error reflects (e.g. a division slip during a
 * linear-equation problem).
 */
public class MathEvaluationRequestDto {

    private String equation;

    @JsonProperty("correct_answer")
    private String correctAnswer;

    @JsonProperty("student_answer")
    private String studentAnswer;

    /** Canonical code of the topic the question is testing. */
    @JsonProperty("node_code")
    private String nodeCode;

    /**
     * The tested node's prerequisite chain in order from immediate prereq
     * outward (e.g. ALGEBRA_LINEAR → [ARITH_SUBTRACTION, ARITH_ADDITION]).
     * Sent as a hint to the LLM, NOT as the only valid weakness candidates —
     * the prompt still allows any of the 8 canonical codes.
     */
    @JsonProperty("prerequisite_codes")
    private List<String> prerequisiteCodes;

    public String getEquation() { return equation; }
    public void setEquation(String equation) { this.equation = equation; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }

    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }

    public List<String> getPrerequisiteCodes() { return prerequisiteCodes; }
    public void setPrerequisiteCodes(List<String> prerequisiteCodes) {
        this.prerequisiteCodes = prerequisiteCodes;
    }
}
