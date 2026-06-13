package com.heritage.bridge.repository;

import com.heritage.bridge.entity.MasonryDemResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasonryDemResultRepository extends JpaRepository<MasonryDemResult, Long> {

    List<MasonryDemResult> findByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);

    List<MasonryDemResult> findByBridgeIdAndAnalysisTypeOrderByCalculatedAtDesc(Long bridgeId, String analysisType);

    Optional<MasonryDemResult> findTopByBridgeIdAndAnalysisTypeOrderByCalculatedAtDesc(Long bridgeId, String analysisType);

    Optional<MasonryDemResult> findTopByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);

    @Query("SELECT m.analysisType, MAX(m.structuralIntegrityIndex), AVG(m.loadTransferEfficiency) FROM MasonryDemResult m WHERE m.bridgeId = :bridgeId GROUP BY m.analysisType")
    List<Object[]> summarizeByAnalysisType(@Param("bridgeId") Long bridgeId);
}
