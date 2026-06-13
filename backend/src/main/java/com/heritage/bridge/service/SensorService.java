package com.heritage.bridge.service;

import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRepository sensorRepository;

    public List<Sensor> findByBridgeId(Long bridgeId) {
        return sensorRepository.findByBridgeId(bridgeId);
    }

    public List<Sensor> findByBridgeIdAndType(Long bridgeId, String type) {
        return sensorRepository.findByBridgeIdAndType(bridgeId, type);
    }

    public Sensor findById(Long id) {
        return sensorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("传感器不存在: " + id));
    }

    public Sensor findByCode(String code) {
        return sensorRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("传感器编码不存在: " + code));
    }

    @Transactional
    public Sensor save(Sensor sensor) {
        return sensorRepository.save(sensor);
    }

    @Transactional
    public void delete(Long id) {
        sensorRepository.deleteById(id);
    }
}
