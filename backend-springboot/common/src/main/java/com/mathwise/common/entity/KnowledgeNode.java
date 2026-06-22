package com.mathwise.common.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_nodes")
public class KnowledgeNode extends BaseEntity{
    @Column(name = "node_code", nullable = false, unique = true)
    private String nodeCode;
    @Column(nullable = false)
    private String title;
    // Optimization by using LAZY fetch method
    // a node can point to multiple other nodes (e.g solving equation requires + - x ...)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="prerequisite_node_id")
    private KnowledgeNode prerequisiteNode;

    @Column(nullable = false)
    private int difficultyLevel;

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public KnowledgeNode getPrerequisiteNode() {
        return prerequisiteNode;
    }

    public void setPrerequisiteNode(KnowledgeNode prerequisiteNode) {
        this.prerequisiteNode = prerequisiteNode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }
}
