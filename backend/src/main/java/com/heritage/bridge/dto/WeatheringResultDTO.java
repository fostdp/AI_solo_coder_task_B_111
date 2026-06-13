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
    }
}
