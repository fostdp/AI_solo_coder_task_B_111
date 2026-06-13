package com.heritage.bridge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendDataDTO {
    private LocalDateTime timestamp;
    private BigDecimal value;
    private BigDecimal avgValue;
}
