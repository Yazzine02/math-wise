package com.mathwise.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Represents a math exercise that can be evaluated by Sympy.
 *
 * Design decisions:
 * - exercise_type drives the Sympy router in FastAPI (diff / solve / integrate / limit)
 * - question_display is what the student sees in the Flutter app (human-readable)
 * - expected_sympy is what Sympy reads to compare with the student's answer (machine format)
 * - sympy_context carries extra hints for Sympy (variable name, bounds for definite integrals, etc.)
 *
 * This entity is intentionally owned by FastAPI for evaluation logic.
 * Spring Boot exposes it via read-only endpoints so Flutter can fetch exercises.
 */
@Entity
@Table(name = "exercises", indexes = {
        @Index(name = "idx_exercises_type",   columnList = "exercise_type"),
        @Index(name = "idx_exercises_node",   columnList = "knowledge_node_id"),
        @Index(name = "idx_exercises_active", columnList = "is_active")
})
public class Exercise extends BaseEntity {

    /**
     * The math chapter this exercise belongs to.
     * Drives the Sympy router in FastAPI :
     *   EQUATION   → solve()
     *   DERIVATIVE  → diff()
     *   INTEGRAL    → integrate()
     *   LIMIT       → limit()
     */
    public enum ExerciseType {
        EQUATION,
        DERIVATIVE,
        INTEGRAL,
        LIMIT
    }

    /**
     * Link to the knowledge graph node this exercise tests.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_node_id", nullable = false, updatable = false)
    private KnowledgeNode knowledgeNode;

    /**
     * Drives the Sympy router. Stored as a VARCHAR in DB via @Enumerated.
     * Example values : EQUATION, DERIVATIVE, INTEGRAL, LIMIT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_type", nullable = false, updatable = false, length = 20)
    private ExerciseType exerciseType;

    /**
     * What the student sees in the Flutter app.
     * Human-readable, may contain LaTeX-style notation.
     * Example : "Calculer la dérivée de f(x) = x² · sin(x)"
     */
    @Column(name = "question_display", nullable = false, length = 500)
    private String questionDisplay;

    /**
     * The correct answer in a format Sympy can parse directly.
     * No LaTeX, no ambiguity — pure Sympy syntax.
     * Examples :
     *   DERIVATIVE  → "2*x*sin(x) + x**2*cos(x)"
     *   EQUATION    → "2"                            (value of x)
     *   INTEGRAL    → "x**3/3"
     *   LIMIT       → "1"
     */
    @Column(name = "expected_sympy", nullable = false, length = 500)
    private String expectedSympy;

    /**
     * Optional context string passed to Sympy for disambiguation.
     * Examples :
     *   "variable: x"
     *   "variable: x, lower: 0, upper: 1, definite: true"
     *   "variable: x, point: 0"
     * FastAPI parses this string to configure the Sympy call.
     */
    @Column(name = "sympy_context", length = 200)
    private String sympyContext;

    /**
     * 1 = easy, 2 = medium, 3 = hard.
     * Used by the recommendation engine to adapt to the student's level.
     */
    @Column(name = "difficulty_level", nullable = false)
    private int difficultyLevel;

    public KnowledgeNode getKnowledgeNode() {
        return knowledgeNode;
    }

    public void setKnowledgeNode(KnowledgeNode knowledgeNode) {
        this.knowledgeNode = knowledgeNode;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public String getQuestionDisplay() {
        return questionDisplay;
    }

    public void setQuestionDisplay(String questionDisplay) {
        this.questionDisplay = questionDisplay;
    }

    public String getExpectedSympy() {
        return expectedSympy;
    }

    public void setExpectedSympy(String expectedSympy) {
        this.expectedSympy = expectedSympy;
    }

    public String getSympyContext() {
        return sympyContext;
    }

    public void setSympyContext(String sympyContext) {
        this.sympyContext = sympyContext;
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }
}