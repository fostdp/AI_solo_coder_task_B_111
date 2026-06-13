package com.heritage.bridge.repository;

import com.heritage.bridge.entity.TrafficVibrationAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficVibrationAnalysisRepository extends JpaRepository<TrafficVibrationAnalysis, Long> {

    List<TrafficVibrationAnalysis> findByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);

    List<TrafficVibrationAnalysis> findByBridgeIdAndVehicleTypeOrderByCalculatedAtDesc(Long bridgeId, String vehicleType);

    Optional<TrafficVibrationAnalysis> findTopByBridgeIdAndVehicleTypeOrderByCalculatedAtDesc(Long bridgeId, String vehicleType);

    Optional<TrafficVibrationAnalysis> findTopByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);

    @Query("SELECT t FROM TrafficVibrationAnalysis t WHERE t.bridgeId = :bridgeId ORDER BY t.calculatedAt DESC LIMIT :limit")
    List<TrafficVibrationAnalysis> findLatestByBridgeId(@Param("bridgeId") Long bridgeId, @Param("limit") int limit);

    @Query("SELECT MIN(t.allowableWeightLimit), MIN(t.allowableSpeedLimit) FROM TrafficVibrationAnalysis t WHERE t.bridgeId = :bridgeId")
    List<Object[]> findMinimumLimits(@Param("bridgeId") Long bridgeId);
}
