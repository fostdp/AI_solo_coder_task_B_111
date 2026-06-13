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
public class MasonrySimulatedEvent implements Serializable {

    private Long bridgeId;
    private String analysisType;
    private Double structuralIntegrityIndex;
    private Double loadTransferEfficiency;
    private Integer elementCount;
    private Integer contactCount;
    private LocalDateTime simulatedAt;
    private Trigger trigger;

    public enum Trigger {
        ON_DEMAND,
        SCHEDULED_DAILY,
        PARAMS_UPDATED
    }
}
