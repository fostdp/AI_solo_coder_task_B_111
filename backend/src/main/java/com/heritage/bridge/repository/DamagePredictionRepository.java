package com.heritage.bridge.repository;

import com.heritage.bridge.entity.DamagePrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DamagePredictionRepository extends JpaRepository<DamagePrediction, Long> {
    List<DamagePrediction> findByBridgeIdOrderByPredictedAtDesc(Long bridgeId);
    Optional<DamagePrediction> findTopByBridgeIdOrderByPredictedAtDesc(Long bridgeId);
    Optional<DamagePrediction> findTopByBridgeIdAndCrackSensorIdOrderByPredictedAtDesc(
            Long bridgeId, Long crackSensorId);
}
