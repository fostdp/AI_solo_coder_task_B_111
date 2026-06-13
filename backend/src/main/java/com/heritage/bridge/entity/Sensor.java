package com.heritage.bridge.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sensor", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bridge_id", "code"})
})
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bridge_id", nullable = false)
    private Long bridgeId;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "loc_x", nullable = false, precision = 12, scale = 6)
    private BigDecimal locX;

    @Column(name = "loc_y", nullable = false, precision = 12, scale = 6)
    private BigDecimal locY;

    @Column(name = "loc_z", nullable = false, precision = 12, scale = 6)
    private BigDecimal locZ;

    @Column(length = 200)
    private String position;

    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal threshold;

    @Column(nullable = false, length = 20)
       private String unit;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
