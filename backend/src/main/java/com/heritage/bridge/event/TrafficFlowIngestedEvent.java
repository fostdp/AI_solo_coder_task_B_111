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
public class TrafficFlowIngestedEvent implements Serializable {

    private Long bridgeId;
    private String vehicleType;
    private Integer vehicleCount;
    private Double totalWeight;
    private LocalDateTime ingestedAt;
}
