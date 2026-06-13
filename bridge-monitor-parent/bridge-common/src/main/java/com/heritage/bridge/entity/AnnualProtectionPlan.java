package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "annual_protection_plan")
public class AnnualProtectionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_year", nullable = false)
    private Integer planYear;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(name = "priority_ranking", nullable = false)
    private Integer priorityRanking;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "project_type", nullable = false, length = 50)
    private String projectType;

    @Column(name = "estimated_budget", nullable = false)
    private Double estimatedBudget;

    @Column(name = "timeline", length = 100)
    private String timeline;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "pending";
        }
    }
}
