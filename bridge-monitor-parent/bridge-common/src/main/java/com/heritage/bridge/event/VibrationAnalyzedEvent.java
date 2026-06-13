package com.heritage.bridge.event;

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
public class VibrationAnalyzedEvent implements Serializable {

    private Long bridgeId;
    private String overallSafetyLevel;
    private Double allowableWeightLimit;
    private Double allowableSpeedLimit;
    private Double maxAcceleration;
    private Boolean hasExceed;
    private LocalDateTime analyzedAt;
    private Trigger trigger;

    public enum Trigger {
        ON_DEMAND,
        SCHEDULED_DAILY,
        TRAFFIC_DATA_TRIGGERED,
        VIBRATION_SENSOR_TRIGGERED,
        TRAFFIC_SUMMARY_TRIGGERED
    }
}
