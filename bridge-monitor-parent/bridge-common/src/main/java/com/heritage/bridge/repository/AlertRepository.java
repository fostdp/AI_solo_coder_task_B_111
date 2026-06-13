package com.heritage.bridge.repository;

import com.heritage.bridge.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByBridgeIdOrderByTimestampDesc(Long bridgeId);
    List<Alert> findByLevelOrderByTimestampDesc(String level);
    List<Alert> findByAcknowledgedOrderByTimestampDesc(Boolean acknowledged);
    List<Alert> findByTimestampAfterOrderByTimestampDesc(LocalDateTime timestamp);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.bridgeId = :bridgeId " +
           "AND a.acknowledged = false")
    Long countUnacknowledgedByBridgeId(@Param("bridgeId") Long bridgeId);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.acknowledged = false")
    Long countAllUnacknowledged();

    @Modifying
    @Transactional
    @Query("UPDATE Alert a SET a.acknowledged = true, a.acknowledgedAt = :acknowledgedAt, " +
           "a.acknowledgedBy = :acknowledgedBy WHERE a.id = :id")
    int acknowledgeAlert(
            @Param("id") Long id,
            @Param("acknowledgedAt") LocalDateTime acknowledgedAt,
            @Param("acknowledgedBy") String acknowledgedBy);

    List<Alert> findByBridgeIdAndTypeAndTimestampAfter(
            Long bridgeId, String type, LocalDateTime timestamp);
}
