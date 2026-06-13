package com.heritage.bridge.repository;

import com.heritage.bridge.entity.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {
    List<Sensor> findByBridgeId(Long bridgeId);
    List<Sensor> findByBridgeIdAndType(Long bridgeId, String type);
    Optional<Sensor> findByCode(String code);
    Optional<Sensor> findByBridgeIdAndCode(Long bridgeId, String code);
    List<Sensor> findByCodeIn(Collection<String> codes);
    List<Sensor> findByType(String type);
}
