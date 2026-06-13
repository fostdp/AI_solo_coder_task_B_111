package com.heritage.bridge.repository;

import com.heritage.bridge.entity.MasonryParams;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasonryParamsRepository extends JpaRepository<MasonryParams, Long> {

    List<MasonryParams> findByBridgeIdOrderByMeasuredAtDesc(Long bridgeId);

    Optional<MasonryParams> findTopByBridgeIdOrderByMeasuredAtDesc(Long bridgeId);
}
