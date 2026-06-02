package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class ExerciseDto {
    private UUID id;

    @JsonProperty("node_code")
    private String nodeCode;

    @JsonProperty("node_title")
    private String nodeTitle;

    @JsonProperty("question_text")
    private String questionText;

    @JsonProperty("correct_answer")
    private String correctAnswer;

    @JsonProperty("difficulty_level")
    private int difficultyLevel;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }

    public String getNodeTitle() { return nodeTitle; }
    public void setNodeTitle(String nodeTitle) { this.nodeTitle = nodeTitle; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public int getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(int difficultyLevel) { this.difficultyLevel = difficultyLevel; }
}
