package com.heritage.bridge.dto;

import lombok.Data;

import java.util.List;

@Data
public class TrafficVibrationRequestDTO {

    private Long bridgeId;
    private Double naturalFrequency;
    private Double dampingRatio;
    private Double bridgeMass;
    private Double spanStiffness;
    private List<VehicleLoad> vehicleLoads;
    private PavementParams pavement;

    @Data
    public static class PavementParams {
        private Double thickness;
        private Double dampingRatio;
        private Double youngsModulus;
        private Double density;
        private String materialType;
    }

    @Data
    public static class VehicleLoad {
        private String vehicleType;
        private Double vehicleWeight;
        private Double vehicleSpeed;
        private Integer count;
    }
}
