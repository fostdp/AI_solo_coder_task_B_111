package com.heritage.bridge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MasonryDemResultDTO {

    private Long bridgeId;
    private String analysisType;
    private Integer elementCount;
    private Integer contactCount;
    private Double maxContactForce;
    private Double avgContactForce;
    private List<Map<String, Object>> forceChainData;
    private List<Map<String, Object>> stoneDisplacements;
    private List<Map<String, Object>> jointStresses;
    private Double structuralIntegrityIndex;
    private Double loadTransferEfficiency;
    private String mortarType;
    private String stoneArrangement;
    private String recommendation;
    private LocalDateTime calculatedAt;
}
