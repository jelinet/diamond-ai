package com.showengine.router.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.showengine.router.enums.IntentSourceEnum;
import com.showengine.router.enums.IntentTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentLogEntry {
    private String timestamp;
    private String query;
    private IntentLogDecision rule;
    private IntentLogDecision model;
    private String llm;
    @JsonProperty("final")
    private IntentLogFinalDecision finalDecision;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentLogDecision {
        private IntentTypeEnum intent;
        private double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentLogFinalDecision {
        private IntentTypeEnum intent;
        private IntentSourceEnum source;
    }
}
