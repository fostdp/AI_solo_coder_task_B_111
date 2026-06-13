package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "bridge_priority_result")
public class BridgePriorityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    @Column(name = "topsis_score", nullable = false)
    private Double topsisScore;

    @Column(name = "structure_safety_score", nullable = false)
    private Double structureSafetyScore;

    @Column(name = "damage_trend_score", nullable = false)
    private Double damageTrendScore;

    @Column(name = "weathering_score", nullable = false)
    private Double weatheringScore;

    @Column(name = "traffic_impact_score", nullable = false)
    private Double trafficImpactScore;

    @Column(name = "historical_value_score", nullable = false)
    private Double historicalValueScore;

    @Column(name = "maintenance_urgency", nullable = false, length = 20)
    private String maintenanceUrgency;

    @Column(name = "estimated_cost")
    private Double estimatedCost;

    @Column(name = "priority_level", nullable = false, length = 20)
    private String priorityLevel;

    @Column(name = "action_recommendation", columnDefinition = "TEXT")
    private String actionRecommendation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weights", columnDefinition = "jsonb")
    private Map<String, Double> weights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria_data", columnDefinition = "jsonb")
    private Map<String, Object> criteriaData;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }
}
