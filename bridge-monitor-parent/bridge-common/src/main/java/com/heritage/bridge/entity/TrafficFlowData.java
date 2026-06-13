package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "traffic_flow_data")
public class TrafficFlowData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "hour_of_day", nullable = false)
    private Integer hourOfDay;

    @Column(name = "vehicle_type", nullable = false, length = 30)
    private String vehicleType;

    @Column(name = "vehicle_count", nullable = false)
    private Integer vehicleCount;

    @Column(name = "avg_speed")
    private Double avgSpeed;

    @Column(name = "avg_weight")
    private Double avgWeight;

    @Column(name = "total_weight")
    private Double totalWeight;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
        if (source == null) {
            source = "simulated";
        }
        if (totalWeight == null && avgWeight != null && vehicleCount != null) {
            totalWeight = avgWeight * vehicleCount;
        }
    }
}
