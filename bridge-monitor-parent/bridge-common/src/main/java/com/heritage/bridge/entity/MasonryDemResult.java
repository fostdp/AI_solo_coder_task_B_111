package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "masonry_dem_result")
public class MasonryDemResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "analysis_type", nullable = false, length = 50)
    private String analysisType;

    @Column(name = "element_count", nullable = false)
    private Integer elementCount;

    @Column(name = "contact_count", nullable = false)
    private Integer contactCount;

    @Column(name = "max_contact_force", nullable = false)
    private Double maxContactForce;

    @Column(name = "avg_contact_force", nullable = false)
    private Double avgContactForce;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "force_chain_data", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> forceChainData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stone_displacements", columnDefinition = "jsonb")
    private List<Map<String, Object>> stoneDisplacements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "joint_stresses", columnDefinition = "jsonb")
    private List<Map<String, Object>> jointStresses;

    @Column(name = "structural_integrity_index", nullable = false)
    private Double structuralIntegrityIndex;

    @Column(name = "load_transfer_efficiency", nullable = false)
    private Double loadTransferEfficiency;

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
