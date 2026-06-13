package com.heritage.bridge.repository;

import com.heritage.bridge.entity.TrafficFlowData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficFlowDataRepository extends JpaRepository<TrafficFlowData, Long> {

    List<TrafficFlowData> findByBridgeIdAndRecordDateOrderByHourOfDay(Long bridgeId, LocalDate recordDate);

    List<TrafficFlowData> findByBridgeIdAndVehicleTypeOrderByRecordDateDesc(Long bridgeId, String vehicleType);

    Optional<TrafficFlowData> findTopByBridgeIdOrderByRecordedAtDesc(Long bridgeId);

    @Query("SELECT t FROM TrafficFlowData t WHERE t.bridgeId = :bridgeId AND t.recordDate BETWEEN :startDate AND :endDate ORDER BY t.recordDate DESC, t.hourOfDay")
    List<TrafficFlowData> findByBridgeIdAndDateRange(
            @Param("bridgeId") Long bridgeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT t.vehicleType, SUM(t.vehicleCount), SUM(t.totalWeight) FROM TrafficFlowData t WHERE t.bridgeId = :bridgeId AND t.recordDate BETWEEN :startDate AND :endDate GROUP BY t.vehicleType")
    List<Object[]> summarizeTrafficByType(
            @Param("bridgeId") Long bridgeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT AVG(t.avgWeight), AVG(t.avgSpeed) FROM TrafficFlowData t WHERE t.bridgeId = :bridgeId AND t.vehicleType = :vehicleType")
    List<Object[]> findAverageWeightAndSpeedByType(
            @Param("bridgeId") Long bridgeId,
            @Param("vehicleType") String vehicleType);
}
