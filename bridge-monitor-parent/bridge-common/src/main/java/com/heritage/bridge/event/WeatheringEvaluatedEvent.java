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
public class WeatheringEvaluatedEvent implements Serializable {

    private Long bridgeId;
    private Double avgDepth;
    private Double maxDepth;
    private String overallGrade;
    private LocalDateTime evaluatedAt;
    private Trigger trigger;

    public enum Trigger {
        ON_DEMAND,
        SCHEDULED_DAILY,
        HARDNESS_DATA_TRIGGERED,
        ULTRASONIC_DATA_TRIGGERED,
        DATA_TRIGGERED_BATCH
    }
}
