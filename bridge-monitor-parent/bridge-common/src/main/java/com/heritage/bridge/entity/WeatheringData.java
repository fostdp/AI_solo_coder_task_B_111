package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "weathering_data")
public class WeatheringData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "sensor_id")
    private Long sensorId;

    @Column(name = "location", nullable = false, length = 200)
    private String location;

    @Column(name = "loc_x", nullable = false)
    private Double locX;

    @Column(name = "loc_y", nullable = false)
    private Double locY;

    @Column(name = "loc_z", nullable = false)
    private Double locZ;

    @Column(name = "surface_hardness", nullable = false)
    private Double surfaceHardness;

    @Column(name = "ultrasonic_velocity", nullable = false)
    private Double ultrasonicVelocity;

    @Column(name = "estimated_depth", nullable = false)
    private Double estimatedDepth;

    @Column(name = "weathering_grade", nullable = false, length = 20)
    private String weatheringGrade;

    @Column(name = "regression_r_squared")
    private Double regressionRSquared;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "measurement_data", columnDefinition = "jsonb")
    private Map<String, Object> measurementData;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @PrePersist
    protected void onCreate() {
        if (measuredAt == null) {
            measuredAt = LocalDateTime.now();
        }
    }
}
