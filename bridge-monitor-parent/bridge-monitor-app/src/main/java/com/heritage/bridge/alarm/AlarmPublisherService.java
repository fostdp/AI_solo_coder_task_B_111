package com.heritage.bridge.alarm;

import com.heritage.bridge.alert.MqttAlertPublisher;
import com.heritage.bridge.config.AlertThresholdProperties;
import com.heritage.bridge.entity.Alert;
import com.heritage.bridge.entity.AlertThreshold;
import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.entity.SensorData;
import com.heritage.bridge.event.AlertFiredEvent;
import com.heritage.bridge.event.DamagePredictedEvent;
import com.heritage.bridge.event.DataIngestedEvent;
import com.heritage.bridge.event.FemResultEvent;
import com.heritage.bridge.repository.AlertRepository;
import com.heritage.bridge.repository.AlertThresholdRepository;
import com.heritage.bridge.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j(topic = "alarm-publisher")
@Service
@RequiredArgsConstructor
public class AlarmPublisherService {

    private final AlertRepository alertRepository;
    private final AlertThresholdRepository thresholdRepository;
    private final SensorRepository sensorRepository;
    private final AlertThresholdProperties thresholdProps;
    private final MqttAlertPublisher mqttPublisher;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void onDataIngested(DataIngestedEvent event) {
        if (event.getLatestData() == null || event.getSensorId() == null) return;
        sensorRepository.findById(event.getSensorId()).ifPresent(sensor ->
                evaluateAndFire(event.getLatestData(), sensor, AlertFiredEvent.Source.SENSOR_DATA)
        );
    }

    @Async
    @EventListener
    public void onFemResult(FemResultEvent event) {
        if (event.getSafetyFactor() == null || event.getSafetyFactor() < 1.5) {
            String msg = event.getSafetyFactor() == null
                    ? "FEM仿真无结果，安全系数未知"
                    : String.format("FEM仿真安全系数偏低: %.2f (阈值1.5)", event.getSafetyFactor());
            fireAlert(event.getBridgeId(), null, "fem_safety",
                    event.getSafetyFactor() != null && event.getSafetyFactor() < 1.0 ? "danger" : "warning",
                    msg,
                    event.getSafetyFactor() != null ? event.getSafetyFactor() : 0.0,
                    1.5,
                    AlertFiredEvent.Source.FEM_RESULT);
        }
    }

    @Async
    @EventListener
    public void onDamagePredicted(DamagePredictedEvent event) {
        if (event.getAnnualGrowthRate() == null) return;
        double rate = event.getAnnualGrowthRate();
        double warnRate = thresholdProps.getCrackRate().getWarning() * 12;
        double dangerRate = thresholdProps.getCrackRate().getDanger() * 12;
        if (rate > dangerRate) {
            fireAlert(event.getBridgeId(), event.getCrackSensorId(), "crack_rate",
                    "danger",
                    String.format("裂缝预测年扩展率危险: %.3fmm/年 (阈值%.1f)", rate, dangerRate),
                    rate, dangerRate, AlertFiredEvent.Source.DAMAGE_PREDICTION);
        } else if (rate > warnRate) {
            fireAlert(event.getBridgeId(), event.getCrackSensorId(), "crack_rate",
                    "warning",
                    String.format("裂缝预测年扩展率预警: %.3fmm/年 (阈值%.1f)", rate, warnRate),
                    rate, warnRate, AlertFiredEvent.Source.DAMAGE_PREDICTION);
        }
        if (event.getMaintenanceYear() != null && event.getMaintenanceYear() - LocalDateTime.now().getYear() <= 1) {
            fireAlert(event.getBridgeId(), event.getCrackSensorId(), "maintenance_due",
                    "warning",
                    String.format("建议维修年份临近: %d年", event.getMaintenanceYear()),
                    event.getMaintenanceYear(),
                    LocalDateTime.now().getYear() + 1,
                    AlertFiredEvent.Source.DAMAGE_PREDICTION);
        }
    }

    @Transactional
    public Alert evaluateAndFire(SensorData data, Sensor sensor, AlertFiredEvent.Source source) {
        if (data == null || sensor == null) return null;
        String type = mapSensorTypeToAlertType(sensor.getType());
        if (type == null) return null;
        double v = data.getValue().doubleValue();
        double warn = thresholdProps.getWarningValue(type);
        double danger = thresholdProps.getDangerValue(type);

        if (v > danger) {
            return fireAlert(sensor.getBridgeId(), sensor.getId(), type, "danger",
                    buildMessage(sensor, type, "危险", v, danger), v, danger, source);
        } else if (v > warn) {
            return fireAlert(sensor.getBridgeId(), sensor.getId(), type, "warning",
                    buildMessage(sensor, type, "预警", v, warn), v, warn, source);
        }
        return null;
    }

    @Transactional
    public Alert fireAlert(Long bridgeId, Long sensorId, String type, String level,
                           String message, double value, double threshold,
                           AlertFiredEvent.Source source) {
        LocalDateTime now = LocalDateTime.now();
        List<Alert> recent = alertRepository.findByBridgeIdAndTypeAndTimestampAfter(
                bridgeId, type, now.minusHours(1));
        if (recent != null && recent.stream().anyMatch(a -> level.equals(a.getLevel()))) {
            log.debug("[ALARM] 1小时内已存在同级别告警 bridge={}, type={}, level={}", bridgeId, type, level);
            return null;
        }
        Alert a = new Alert();
        a.setBridgeId(bridgeId);
        a.setSensorId(sensorId);
        a.setType(type);
        a.setLevel(level);
        a.setMessage(message);
        a.setValue(bd(value, 8));
        a.setThreshold(bd(threshold, 8));
        a.setTimestamp(now);
        a.setAcknowledged(false);
        Alert saved = alertRepository.save(a);

        mqttPublisher.publish(saved);

        try {
            AlertFiredEvent event = AlertFiredEvent.builder()
                    .alertId(saved.getId())
                    .bridgeId(bridgeId)
                    .sensorId(sensorId)
                    .alert(saved)
                    .alertType(type)
                    .level(level)
                    .message(message)
                    .value(value)
                    .threshold(threshold)
                    .timestamp(now)
                    .source(source)
                    .deliveryStatus(AlertFiredEvent.DeliveryStatus.QUEUED)
                    .build();
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("[ALARM] 发布AlertFiredEvent失败: {}", e.getMessage());
        }

        log.warn("[ALARM] 生成告警 bridge={}, sensor={}, type={}, level={}, msg={}",
                bridgeId, sensorId, type, level, message);
        return saved;
    }

    public List<Alert> findAll() { return alertRepository.findAll(); }
    public List<Alert> findByBridgeId(Long bridgeId) { return alertRepository.findByBridgeIdOrderByTimestampDesc(bridgeId); }
    public List<Alert> findByLevel(String level) { return alertRepository.findByLevelOrderByTimestampDesc(level); }
    public List<Alert> findUnacknowledged() { return alertRepository.findByAcknowledgedOrderByTimestampDesc(false); }

    @Transactional
    public Alert acknowledge(Long id, String user) {
        alertRepository.acknowledgeAlert(id, LocalDateTime.now(), user);
        return alertRepository.findById(id).orElse(null);
    }

    public Long countUnacknowledged() { return alertRepository.countAllUnacknowledged(); }
    public Long countUnacknowledged(Long bridgeId) { return alertRepository.countUnacknowledgedByBridgeId(bridgeId); }

    public List<AlertThreshold> listThresholds() { return thresholdRepository.findAll(); }

    @Transactional
    public AlertThreshold updateThreshold(Long id, AlertThreshold t) {
        AlertThreshold existing = thresholdRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("阈值配置不存在"));
        existing.setWarningValue(t.getWarningValue());
        existing.setDangerValue(t.getDangerValue());
        existing.setDescription(t.getDescription());
        return thresholdRepository.save(existing);
    }

    private static String mapSensorTypeToAlertType(String sensorType) {
        return switch (sensorType == null ? "" : sensorType) {
            case "displacement" -> "settlement";
            case "crack" -> "crack_rate";
            case "strain" -> "strain";
            case "temperature" -> "temperature_rate";
            default -> null;
        };
    }

    private String buildMessage(Sensor sensor, String type, String levelLabel, double v, double threshold) {
        String name = sensor.getName() != null ? sensor.getName() : "传感器" + sensor.getId();
        String unit = thresholdProps.getUnit(type);
        return switch (type) {
            case "settlement" -> String.format("%s沉降%s: %.2f%s (阈值%.1f%s)", name, levelLabel, v, unit, threshold, unit);
            case "crack_rate" -> String.format("%s扩展速率%s: %.3f%s (阈值%.1f%s)", name, levelLabel, v, unit, threshold, unit);
            case "strain" -> String.format("%s%s: %.1f%s (阈值%.0f%s)", name, levelLabel, v, unit, threshold, unit);
            case "temperature_rate" -> String.format("温度变化速率%s: %.1f%s (阈值%.1f%s)", levelLabel, v, unit, threshold, unit);
            default -> String.format("%s超出%s阈值: %.2f", name, levelLabel, v);
        };
    }

    private static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
