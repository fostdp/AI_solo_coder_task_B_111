package com.heritage.bridge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class WeatheringResultDTO {

    private Long bridgeId;
    private Double hardnessCoefficient;
    private Double velocityCoefficient;
    private Double intercept;
    private Double rSquared;
    private List<WeatheringPoint> points;
    private Map<String, Integer> gradeDistribution;
    private Double avgDepth;
    private Double maxDepth;
    private String overallGrade;
    private String recommendation;
    private LocalDateTime calculatedAt;

    private DataQualityReport dataQualityReport;
    private Integer totalPoints;
    private Integer validPoints;
    private Integer rejectedPoints;
    private Double dataPassRate;

    @Data
    @Builder
    public static class WeatheringPoint {
        private Long id;
        private String location;
        private Double locX;
        private Double locY;
        private Double locZ;
        private Double surfaceHardness;
        private Double ultrasonicVelocity;
        private Double estimatedDepth;
        private String weatheringGrade;
        private Double couplingQualityIndex;
        private Boolean isOutlier;
        private String rejectReason;
    }

    @Data
    @Builder
    public static class DataQualityReport {
        private Integer totalReceived;
        private Integer iqrRejected;
        private Integer couplingRejected;
        private Integer duplicateRejected;
        private Double passRate;
        private Double avgCouplingQuality;
        private Double hardnessStdDev;
        private Double velocityStdDev;
        private String overallQuality;
        private String qualityAdvice;
    }
}
