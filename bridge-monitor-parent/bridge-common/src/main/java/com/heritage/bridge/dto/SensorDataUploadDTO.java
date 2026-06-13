package com.heritage.bridge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SensorDataUploadDTO {

    @NotNull(message = "传感器编码不能为空")
    private String sensorCode;

    private Long bridgeId;

    @NotNull(message = "监测值不能为空")
    private BigDecimal value;

    private BigDecimal temperature;

    private LocalDateTime timestamp;
}
