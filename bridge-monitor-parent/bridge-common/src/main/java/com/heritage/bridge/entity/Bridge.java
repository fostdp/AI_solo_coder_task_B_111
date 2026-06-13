package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bridge")
public class Bridge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(name = "built_year")
    private Integer builtYear;

    @Column(name = "span_length", nullable = false, precision = 10, scale = 2)
    private BigDecimal spanLength;

    @Column(name = "rise_span_ratio", nullable = false, precision = 8, scale = 4)
    private BigDecimal riseSpanRatio;

    @Column(name = "pier_thickness", nullable = false, precision = 8, scale = 2)
    private BigDecimal pierThickness;

    @Column(name = "arch_count", nullable = false)
    private Integer archCount;

    @Column(name = "stone_modulus", nullable = false, precision = 10, scale = 4)
    private BigDecimal stoneModulus;

    @Column(name = "stone_poisson", nullable = false, precision = 8, scale = 4)
    private BigDecimal stonePoisson;

    @Column(name = "stone_strength", nullable = false, precision = 10, scale = 4)
    private BigDecimal stoneStrength;

    @Column(name = "health_score")
    private Integer healthScore;

    @Column(length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
