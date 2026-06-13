package com.heritage.bridge.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PriorityTopsisRequestDTO {

    private Integer planYear;
    private Map<String, Double> weights;
    private Boolean generateProtectionPlan;
}
