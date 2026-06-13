package com.heritage.bridge.repository;

import com.heritage.bridge.entity.BridgePriorityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BridgePriorityResultRepository extends JpaRepository<BridgePriorityResult, Long> {

    List<BridgePriorityResult> findAllByOrderByRankingAsc();

    List<BridgePriorityResult> findByPriorityLevelOrderByRankingAsc(String priorityLevel);

    List<BridgePriorityResult> findByMaintenanceUrgencyOrderByRankingAsc(String maintenanceUrgency);

    Optional<BridgePriorityResult> findByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);

    @Query("SELECT p FROM BridgePriorityResult p WHERE p.calculatedAt = (SELECT MAX(p2.calculatedAt) FROM BridgePriorityResult p2) ORDER BY p.ranking ASC")
    List<BridgePriorityResult> findLatestResults();

    @Query("SELECT p.priorityLevel, COUNT(p) FROM BridgePriorityResult p WHERE p.calculatedAt = (SELECT MAX(p2.calculatedAt) FROM BridgePriorityResult p2) GROUP BY p.priorityLevel")
    List<Object[]> countByPriorityLevel();

    @Query("SELECT p FROM BridgePriorityResult p WHERE p.bridgeId = :bridgeId ORDER BY p.calculatedAt DESC LIMIT :limit")
    List<BridgePriorityResult> findHistoryByBridgeId(@Param("bridgeId") Long bridgeId, @Param("limit") int limit);
}
