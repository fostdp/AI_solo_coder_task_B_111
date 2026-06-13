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
