package com.heritage.bridge.repository;

import com.heritage.bridge.entity.FemResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FemResultRepository extends JpaRepository<FemResult, Long> {
    List<FemResult> findByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);
    Optional<FemResult> findTopByBridgeIdOrderByCalculatedAtDesc(Long bridgeId);
    List<FemResult> findByBridgeIdAndLoadTypeOrderByCalculatedAtDesc(Long bridgeId, String loadType);
}
