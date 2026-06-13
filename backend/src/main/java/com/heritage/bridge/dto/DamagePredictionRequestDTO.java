package com.heritage.bridge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DamagePredictionRequestDTO {

    @NotNull(message = "桥梁ID不能为空")
    private Long bridgeId;

    @NotNull(message = "裂缝传感器ID不能为空")
    private Long crackSensorId;

    private BigDecimal initialLength;

    private BigDecimal parisC;

    private BigDecimal parisM;

    private Integer yearsToPredict;

    private BigDecimal stressAmplitude;

    private Integer annualCycles;

    private Boolean enableBayesian;

    private Integer mcmcSamples;

    private Integer mcmcBurnin;

    private BigDecimal priorC_mean;

    private BigDecimal priorC_std;

    private BigDecimal priorM_mean;

    private BigDecimal priorM_std;
}
