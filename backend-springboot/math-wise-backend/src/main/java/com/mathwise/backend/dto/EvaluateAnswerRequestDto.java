package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EvaluateAnswerRequestDto {
    @JsonProperty("node_code")
    private String nodeCode;

    private String equation;

    @JsonProperty("correct_answer")
    private String correctAnswer;

    @JsonProperty("student_answer")
    private String studentAnswer;

    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }

    public String getEquation() { return equation; }
    public void setEquation(String equation) { this.equation = equation; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
}
