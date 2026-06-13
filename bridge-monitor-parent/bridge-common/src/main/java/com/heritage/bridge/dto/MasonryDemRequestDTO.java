package com.heritage.bridge.dto;

import lombok.Data;

@Data
public class MasonryDemRequestDTO {

    private Long bridgeId;
    private String analysisType;
    private Integer elementCount;
    private Double timeStep;
    private Double gravityFactor;
    private Double contactStiffness;
    private Double dampingCoefficient;
    private Double frictionCoefficient;
    private Double cohesion;
    private Double mortarCompressiveStrength;
    private Double mortarTensileStrength;
    private Double jointThickness;

    private String computationMode;
    private Boolean enableParallel;
    private Integer parallelThreads;
    private Boolean useSimplifiedContact;
    private Double neighborSearchRadius;
    private Integer maxSteps;
    private Double convergenceThreshold;
    private Long maxSimulationTimeMs;
}
