package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sensor_data")
@IdClass(SensorDataId.class)
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "sensor_id", nullable = false)
    private Long sensorId;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal value;

    @Column(precision = 8, scale = 2)
    private BigDecimal temperature;
}
