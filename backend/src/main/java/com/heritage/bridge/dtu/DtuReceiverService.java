package com.heritage.bridge.dtu;

import com.heritage.bridge.dto.SensorDataUploadDTO;
import com.heritage.bridge.dto.TrendDataDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.entity.SensorData;
import com.heritage.bridge.event.DataIngestedEvent;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.SensorDataRepository;
import com.heritage.bridge.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j(topic = "dtu-receiver")
@Service
@RequiredArgsConstructor
public class DtuReceiverService {

    private final SensorDataRepository dataRepository;
    private final SensorRepository sensorRepository;
    private final BridgeRepository bridgeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SensorData ingest(SensorDataUploadDTO dto) {
        Sensor sensor = resolveSensor(dto);
        SensorData sd = buildSensorData(dto, sensor);
        SensorData saved = dataRepository.save(sd);
        publishEvent(saved, sensor, DataIngestedEvent.Trigger.DTU_UPLOAD);
        return saved;
    }

    @Transactional
    public List<SensorData> batchIngest(List<SensorDataUploadDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return Collections.emptyList();
        Set<String> codes = dtos.stream()
                .map(SensorDataUploadDTO::getSensorCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Sensor> sensorMap = sensorRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(Sensor::getCode, s -> s, (a, b) -> a));

        List<SensorData> batch = new ArrayList<>(dtos.size());
        Map<Long, SensorData> latestPerSensor = new LinkedHashMap<>();

        for (SensorDataUploadDTO dto : dtos) {
            try {
                Sensor sensor = sensorMap.get(dto.getSensorCode());
                if (sensor == null) {
                    log.warn("丢弃未知传感器编码: {}", dto.getSensorCode());
                    continue;
                }
                SensorData sd = buildSensorData(dto, sensor);
                batch.add(sd);
                latestPerSensor.put(sensor.getId(), sd);
            } catch (Exception e) {
                log.warn("数据入库失败 sensor={}, err={}", dto.getSensorCode(), e.getMessage());
            }
        }

        if (batch.isEmpty()) return Collections.emptyList();
        List<SensorData> saved = dataRepository.saveAll(batch);

        for (SensorData sd : latestPerSensor.values()) {
            Sensor sensor = sensorMap.values().stream()
                    .filter(s -> s.getId().equals(sd.getSensorId()))
                    .findFirst().orElse(null);
            if (sensor != null) {
                publishEvent(sd, sensor, DataIngestedEvent.Trigger.BATCH_UPLOAD);
            }
        }
        log.info("批量入库完成: {}/{} 条成功", saved.size(), dtos.size());
        return saved;
    }

    public List<SensorData> findLatestByBridgeId(Long bridgeId) {
        LocalDateTime since = LocalDateTime.now().minusHours(2);
        List<SensorData> all = dataRepository.findLatestByBridgeId(bridgeId, since);
        Map<Long, SensorData> latest = new LinkedHashMap<>();
        for (SensorData sd : all) latest.putIfAbsent(sd.getSensorId(), sd);
        return new ArrayList<>(latest.values());
    }

    public List<TrendDataDTO> getSensorTrend(Long sensorId, int days) {
        if (sensorId == null) return Collections.emptyList();
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(365 * 5, days)));
        if (days > 365) {
            List<Object[]> raw = dataRepository.findDailyTrendDataBySensorId(sensorId, since);
            return raw.stream()
                    .map(row -> new TrendDataDTO(
                            ((java.sql.Timestamp) row[0]).toLocalDateTime(),
                            null,
                            java.math.BigDecimal.valueOf(((Number) row[1]).doubleValue())))
                    .toList();
        }
        List<SensorData> raw = dataRepository.findTrendDataBySensorId(sensorId, since);
        return raw.stream()
                .map(sd -> new TrendDataDTO(
                        sd.getTimestamp(),
                        sd.getValue(),
                        null))
                .toList();
    }

    @Scheduled(cron = "${simulation.fem.scheduled-cron:0 5 0 * * ?}")
    @Transactional
    public void dailyHealthCheck() {
        log.info("[DTU] 启动每日健康检查任务");
        List<Sensor> all = sensorRepository.findAll();
        int processed = 0;
        for (Sensor s : all) {
            dataRepository.findFirstBySensorIdOrderByTimestampDesc(s.getId()).ifPresent(latest -> {
                publishEvent(latest, s, DataIngestedEvent.Trigger.DAILY_CHECK);
            });
            processed++;
        }
        log.info("[DTU] 每日健康检查完成, 处理传感器={}", processed);
    }

    private Sensor resolveSensor(SensorDataUploadDTO dto) {
        return sensorRepository.findByCode(dto.getSensorCode())
                .orElseThrow(() -> new IllegalArgumentException("传感器编码不存在: " + dto.getSensorCode()));
    }

    private SensorData buildSensorData(SensorDataUploadDTO dto, Sensor sensor) {
        SensorData sd = new SensorData();
        sd.setSensorId(sensor.getId());
        sd.setBridgeId(dto.getBridgeId() != null ? dto.getBridgeId() : sensor.getBridgeId());
        sd.setValue(dto.getValue());
        sd.setTemperature(dto.getTemperature());
        sd.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());
        return sd;
    }

    @Async
    public void publishEvent(SensorData data, Sensor sensor, DataIngestedEvent.Trigger trigger) {
        try {
            DataIngestedEvent event = DataIngestedEvent.builder()
                    .bridgeId(data.getBridgeId())
                    .sensorId(sensor.getId())
                    .sensorCode(sensor.getCode())
                    .sensorType(sensor.getType())
                    .latestData(data)
                    .ingestTime(LocalDateTime.now())
                    .trigger(trigger)
                    .build();
            eventPublisher.publishEvent(event);
            log.trace("[DTU] 发布DataIngestedEvent bridge={}, sensor={}, type={}",
                    data.getBridgeId(), sensor.getCode(), sensor.getType());
        } catch (Exception e) {
            log.warn("[DTU] 发布事件失败: {}", e.getMessage());
        }
    }
}
