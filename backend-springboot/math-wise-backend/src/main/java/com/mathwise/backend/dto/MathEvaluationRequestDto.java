package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

//This is what Flutter sends to Spring Boot
public class MathEvaluationRequestDto {
    //FastAPI needs snake_case fields, so we specify it for Spring Boot's Jackson library
    private String equation;
    @JsonProperty("correct_answer")
    private String correctAnswer;
    @JsonProperty("student_answer")
    private String studentAnswer;

    public String getEquation() {
        return equation;
    }

    public void setEquation(String equation) {
        this.equation = equation;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getStudentAnswer() {
        return studentAnswer;
    }

    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }
}
