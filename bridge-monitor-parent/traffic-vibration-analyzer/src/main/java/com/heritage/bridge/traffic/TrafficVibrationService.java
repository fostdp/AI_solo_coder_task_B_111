package com.heritage.bridge.traffic;

import com.heritage.bridge.config.TrafficVibrationProperties;
import com.heritage.bridge.dto.TrafficVibrationRequestDTO;
import com.heritage.bridge.dto.TrafficVibrationResultDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.TrafficFlowData;
import com.heritage.bridge.entity.TrafficVibrationAnalysis;
import com.heritage.bridge.event.DataIngestedEvent;
import com.heritage.bridge.event.TrafficFlowIngestedEvent;
import com.heritage.bridge.event.VibrationAnalyzedEvent;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.TrafficFlowDataRepository;
import com.heritage.bridge.repository.TrafficVibrationAnalysisRepository;
import com.heritage.bridge.simulation.VehicleBridgeCouplingSolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficVibrationService {

    private final TrafficVibrationAnalysisRepository analysisRepository;
    private final TrafficFlowDataRepository flowRepository;
    private final BridgeRepository bridgeRepository;
    private final VehicleBridgeCouplingSolver solver;
    private final TrafficVibrationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TrafficVibrationResultDTO analyzeVibration(TrafficVibrationRequestDTO request) {
        Bridge bridge = bridgeRepository.findById(request.getBridgeId())
                .orElseThrow(() -> new IllegalArgumentException("桥梁不存在: " + request.getBridgeId()));

        TrafficVibrationResultDTO result = solver.solve(request, bridge.getName());

        for (TrafficVibrationResultDTO.VehicleAnalysis analysis : result.getAnalyses()) {
            TrafficVibrationAnalysis entity = new TrafficVibrationAnalysis();
            entity.setBridgeId(request.getBridgeId());
            entity.setVehicleType(analysis.getVehicleType());
            entity.setVehicleWeight(analysis.getVehicleWeight());
            entity.setVehicleSpeed(analysis.getVehicleSpeed());
            entity.setNaturalFrequency(analysis.getNaturalFrequency());
            entity.setDampingRatio(analysis.getDampingRatio());
            entity.setMaxAcceleration(analysis.getMaxAcceleration());
            entity.setMaxDynamicDisplacement(analysis.getMaxDynamicDisplacement());
            entity.setDynamicAmplificationFactor(analysis.getDynamicAmplificationFactor());
            entity.setSafetyMargin(analysis.getSafetyMargin());
            entity.setAllowableWeightLimit(analysis.getAllowableWeightLimit());
            entity.setAllowableSpeedLimit(analysis.getAllowableSpeedLimit());
            analysisRepository.save(entity);
        }

        boolean hasExceed = result.getAnalyses().stream()
                .anyMatch(a -> a.getExceedLimit() != null && a.getExceedLimit());

        Double maxAccel = result.getAnalyses().stream()
                .mapToDouble(TrafficVibrationResultDTO.VehicleAnalysis::getMaxAcceleration)
                .max().orElse(0);

        VibrationAnalyzedEvent event = VibrationAnalyzedEvent.builder()
                .bridgeId(request.getBridgeId())
                .overallSafetyLevel(result.getOverallSafetyLevel())
                .allowableWeightLimit(result.getAllowableWeightLimit())
                .allowableSpeedLimit(result.getAllowableSpeedLimit())
                .maxAcceleration(maxAccel)
                .hasExceed(hasExceed)
                .analyzedAt(LocalDateTime.now())
                .trigger(VibrationAnalyzedEvent.Trigger.ON_DEMAND)
                .build();
        eventPublisher.publishEvent(event);

        log.info("完成桥梁{}交通振动分析，安全等级:{}，限重:{:.1f}吨，限速:{:.0f}km/h",
                bridge.getName(), result.getOverallSafetyLevel(),
                result.getAllowableWeightLimit(), result.getAllowableSpeedLimit());

        return result;
    }

    @Transactional
    public TrafficFlowData ingestTrafficFlow(TrafficFlowData data) {
        if (data.getTotalWeight() == null && data.getAvgWeight() != null && data.getVehicleCount() != null) {
            data.setTotalWeight(data.getAvgWeight() * data.getVehicleCount());
        }
        TrafficFlowData saved = flowRepository.save(data);

        TrafficFlowIngestedEvent event = TrafficFlowIngestedEvent.builder()
                .bridgeId(data.getBridgeId())
                .vehicleType(data.getVehicleType())
                .vehicleCount(data.getVehicleCount())
                .totalWeight(data.getTotalWeight())
                .ingestedAt(LocalDateTime.now())
                .build();
        eventPublisher.publishEvent(event);

        return saved;
    }

    public List<TrafficVibrationAnalysis> getAnalysisHistory(Long bridgeId, Integer limit) {
        int l = limit != null ? limit : 10;
        return analysisRepository.findLatestByBridgeId(bridgeId, l);
    }

    public Optional<TrafficVibrationAnalysis> getLatestAnalysis(Long bridgeId) {
        return analysisRepository.findTopByBridgeIdOrderByCalculatedAtDesc(bridgeId);
    }

    public List<TrafficFlowData> getTrafficFlow(Long bridgeId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return flowRepository.findByBridgeIdAndDateRange(bridgeId, start, end);
    }

    public List<Object[]> getTrafficSummary(Long bridgeId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return flowRepository.summarizeTrafficByType(bridgeId, start, end);
    }

    @Transactional
    public void autoAnalyzeFromTraffic(Long bridgeId) {
        try {
            Bridge bridge = bridgeRepository.findById(bridgeId).orElse(null);
            if (bridge == null) return;

            LocalDate today = LocalDate.now();
            List<TrafficFlowData> todayData = flowRepository.findByBridgeIdAndRecordDateOrderByHourOfDay(bridgeId, today);
            if (todayData == null || todayData.isEmpty()) {
                todayData = flowRepository.findByBridgeIdAndDateRange(bridgeId, today.minusDays(1), today);
            }

            TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
            request.setBridgeId(bridgeId);

            Map<String, double[]> typeStats = new HashMap<>();
            if (todayData != null && !todayData.isEmpty()) {
                for (TrafficFlowData flow : todayData) {
                    typeStats.computeIfAbsent(flow.getVehicleType(), k -> new double[]{0, 0, 0});
                    double[] stats = typeStats.get(flow.getVehicleType());
                    stats[0] = Math.max(stats[0], flow.getAvgWeight() != null ? flow.getAvgWeight() : 0);
                    stats[1] = Math.max(stats[1], flow.getAvgSpeed() != null ? flow.getAvgSpeed() : 0);
                    stats[2] += flow.getVehicleCount() != null ? flow.getVehicleCount() : 0;
                }
            }

            List<TrafficVibrationRequestDTO.VehicleLoad> loads = new ArrayList<>();
            if (!typeStats.isEmpty()) {
                for (Map.Entry<String, double[]> entry : typeStats.entrySet()) {
                    double[] stats = entry.getValue();
                    TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
                    load.setVehicleType(entry.getKey());
                    load.setVehicleWeight(Math.max(1.5, stats[0]));
                    load.setVehicleSpeed(Math.max(20, stats[1]));
                    loads.add(load);
                }
            } else {
                loads = generateDefaultRequest(bridgeId).getVehicleLoads();
            }
            request.setVehicleLoads(loads);

            TrafficVibrationResultDTO result = solver.solve(request, bridge.getName());
            saveAnalysisResults(bridgeId, result);
            publishAnalyzedEvent(bridgeId, result,
                    VibrationAnalyzedEvent.Trigger.TRAFFIC_SUMMARY_TRIGGERED);

            log.info("从交通流量数据自动分析桥梁{}振动完成，车型:{}", bridge.getName(), loads.size());
        } catch (Exception e) {
            log.warn("自动振动分析失败 bridgeId={}, err={}", bridgeId, e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onTrafficFlowIngested(TrafficFlowIngestedEvent event) {
        if (!properties.isDataTriggeredAutoAnalyze()) return;
        if (event.getTotalWeight() == null || event.getTotalWeight() < 100) return;

        log.debug("收到交通流量数据，触发振动分析，桥ID:{}", event.getBridgeId());
        try {
            TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
            request.setBridgeId(event.getBridgeId());

            TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
            load.setVehicleType(event.getVehicleType());
            load.setVehicleWeight(Math.min(50, event.getTotalWeight() / Math.max(1, event.getVehicleCount())));
            load.setVehicleSpeed(50.0);
            request.setVehicleLoads(Collections.singletonList(load));

            Bridge bridge = bridgeRepository.findById(event.getBridgeId()).orElse(null);
            if (bridge != null) {
                TrafficVibrationResultDTO result = solver.solve(request, bridge.getName());
                saveAnalysisResults(event.getBridgeId(), result);
                publishAnalyzedEvent(event.getBridgeId(), result,
                        VibrationAnalyzedEvent.Trigger.TRAFFIC_DATA_TRIGGERED);
            }
        } catch (Exception e) {
            log.warn("自动振动分析失败: {}", e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onVibrationDataIngested(DataIngestedEvent event) {
        if (!properties.isDataTriggeredAutoAnalyze()) return;
        if (!"vibration".equals(event.getSensorType())) return;

        log.debug("收到振动传感器数据，触发振动分析，桥ID:{}", event.getBridgeId());
        try {
            TrafficVibrationRequestDTO request = generateDefaultRequest(event.getBridgeId());
            Bridge bridge = bridgeRepository.findById(event.getBridgeId()).orElse(null);
            if (bridge != null) {
                TrafficVibrationResultDTO result = solver.solve(request, bridge.getName());
                saveAnalysisResults(event.getBridgeId(), result);
                publishAnalyzedEvent(event.getBridgeId(), result,
                        VibrationAnalyzedEvent.Trigger.VIBRATION_SENSOR_TRIGGERED);
            }
        } catch (Exception e) {
            log.warn("振动数据触发分析失败: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${traffic.vibration.scheduled-cron:0 30 2 * * ?}")
    public void scheduledDailyAnalysis() {
        if (!properties.isScheduledEnabled()) return;
        log.info("开始每日交通振动分析调度任务");

        List<Bridge> bridges = bridgeRepository.findAll();
        for (Bridge bridge : bridges) {
            try {
                TrafficVibrationRequestDTO request = generateDefaultRequest(bridge.getId());
                TrafficVibrationResultDTO result = solver.solve(request, bridge.getName());
                saveAnalysisResults(bridge.getId(), result);
                publishAnalyzedEvent(bridge.getId(), result,
                        VibrationAnalyzedEvent.Trigger.SCHEDULED_DAILY);
            } catch (Exception e) {
                log.error("桥梁{}交通振动分析失败: {}", bridge.getName(), e.getMessage());
            }
        }
        log.info("每日交通振动分析调度任务完成");
    }

    private TrafficVibrationRequestDTO generateDefaultRequest(Long bridgeId) {
        TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
        request.setBridgeId(bridgeId);

        String[] vehicleTypes = properties.getDefaultVehicleTypes().split(",");
        double[] weights = {1.5, 7.5, 18.0, 35.0, 12.0};
        double[] speeds = {60.0, 55.0, 50.0, 40.0, 50.0};

        List<TrafficVibrationRequestDTO.VehicleLoad> loads = new ArrayList<>();
        for (int i = 0; i < vehicleTypes.length; i++) {
            TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
            load.setVehicleType(vehicleTypes[i].trim());
            load.setVehicleWeight(weights[i]);
            load.setVehicleSpeed(speeds[i]);
            loads.add(load);
        }
        request.setVehicleLoads(loads);
        return request;
    }

    private void saveAnalysisResults(Long bridgeId, TrafficVibrationResultDTO result) {
        for (TrafficVibrationResultDTO.VehicleAnalysis analysis : result.getAnalyses()) {
            TrafficVibrationAnalysis entity = new TrafficVibrationAnalysis();
            entity.setBridgeId(bridgeId);
            entity.setVehicleType(analysis.getVehicleType());
            entity.setVehicleWeight(analysis.getVehicleWeight());
            entity.setVehicleSpeed(analysis.getVehicleSpeed());
            entity.setNaturalFrequency(analysis.getNaturalFrequency());
            entity.setDampingRatio(analysis.getDampingRatio());
            entity.setMaxAcceleration(analysis.getMaxAcceleration());
            entity.setMaxDynamicDisplacement(analysis.getMaxDynamicDisplacement());
            entity.setDynamicAmplificationFactor(analysis.getDynamicAmplificationFactor());
            entity.setSafetyMargin(analysis.getSafetyMargin());
            entity.setAllowableWeightLimit(analysis.getAllowableWeightLimit());
            entity.setAllowableSpeedLimit(analysis.getAllowableSpeedLimit());
            analysisRepository.save(entity);
        }
    }

    private void publishAnalyzedEvent(Long bridgeId, TrafficVibrationResultDTO result,
                                      VibrationAnalyzedEvent.Trigger trigger) {
        boolean hasExceed = result.getAnalyses().stream()
                .anyMatch(a -> a.getExceedLimit() != null && a.getExceedLimit());

        Double maxAccel = result.getAnalyses().stream()
                .mapToDouble(TrafficVibrationResultDTO.VehicleAnalysis::getMaxAcceleration)
                .max().orElse(0);

        VibrationAnalyzedEvent event = VibrationAnalyzedEvent.builder()
                .bridgeId(bridgeId)
                .overallSafetyLevel(result.getOverallSafetyLevel())
                .allowableWeightLimit(result.getAllowableWeightLimit())
                .allowableSpeedLimit(result.getAllowableSpeedLimit())
                .maxAcceleration(maxAccel)
                .hasExceed(hasExceed)
                .analyzedAt(LocalDateTime.now())
                .trigger(trigger)
                .build();
        eventPublisher.publishEvent(event);
    }
}
