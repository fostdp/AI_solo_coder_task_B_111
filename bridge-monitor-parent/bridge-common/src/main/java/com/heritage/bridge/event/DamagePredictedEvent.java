package com.heritage.bridge.event;

import com.heritage.bridge.entity.DamagePrediction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DamagePredictedEvent implements Serializable {

    private Long bridgeId;
    private Long crackSensorId;
    private DamagePrediction prediction;
    private Double predictedLengthIn5Years;
    private Double annualGrowthRate;
    private Integer maintenanceYear;
    private String recommendation;
    private String riskLevel;
    private Double parisC;
    private Double parisM;
    private Boolean bayesianCalibrated;
    private LocalDateTime predictedAt;
    private Source source;

    public enum Source {
        ON_DEMAND,
        SCHEDULED_MONTHLY,
        BAYESIAN_RECALIBRATION,
        CRACK_TRIGGERED
    }
}
