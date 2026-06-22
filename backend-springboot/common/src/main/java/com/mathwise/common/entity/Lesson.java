package com.mathwise.common.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/*
 A Lesson is the educational content associated with a KnowledgeNode.
 KnowledgeNode represents the structural concept (with prerequisites, difficulty).
 Lesson represents the human-readable course material (theory, examples, tips).
 Kept as a separate entity so future iterations can support multiple lessons
 per concept, versioning, or alternate explanations.
*/
@Entity
@Table(name = "lessons")
public class Lesson extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_node_id", nullable = false, unique = true)
    private KnowledgeNode knowledgeNode;

    @Column(name = "intro", nullable = false, length = 500)
    private String intro;

    @Column(name = "theory", nullable = false, length = 2000)
    private String theory;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "lesson_examples",
            joinColumns = @JoinColumn(name = "lesson_id")
    )
    @Column(name = "example_text", length = 1000)
    @OrderColumn(name = "example_order")
    private List<String> examples = new ArrayList<>();

    @Column(name = "tip", nullable = false, length = 1000)
    private String tip;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    public KnowledgeNode getKnowledgeNode() { return knowledgeNode; }
    public void setKnowledgeNode(KnowledgeNode knowledgeNode) { this.knowledgeNode = knowledgeNode; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public String getTheory() { return theory; }
    public void setTheory(String theory) { this.theory = theory; }

    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }

    public String getTip() { return tip; }
    public void setTip(String tip) { this.tip = tip; }

    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
}
