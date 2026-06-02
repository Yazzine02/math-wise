package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WeaknessSummaryDto {

    private List<WeaknessEntry> weaknesses;

    public WeaknessSummaryDto(List<WeaknessEntry> weaknesses) {
        this.weaknesses = weaknesses;
    }

    public List<WeaknessEntry> getWeaknesses() { return weaknesses; }
    public void setWeaknesses(List<WeaknessEntry> weaknesses) { this.weaknesses = weaknesses; }

    public static class WeaknessEntry {
        @JsonProperty("node_code")
        private String nodeCode;

        @JsonProperty("node_title")
        private String nodeTitle;

        @JsonProperty("failure_count")
        private long failureCount;

        public WeaknessEntry(String nodeCode, String nodeTitle, long failureCount) {
            this.nodeCode = nodeCode;
            this.nodeTitle = nodeTitle;
            this.failureCount = failureCount;
        }

        public String getNodeCode() { return nodeCode; }
        public String getNodeTitle() { return nodeTitle; }
        public long getFailureCount() { return failureCount; }
    }
}
