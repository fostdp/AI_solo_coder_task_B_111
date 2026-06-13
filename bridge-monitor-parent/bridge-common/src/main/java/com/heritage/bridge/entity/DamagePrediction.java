package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "damage_prediction")
public class DamagePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "crack_sensor_id", nullable = false)
    private Long crackSensorId;

    @Column(name = "initial_length", nullable = false, precision = 12, scale = 6)
    private BigDecimal initialLength;

    @Column(name = "paris_c", nullable = false, precision = 18, scale = 12)
    private BigDecimal parisC;

    @Column(name = "paris_m", nullable = false, precision = 10, scale = 4)
    private BigDecimal parisM;

    @Column(name = "paris_c_posterior_mean", precision = 18, scale = 12)
    private BigDecimal parisCPosteriorMean;

    @Column(name = "paris_c_posterior_std", precision = 18, scale = 12)
    private BigDecimal parisCPosteriorStd;

    @Column(name = "paris_m_posterior_mean", precision = 10, scale = 6)
    private BigDecimal parisMPosteriorMean;

    @Column(name = "paris_m_posterior_std", precision = 10, scale = 6)
    private BigDecimal parisMPosteriorStd;

    @Column(name = "mcmc_samples")
    private Integer mcmcSamples;

    @Column(name = "is_bayesian")
    private Boolean isBayesian;

    @Column(name = "prediction_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<YearPrediction> predictionData;

    @Column(name = "maintenance_year")
    private Integer maintenanceYear;

    @Column(columnDefinition = "text")
    private String recommendation;

    @CreationTimestamp
    @Column(name = "predicted_at", updatable = false)
    private LocalDateTime predictedAt;

    @Data
    @Embeddable
    public static class YearPrediction {
        private Integer year;
        private BigDecimal length;
        private String risk;
    }
}
