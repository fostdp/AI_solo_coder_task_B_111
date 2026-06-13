package com.heritage.bridge.dto;

import lombok.Data;

import java.util.List;

@Data
public class WeatheringRequestDTO {

    private Long bridgeId;
    private Double hardnessCoefficient;
    private Double velocityCoefficient;
    private Double intercept;
    private List<MeasurementPoint> measurements;

    @Data
    public static class MeasurementPoint {
        private String location;
        private Double locX;
        private Double locY;
        private Double locZ;
        private Double surfaceHardness;
        private Double ultrasonicVelocity;
    }
}
