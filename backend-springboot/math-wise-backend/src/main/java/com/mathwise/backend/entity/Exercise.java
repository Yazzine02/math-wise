package com.mathwise.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "exercises")
public class Exercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_node_id", nullable = false)
    private KnowledgeNode knowledgeNode;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String questionText;

    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer;

    @Column(name = "difficulty_level", nullable = false)
    private int difficultyLevel;

    public KnowledgeNode getKnowledgeNode() {
        return knowledgeNode;
    }

    public void setKnowledgeNode(KnowledgeNode knowledgeNode) {
        this.knowledgeNode = knowledgeNode;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }
}
