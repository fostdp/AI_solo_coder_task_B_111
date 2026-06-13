package com.heritage.bridge.repository;

import com.heritage.bridge.entity.AlertThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertThresholdRepository extends JpaRepository<AlertThreshold, Long> {
    Optional<AlertThreshold> findByAlertType(String alertType);
}
