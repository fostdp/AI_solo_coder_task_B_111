package com.heritage.bridge.event;

import com.heritage.bridge.entity.FemResult;
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
public class FemResultEvent implements Serializable {

    private Long bridgeId;
    private FemResult femResult;
    private Double maxStress;
    private Double maxStrain;
    private Double safetyFactor;
    private Double pfFailure;
    private Double stressP95;
    private Double stressP99;
    private Boolean isStochastic;
    private LocalDateTime calculatedAt;
    private Source source;

    public enum Source {
        ON_DEMAND,
        SCHEDULED_DAILY,
        DATA_TRIGGERED,
        MODEL_RECALIBRATION
    }
}
