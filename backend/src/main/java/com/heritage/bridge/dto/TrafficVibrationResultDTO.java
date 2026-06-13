package com.heritage.bridge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TrafficVibrationResultDTO {

    private Long bridgeId;
    private List<VehicleAnalysis> analyses;
    private Double allowableWeightLimit;
    private Double allowableSpeedLimit;
    private String overallSafetyLevel;
    private String recommendation;
    private LocalDateTime calculatedAt;

    @Data
    @Builder
    public static class VehicleAnalysis {
        private Long id;
        private String vehicleType;
        private Double vehicleWeight;
        private Double vehicleSpeed;
        private Double naturalFrequency;
        private Double dampingRatio;
        private Double maxAcceleration;
        private Double maxDynamicDisplacement;
        private Double dynamicAmplificationFactor;
        private Double safetyMargin;
        private Boolean exceedLimit;
    }
}
