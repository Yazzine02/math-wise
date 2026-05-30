package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EvaluateAnswerRequestDto {

    @JsonProperty("node_code")
    @NotBlank(message = "node_code is required")
    private String nodeCode;

    @NotBlank(message = "equation is required")
    @Size(max = 500, message = "equation must be 500 characters or fewer")
    private String equation;

    @JsonProperty("correct_answer")
    @NotBlank(message = "correct_answer is required")
    private String correctAnswer;

    @JsonProperty("student_answer")
    @NotBlank(message = "student_answer is required")
    @Size(max = 500, message = "student_answer must be 500 characters or fewer")
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
