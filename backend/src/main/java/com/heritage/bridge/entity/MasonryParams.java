package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "masonry_params")
public class MasonryParams {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "stone_shape", nullable = false, length = 50)
    private String stoneShape;

    @Column(name = "stone_arrangement", nullable = false, length = 50)
    private String stoneArrangement;

    @Column(name = "mortar_type", nullable = false, length = 50)
    private String mortarType;

    @Column(name = "mortar_compressive_strength", nullable = false)
    private Double mortarCompressiveStrength;

    @Column(name = "mortar_tensile_strength", nullable = false)
    private Double mortarTensileStrength;

    @Column(name = "joint_thickness", nullable = false)
    private Double jointThickness;

    @Column(name = "stone_friction_coefficient", nullable = false)
    private Double stoneFrictionCoefficient;

    @Column(name = "cohesion")
    private Double cohesion;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @PrePersist
    protected void onCreate() {
        if (measuredAt == null) {
            measuredAt = LocalDateTime.now();
        }
    }
}
