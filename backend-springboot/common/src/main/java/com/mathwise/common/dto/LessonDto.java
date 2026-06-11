package com.mathwise.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class LessonDto {
    @JsonProperty("node_code")
    private String nodeCode;

    @JsonProperty("node_title")
    private String nodeTitle;

    @JsonProperty("difficulty_level")
    private int difficultyLevel;

    @JsonProperty("estimated_minutes")
    private int estimatedMinutes;

    private String intro;
    private String theory;
    private List<String> examples;
    private String tip;

    public LessonDto(String nodeCode, String nodeTitle, int difficultyLevel, int estimatedMinutes,
                     String intro, String theory, List<String> examples, String tip) {
        this.nodeCode = nodeCode;
        this.nodeTitle = nodeTitle;
        this.difficultyLevel = difficultyLevel;
        this.estimatedMinutes = estimatedMinutes;
        this.intro = intro;
        this.theory = theory;
        this.examples = examples;
        this.tip = tip;
    }

    public String getNodeCode() { return nodeCode; }
    public String getNodeTitle() { return nodeTitle; }
    public int getDifficultyLevel() { return difficultyLevel; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public String getIntro() { return intro; }
    public String getTheory() { return theory; }
    public List<String> getExamples() { return examples; }
    public String getTip() { return tip; }
}
