package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One item from the {@code POST /generate-exercises} response.
 * The {@code correctAnswer} is the SymPy-canonical form — FastAPI deliberately
 * overwrites whatever the LLM claimed with what SymPy computed, so this is
 * safe to persist directly as the truth.
 */
public class GeneratedExerciseDto {

    @JsonProperty("question_text")
    private String questionText;

    @JsonProperty("correct_answer")
    private String correctAnswer;

    @JsonProperty("difficulty_level")
    private int difficultyLevel;

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public int getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(int difficultyLevel) { this.difficultyLevel = difficultyLevel; }
}
