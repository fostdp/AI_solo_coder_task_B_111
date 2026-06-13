package com.heritage.bridge.weathering;

import com.heritage.bridge.config.WeatheringProperties;
import com.heritage.bridge.dto.WeatheringRequestDTO;
import com.heritage.bridge.dto.WeatheringResultDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.WeatheringData;
import com.heritage.bridge.event.DataIngestedEvent;
import com.heritage.bridge.event.WeatheringEvaluatedEvent;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.WeatheringDataRepository;
import com.heritage.bridge.simulation.WeatheringRegressionSolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatheringEvaluationService {

    private final WeatheringDataRepository weatheringRepository;
    private final BridgeRepository bridgeRepository;
    private final WeatheringRegressionSolver solver;
    private final WeatheringProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WeatheringResultDTO evaluateWeathering(WeatheringRequestDTO request) {
        Bridge bridge = bridgeRepository.findById(request.getBridgeId())
                .orElseThrow(() -> new IllegalArgumentException("桥梁不存在: " + request.getBridgeId()));

        WeatheringResultDTO result = solver.solve(request, bridge.getName());

        List<WeatheringData> dataToSave = new ArrayList<>();
        for (WeatheringResultDTO.WeatheringPoint point : result.getPoints()) {
            WeatheringData data = new WeatheringData();
            data.setBridgeId(request.getBridgeId());
            data.setLocation(point.getLocation());
            data.setLocX(point.getLocX());
            data.setLocY(point.getLocY());
            data.setLocZ(point.getLocZ());
            data.setSurfaceHardness(point.getSurfaceHardness());
            data.setUltrasonicVelocity(point.getUltrasonicVelocity());
            data.setEstimatedDepth(point.getEstimatedDepth());
            data.setWeatheringGrade(point.getWeatheringGrade());
            data.setRegressionRSquared(result.getRSquared());
            dataToSave.add(data);
        }

        weatheringRepository.saveAll(dataToSave);

        WeatheringEvaluatedEvent event = WeatheringEvaluatedEvent.builder()
                .bridgeId(request.getBridgeId())
                .avgDepth(result.getAvgDepth())
                .maxDepth(result.getMaxDepth())
                .overallGrade(result.getOverallGrade())
                .evaluatedAt(LocalDateTime.now())
                .trigger(WeatheringEvaluatedEvent.Trigger.ON_DEMAND)
                .build();
        eventPublisher.publishEvent(event);

        log.info("完成桥梁{}风化评估，平均深度{:.2f}mm，等级{}",
                bridge.getName(), result.getAvgDepth(), result.getOverallGrade());

        return result;
    }

    public List<WeatheringData> getWeatheringHistory(Long bridgeId) {
        return weatheringRepository.findByBridgeIdOrderByMeasuredAtDesc(bridgeId);
    }

    public Optional<WeatheringData> getLatestWeathering(Long bridgeId) {
        return weatheringRepository.findTopByBridgeIdOrderByMeasuredAtDesc(bridgeId);
    }

    public Map<String, Integer> getWeatheringGradeDistribution(Long bridgeId) {
        List<Object[]> counts = weatheringRepository.countByGradeForBridge(bridgeId);
        Map<String, Integer> distribution = new java.util.LinkedHashMap<>();
        distribution.put("none", 0);
        distribution.put("slight", 0);
        distribution.put("moderate", 0);
        distribution.put("severe", 0);
        distribution.put("critical", 0);
        for (Object[] row : counts) {
            distribution.put((String) row[0], ((Number) row[1]).intValue());
        }
        return distribution;
    }

    @Transactional
    public void autoEvaluateFromLatestData(Long bridgeId) {
        try {
            Bridge bridge = bridgeRepository.findById(bridgeId).orElse(null);
            if (bridge == null) return;

            List<WeatheringData> latestData = weatheringRepository.findLatestByBridgeId(bridgeId);
            if (latestData == null || latestData.isEmpty()) return;

            List<WeatheringRequestDTO.MeasurementPoint> measurements = new ArrayList<>();
            for (WeatheringData d : latestData) {
                WeatheringRequestDTO.MeasurementPoint mp = new WeatheringRequestDTO.MeasurementPoint();
                mp.setLocation(d.getLocation());
                mp.setLocX(d.getLocX());
                mp.setLocY(d.getLocY());
                mp.setLocZ(d.getLocZ());
                mp.setSurfaceHardness(d.getSurfaceHardness());
                mp.setUltrasonicVelocity(d.getUltrasonicVelocity());
                measurements.add(mp);
            }

            WeatheringRequestDTO request = new WeatheringRequestDTO();
            request.setBridgeId(bridgeId);
            request.setMeasurements(measurements);

            WeatheringResultDTO result = solver.solve(request, bridge.getName());
            saveWeatheringResults(bridgeId, result);
            publishEvaluatedEvent(bridgeId, result,
                    WeatheringEvaluatedEvent.Trigger.DATA_TRIGGERED_BATCH);

            log.info("从最新数据自动评估桥梁{}风化完成，测点:{}", bridge.getName(), measurements.size());
        } catch (Exception e) {
            log.warn("自动风化评估失败 bridgeId={}, err={}", bridgeId, e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onDataIngested(DataIngestedEvent event) {
        if (!properties.isDataTriggeredAutoEvaluate()) return;

        String sensorType = event.getSensorType();
        if ("hardness".equals(sensorType) || "ultrasonic".equals(sensorType)) {
            log.debug("收到{}数据，触发风化评估，桥ID:{}", sensorType, event.getBridgeId());
            try {
                List<WeatheringRequestDTO.MeasurementPoint> measurements = generateMeasurementsFromSensor(event);
                WeatheringRequestDTO request = new WeatheringRequestDTO();
                request.setBridgeId(event.getBridgeId());
                request.setMeasurements(measurements);

                WeatheringResultDTO result = solver.solve(request, "Auto");
                publishEvaluatedEvent(event.getBridgeId(), result,
                        "hardness".equals(sensorType) ?
                                WeatheringEvaluatedEvent.Trigger.HARDNESS_DATA_TRIGGERED :
                                WeatheringEvaluatedEvent.Trigger.ULTRASONIC_DATA_TRIGGERED);

            } catch (Exception e) {
                log.warn("自动风化评估失败: {}", e.getMessage());
            }
        }
    }

    @Scheduled(cron = "${weathering.regression.scheduled-cron:0 0 4 * * ?}")
    public void scheduledDailyEvaluation() {
        if (!properties.isScheduledEnabled()) return;
        log.info("开始每日风化评估调度任务");

        List<Bridge> bridges = bridgeRepository.findAll();
        for (Bridge bridge : bridges) {
            try {
                List<WeatheringRequestDTO.MeasurementPoint> measurements = generateDefaultMeasurements(bridge);
                WeatheringRequestDTO request = new WeatheringRequestDTO();
                request.setBridgeId(bridge.getId());
                request.setMeasurements(measurements);

                WeatheringResultDTO result = solver.solve(request, bridge.getName());
                saveWeatheringResults(bridge.getId(), result);
                publishEvaluatedEvent(bridge.getId(), result,
                        WeatheringEvaluatedEvent.Trigger.SCHEDULED_DAILY);

            } catch (Exception e) {
                log.error("桥梁{}风化评估失败: {}", bridge.getName(), e.getMessage());
            }
        }
        log.info("每日风化评估调度任务完成");
    }

    private List<WeatheringRequestDTO.MeasurementPoint> generateDefaultMeasurements(Bridge bridge) {
        double[][] positions = {
                {-2.0, 0.2, 0.5}, {-1.0, 0.4, 0.5}, {0.0, 0.5, 0.5},
                {1.0, 0.4, 0.5}, {2.0, 0.2, 0.5}, {-1.5, 0.35, 0.5},
                {1.5, 0.35, 0.5}, {0.0, 0.45, 0.5}
        };

        List<WeatheringRequestDTO.MeasurementPoint> measurements = new ArrayList<>();
        java.util.Random random = new java.util.Random(bridge.getId().intValue());

        for (int i = 0; i < positions.length; i++) {
            double baseHardness = 45.0 - (bridge.getHealthScore() < 80 ? 15 : 5);
            double baseVelocity = 3.8 - (bridge.getHealthScore() < 80 ? 0.8 : 0.3);

            WeatheringRequestDTO.MeasurementPoint mp = new WeatheringRequestDTO.MeasurementPoint();
            mp.setLocation("自动检测点" + (i + 1));
            mp.setLocX(positions[i][0] * bridge.getSpanLength() / 10);
            mp.setLocY(positions[i][1] * bridge.getSpanLength() * bridge.getRiseSpanRatio());
            mp.setLocZ(positions[i][2]);
            mp.setSurfaceHardness(Math.max(20, Math.min(65, baseHardness + random.nextGaussian() * 3)));
            mp.setUltrasonicVelocity(Math.max(2.0, Math.min(5.0, baseVelocity + random.nextGaussian() * 0.2)));
            measurements.add(mp);
        }
        return measurements;
    }

    private List<WeatheringRequestDTO.MeasurementPoint> generateMeasurementsFromSensor(DataIngestedEvent event) {
        List<WeatheringRequestDTO.MeasurementPoint> measurements = new ArrayList<>();
        WeatheringRequestDTO.MeasurementPoint mp = new WeatheringRequestDTO.MeasurementPoint();
        mp.setLocation(event.getSensorCode());
        if (event.getLatestData() != null) {
            mp.setSurfaceHardness(event.getLatestData().getValue().doubleValue());
            mp.setUltrasonicVelocity(3.5);
        } else {
            mp.setSurfaceHardness(40.0);
            mp.setUltrasonicVelocity(3.5);
        }
        mp.setLocX(0.0);
        mp.setLocY(0.4);
        mp.setLocZ(0.5);
        measurements.add(mp);
        return measurements;
    }

    private void saveWeatheringResults(Long bridgeId, WeatheringResultDTO result) {
        List<WeatheringData> dataToSave = new ArrayList<>();
        for (WeatheringResultDTO.WeatheringPoint point : result.getPoints()) {
            WeatheringData data = new WeatheringData();
            data.setBridgeId(bridgeId);
            data.setLocation(point.getLocation());
            data.setLocX(point.getLocX());
            data.setLocY(point.getLocY());
            data.setLocZ(point.getLocZ());
            data.setSurfaceHardness(point.getSurfaceHardness());
            data.setUltrasonicVelocity(point.getUltrasonicVelocity());
            data.setEstimatedDepth(point.getEstimatedDepth());
            data.setWeatheringGrade(point.getWeatheringGrade());
            data.setRegressionRSquared(result.getRSquared());
            dataToSave.add(data);
        }
        weatheringRepository.saveAll(dataToSave);
    }

    private void publishEvaluatedEvent(Long bridgeId, WeatheringResultDTO result,
                                       WeatheringEvaluatedEvent.Trigger trigger) {
        WeatheringEvaluatedEvent event = WeatheringEvaluatedEvent.builder()
                .bridgeId(bridgeId)
                .avgDepth(result.getAvgDepth())
                .maxDepth(result.getMaxDepth())
                .overallGrade(result.getOverallGrade())
                .evaluatedAt(LocalDateTime.now())
                .trigger(trigger)
                .build();
        eventPublisher.publishEvent(event);
    }
}
