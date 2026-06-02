package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CourseSummaryDto {
    @JsonProperty("node_code")
    private String nodeCode;

    @JsonProperty("node_title")
    private String nodeTitle;

    private String intro;

    @JsonProperty("difficulty_level")
    private int difficultyLevel;

    @JsonProperty("estimated_minutes")
    private int estimatedMinutes;

    public CourseSummaryDto(String nodeCode, String nodeTitle, String intro,
                             int difficultyLevel, int estimatedMinutes) {
        this.nodeCode = nodeCode;
        this.nodeTitle = nodeTitle;
        this.intro = intro;
        this.difficultyLevel = difficultyLevel;
        this.estimatedMinutes = estimatedMinutes;
    }

    public String getNodeCode() { return nodeCode; }
    public String getNodeTitle() { return nodeTitle; }
    public String getIntro() { return intro; }
    public int getDifficultyLevel() { return difficultyLevel; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
}
