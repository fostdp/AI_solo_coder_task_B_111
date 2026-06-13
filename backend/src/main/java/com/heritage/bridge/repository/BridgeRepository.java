package com.heritage.bridge.repository;

import com.heritage.bridge.entity.Bridge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BridgeRepository extends JpaRepository<Bridge, Long> {
    List<Bridge> findByStatus(String status);
    List<Bridge> findByHealthScoreLessThan(Integer score);
}
