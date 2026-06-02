package com.mathwise.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name="interaction_logs")
// This is an append-only ledger for every AI evaluation
public class InteractionLog extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="tested_node_id", nullable = false)
    private KnowledgeNode testedNode;

    @Column(nullable = false, length = 500)
    private String originalEquation;

    @Column(nullable = false)
    private String studentInput;

    @Column(nullable = false)
    private boolean isCorrect;

    // If incorrect, this stores the AI's diagnosis
    @Column(name = "ai_identified_weakness_code")
    private String aiIdentifiedWeaknessCode;

    @Column(length = 1000)
    private String aiExplanation;

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public KnowledgeNode getTestedNode() {
        return testedNode;
    }

    public void setTestedNode(KnowledgeNode testedNode) {
        this.testedNode = testedNode;
    }

    public String getOriginalEquation() {
        return originalEquation;
    }

    public void setOriginalEquation(String originalEquation) {
        this.originalEquation = originalEquation;
    }

    public String getStudentInput() {
        return studentInput;
    }

    public void setStudentInput(String studentInput) {
        this.studentInput = studentInput;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }

    public String getAiIdentifiedWeaknessCode() {
        return aiIdentifiedWeaknessCode;
    }

    public void setAiIdentifiedWeaknessCode(String aiIdentifiedWeaknessCode) {
        this.aiIdentifiedWeaknessCode = aiIdentifiedWeaknessCode;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }
}
