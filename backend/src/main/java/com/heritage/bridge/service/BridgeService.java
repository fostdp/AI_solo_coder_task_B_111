package com.heritage.bridge.service;

import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.repository.BridgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BridgeService {

    private final BridgeRepository bridgeRepository;

    public List<Bridge> findAll() {
        return bridgeRepository.findAll();
    }

    public Bridge findById(Long id) {
        return bridgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("桥梁不存在: " + id));
    }

    @Transactional
    public Bridge save(Bridge bridge) {
        return bridgeRepository.save(bridge);
    }

    @Transactional
    public Bridge update(Long id, Bridge data) {
        Bridge existing = findById(id);
        existing.setName(data.getName());
        existing.setLocation(data.getLocation());
        existing.setBuiltYear(data.getBuiltYear());
        existing.setSpanLength(data.getSpanLength());
        existing.setRiseSpanRatio(data.getRiseSpanRatio());
        existing.setPierThickness(data.getPierThickness());
        existing.setArchCount(data.getArchCount());
        existing.setStoneModulus(data.getStoneModulus());
        existing.setStonePoisson(data.getStonePoisson());
        existing.setStoneStrength(data.getStoneStrength());
        existing.setHealthScore(data.getHealthScore());
        existing.setStatus(data.getStatus());
        return bridgeRepository.save(existing);
    }

    public List<Bridge> findByStatus(String status) {
        return bridgeRepository.findByStatus(status);
    }

    public List<Bridge> findLowHealth(int threshold) {
        return bridgeRepository.findByHealthScoreLessThan(threshold);
    }

    @Transactional
    public void delete(Long id) {
        bridgeRepository.deleteById(id);
    }
}
