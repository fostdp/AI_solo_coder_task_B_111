package com.heritage.bridge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FemRequestDTO {

    @NotNull(message = "桥梁ID不能为空")
    private Long bridgeId;

    private String loadType = "static";

    private BigDecimal customLoad;

    private BigDecimal temperatureDelta;

    private Integer elementCount;

    private Integer monteCarloSamples;

    private BigDecimal modulusCov;

    private BigDecimal strengthCov;

    private Boolean enableStochastic;
}
