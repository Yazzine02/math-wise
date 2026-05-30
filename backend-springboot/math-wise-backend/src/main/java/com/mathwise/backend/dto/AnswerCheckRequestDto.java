package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sent from Spring Boot to FastAPI's {@code /check-answer} endpoint.
 * Snake-case property names match the Pydantic model on the Python side.
 */
public class AnswerCheckRequestDto {

    @JsonProperty("correct_answer")
    private String correctAnswer;

    @JsonProperty("student_answer")
    private String studentAnswer;

    public AnswerCheckRequestDto() {}

    public AnswerCheckRequestDto(String correctAnswer, String studentAnswer) {
        this.correctAnswer = correctAnswer;
        this.studentAnswer = studentAnswer;
    }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
}
