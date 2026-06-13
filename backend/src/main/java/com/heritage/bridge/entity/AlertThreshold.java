package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alert_threshold")
public class AlertThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false, length = 30, unique = true)
    private String alertType;

    @Column(name = "warning_value", nullable = false, precision = 18, scale = 8)
    private BigDecimal warningValue;

    @Column(name = "danger_value", nullable = false, precision = 18, scale = 8)
    private BigDecimal dangerValue;

    @Column(columnDefinition = "text")
    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
