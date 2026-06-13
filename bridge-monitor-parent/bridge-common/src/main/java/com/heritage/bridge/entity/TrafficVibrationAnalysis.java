package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "traffic_vibration_analysis")
public class TrafficVibrationAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "vehicle_type", nullable = false, length = 30)
    private String vehicleType;

    @Column(name = "vehicle_weight", nullable = false)
    private Double vehicleWeight;

    @Column(name = "vehicle_speed", nullable = false)
    private Double vehicleSpeed;

    @Column(name = "natural_frequency", nullable = false)
    private Double naturalFrequency;

    @Column(name = "damping_ratio", nullable = false)
    private Double dampingRatio;

    @Column(name = "max_acceleration", nullable = false)
    private Double maxAcceleration;

    @Column(name = "max_dynamic_displacement", nullable = false)
    private Double maxDynamicDisplacement;

    @Column(name = "dynamic_amplification_factor", nullable = false)
    private Double dynamicAmplificationFactor;

    @Column(name = "safety_margin", nullable = false)
    private Double safetyMargin;

    @Column(name = "allowable_weight_limit")
    private Double allowableWeightLimit;

    @Column(name = "allowable_speed_limit")
    private Double allowableSpeedLimit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vibration_response_data", columnDefinition = "jsonb")
    private Map<String, Object> vibrationResponseData;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }
}
