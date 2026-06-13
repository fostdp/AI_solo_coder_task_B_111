package com.heritage.bridge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PriorityTopsisResultDTO {

    private Integer planYear;
    private List<BridgePriority> rankings;
    private Map<String, Double> weights;
    private List<AnnualPlanItem> protectionPlan;
    private LocalDateTime calculatedAt;

    private Boolean delphiMethodUsed;
    private Integer expertCount;
    private Double expertConsensusCoefficient;
    private Map<String, Double> expertAggregatedWeights;
    private List<ExpertResult> expertResults;
    private Boolean sensitivityAnalysisPerformed;
    private Double rankingStabilityIndex;
    private Map<String, Double> criteriaSensitivity;
    private List<SensitivityResult> sensitivityResults;
    private Boolean groupDecisionUsed;
    private GroupDecisionReport groupDecisionReport;

    @Data
    @Builder
    public static class ExpertResult {
        private String expertName;
        private String expertTitle;
        private Double expertWeight;
        private Map<String, Double> criteriaWeights;
        private List<Integer> bridgeRankings;
        private Double rankingAgreement;
        private String comments;
    }

    @Data
    @Builder
    public static class SensitivityResult {
        private String criteriaName;
        private Double weightPerturbation;
        private List<Integer> originalRankings;
        private List<Integer> perturbedRankings;
        private Double rankingCorrelation;
        private Double stabilityScore;
    }

    @Data
    @Builder
    public static class GroupDecisionReport {
        private Integer totalExperts;
        private Double consensusLevel;
        private String consensusInterpretation;
        private String finalRecommendation;
        private String minorityOpinions;
    }

    @Data
    @Builder
    public static class BridgePriority {
        private Long id;
        private Long bridgeId;
        private String bridgeName;
        private Integer ranking;
        private Double topsisScore;
        private Double structureSafetyScore;
        private Double damageTrendScore;
        private Double weatheringScore;
        private Double trafficImpactScore;
        private Double historicalValueScore;
        private String maintenanceUrgency;
        private Double estimatedCost;
        private String priorityLevel;
        private String actionRecommendation;
    }

    @Data
    @Builder
    public static class AnnualPlanItem {
        private Long id;
        private Integer planYear;
        private Long bridgeId;
        private String bridgeName;
        private Integer priorityRanking;
        private String projectName;
        private String projectType;
        private Double estimatedBudget;
        private String timeline;
        private String status;
        private String description;
    }
}
