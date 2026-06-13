package com.heritage.bridge.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorityCalculatedEvent implements Serializable {

    private Integer planYear;
    private Integer totalBridges;
    private Integer criticalCount;
    private Integer highPriorityCount;
    private List<String> criticalBridges;
    private LocalDateTime calculatedAt;
    private Trigger trigger;

    public enum Trigger {
        ON_DEMAND,
        SCHEDULED_MONTHLY,
        DATA_UPDATED
    }
}
