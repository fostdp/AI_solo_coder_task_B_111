package com.heritage.bridge.repository;

import com.heritage.bridge.entity.WeatheringData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatheringDataRepository extends JpaRepository<WeatheringData, Long> {

    List<WeatheringData> findByBridgeIdOrderByMeasuredAtDesc(Long bridgeId);

    List<WeatheringData> findByBridgeIdAndWeatheringGradeOrderByMeasuredAtDesc(Long bridgeId, String weatheringGrade);

    Optional<WeatheringData> findTopByBridgeIdOrderByMeasuredAtDesc(Long bridgeId);

    @Query("SELECT w FROM WeatheringData w WHERE w.bridgeId = :bridgeId AND w.measuredAt BETWEEN :startTime AND :endTime ORDER BY w.measuredAt DESC")
    List<WeatheringData> findByBridgeIdAndTimeRange(
            @Param("bridgeId") Long bridgeId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT AVG(w.estimatedDepth) FROM WeatheringData w WHERE w.bridgeId = :bridgeId")
    Double findAverageDepthByBridgeId(@Param("bridgeId") Long bridgeId);

    @Query("SELECT MAX(w.estimatedDepth) FROM WeatheringData w WHERE w.bridgeId = :bridgeId")
    Double findMaxDepthByBridgeId(@Param("bridgeId") Long bridgeId);

    @Query("SELECT w.weatheringGrade, COUNT(w) FROM WeatheringData w WHERE w.bridgeId = :bridgeId GROUP BY w.weatheringGrade")
    List<Object[]> countByGradeForBridge(@Param("bridgeId") Long bridgeId);

    @Query("SELECT w FROM WeatheringData w WHERE w.bridgeId = :bridgeId ORDER BY w.measuredAt DESC LIMIT 20")
    List<WeatheringData> findLatestByBridgeId(@Param("bridgeId") Long bridgeId);
}
