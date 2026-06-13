package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "fem_result")
public class FemResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "load_type", nullable = false, length = 20)
    private String loadType;

    @Column(name = "node_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<FemNode> nodeData;

    @Column(name = "max_stress", nullable = false, precision = 18, scale = 8)
    private BigDecimal maxStress;

    @Column(name = "max_strain", nullable = false, precision = 18, scale = 8)
    private BigDecimal maxStrain;

    @Column(name = "safety_factor", nullable = false, precision = 10, scale = 4)
    private BigDecimal safetyFactor;

    @Column(name = "mc_samples")
    private Integer mcSamples;

    @Column(name = "stress_p95", precision = 18, scale = 8)
    private BigDecimal stressP95;

    @Column(name = "stress_p99", precision = 18, scale = 8)
    private BigDecimal stressP99;

    @Column(name = "pf_failure", precision = 12, scale = 10)
    private BigDecimal pfFailure;

    @Column(name = "modulus_cov", precision = 8, scale = 4)
    private BigDecimal modulusCov;

    @Column(name = "is_stochastic")
    private Boolean isStochastic;

    @CreationTimestamp
    @Column(name = "calculated_at", updatable = false)
    private LocalDateTime calculatedAt;

    @Data
    @Embeddable
    public static class FemNode {
        private BigDecimal x;
        private BigDecimal y;
        private BigDecimal z;
        private BigDecimal stress;
        private BigDecimal strain;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultWrapper {
        private Long id;
        private Long bridgeId;
        private String loadType;
        private List<FemNode> nodeData;
        private BigDecimal maxStress;
        private BigDecimal maxStrain;
        private BigDecimal safetyFactor;
        private BigDecimal stressP95;
        private BigDecimal stressP99;
        private BigDecimal pfFailure;
        private BigDecimal modulusCov;
        private Boolean isStochastic;
        private Integer mcSamples;
        private LocalDateTime calculatedAt;
    }

    public static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
    }

    public static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    public static Integer intOrNull(Integer v) {
        return v == null || v == 0 ? null : v;
    }
}

