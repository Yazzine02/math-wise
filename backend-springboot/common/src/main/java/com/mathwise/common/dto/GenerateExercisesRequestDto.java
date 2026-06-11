package com.mathwise.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spring Boot → FastAPI request for {@code POST /generate-exercises}.
 * Snake-case field names on the wire match the Pydantic model on the Python side.
 */
public class GenerateExercisesRequestDto {

    @JsonProperty("node_code")
    private String nodeCode;

    private int count;

    public GenerateExercisesRequestDto() {}

    public GenerateExercisesRequestDto(String nodeCode, int count) {
        this.nodeCode = nodeCode;
        this.count = count;
    }

    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
