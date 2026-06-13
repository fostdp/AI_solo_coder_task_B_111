package com.heritage.bridge.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PriorityTopsisRequestDTO {

    private Integer planYear;
    private Map<String, Double> weights;
    private Boolean generateProtectionPlan;

    private Boolean enableDelphiMethod;
    private Boolean enableSensitivityAnalysis;
    private Boolean useGroupDecision;
    private List<ExpertRating> expertRatings;
    private Integer expertCount;
    private Double expertWeightInfluence;

    @Data
    public static class ExpertRating {
        private String expertName;
        private String expertTitle;
        private Double expertWeight;
        private Map<String, Double> criteriaWeights;
        private Map<String, Double> bridgeScores;
        private String comments;
    }
}
