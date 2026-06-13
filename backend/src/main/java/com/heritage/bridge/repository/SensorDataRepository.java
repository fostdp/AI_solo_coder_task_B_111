package com.heritage.bridge.repository;

import com.heritage.bridge.entity.SensorData;
import com.heritage.bridge.entity.SensorDataId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, SensorDataId> {

    List<SensorData> findBySensorIdAndTimestampBetweenOrderByTimestampAsc(
            Long sensorId, LocalDateTime startTime, LocalDateTime endTime);

    List<SensorData> findByBridgeIdAndSensorIdAndTimestampBetweenOrderByTimestampAsc(
            Long bridgeId, Long sensorId, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT sd FROM SensorData sd WHERE sd.bridgeId = :bridgeId " +
           "AND sd.timestamp >= :startTime ORDER BY sd.timestamp DESC")
    List<SensorData> findLatestByBridgeId(
            @Param("bridgeId") Long bridgeId,
            @Param("startTime") LocalDateTime startTime);

    Optional<SensorData> findFirstBySensorIdOrderByTimestampDesc(Long sensorId);

    @Query("SELECT sd FROM SensorData sd WHERE sd.sensorId = :sensorId " +
           "AND sd.timestamp >= :startTime ORDER BY sd.timestamp ASC")
    List<SensorData> findTrendDataBySensorId(
            @Param("sensorId") Long sensorId,
            @Param("startTime") LocalDateTime startTime);

    @Query(value = "SELECT * FROM sensor_data_daily " +
                   "WHERE sensor_id = :sensorId " +
                   "AND bucket >= :startTime ORDER BY bucket ASC",
           nativeQuery = true)
    List<Object[]> findDailyTrendDataBySensorId(
            @Param("sensorId") Long sensorId,
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(DISTINCT sd.sensorId) FROM SensorData sd " +
           "WHERE sd.bridgeId = :bridgeId AND sd.value > :threshold")
    Long countSensorsExceedingThreshold(
            @Param("bridgeId") Long bridgeId,
            @Param("threshold") java.math.BigDecimal threshold);
}
