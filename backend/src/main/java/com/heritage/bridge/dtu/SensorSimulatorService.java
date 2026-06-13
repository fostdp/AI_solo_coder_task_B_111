package com.heritage.bridge.dtu;

import com.heritage.bridge.dto.SensorDataUploadDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.entity.TrafficFlowData;
import com.heritage.bridge.entity.WeatheringData;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.SensorRepository;
import com.heritage.bridge.repository.TrafficFlowDataRepository;
import com.heritage.bridge.repository.WeatheringDataRepository;
import com.heritage.bridge.traffic.TrafficVibrationService;
import com.heritage.bridge.weathering.WeatheringEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j(topic = "sensor-simulator")
@Service
@RequiredArgsConstructor
public class SensorSimulatorService {

    private final BridgeRepository bridgeRepository;
    private final SensorRepository sensorRepository;
    private final DtuReceiverService dtuReceiverService;
    private final WeatheringDataRepository weatheringDataRepository;
    private final TrafficFlowDataRepository trafficFlowDataRepository;
    private final WeatheringEvaluationService weatheringEvaluationService;
    private final TrafficVibrationService trafficVibrationService;

    private final Random random = new Random();

    @Scheduled(fixedDelayString = "${simulation.sensor.interval-ms:60000}")
    @Transactional
    public void simulateAllSensors() {
        log.debug("[Simulator] 开始生成模拟传感器数据");
        List<Bridge> bridges = bridgeRepository.findAll();
        int count = 0;
        for (Bridge bridge : bridges) {
            count += simulateBridgeSensors(bridge);
        }
        if (count > 0) {
            log.debug("[Simulator] 完成生成 {} 条传感器数据", count);
        }
    }

    private int simulateBridgeSensors(Bridge bridge) {
        List<Sensor> sensors = sensorRepository.findByBridgeId(bridge.getId());
        int count = 0;
        for (Sensor sensor : sensors) {
            try {
                double value = generateSensorValue(bridge, sensor);
                SensorDataUploadDTO dto = SensorDataUploadDTO.builder()
                        .sensorCode(sensor.getCode())
                        .bridgeId(bridge.getId())
                        .value(BigDecimal.valueOf(value))
                        .temperature(BigDecimal.valueOf(20 + random.nextGaussian() * 5))
                        .timestamp(LocalDateTime.now())
                        .build();
                dtuReceiverService.ingest(dto);
                count++;
            } catch (Exception e) {
                log.warn("[Simulator] 生成传感器数据失败 sensor={}, err={}", sensor.getCode(), e.getMessage());
            }
        }
        return count;
    }

    private double generateSensorValue(Bridge bridge, Sensor sensor) {
        double base;
        switch (sensor.getType()) {
            case "strain":
                base = 50 + random.nextDouble() * 80;
                return base + random.nextGaussian() * 10;
            case "displacement":
                base = 0.5 + random.nextDouble() * 3;
                return base + random.nextGaussian() * 0.5;
            case "crack":
                base = 0.05 + random.nextDouble() * 0.3;
                return base + random.nextGaussian() * 0.05;
            case "temperature":
                base = 15 + random.nextDouble() * 15;
                return base + random.nextGaussian() * 2;
            case "vibration":
                base = 0.02 + random.nextDouble() * 0.1;
                return base + random.nextGaussian() * 0.02;
            case "hardness":
                base = 35 + random.nextDouble() * 20;
                return Math.max(10, base - (bridge.getHealthScore() < 80 ? 5 + random.nextDouble() * 10 : 0));
            case "ultrasonic":
                base = 3.0 + random.nextDouble() * 1.2;
                return Math.max(1.5, base - (bridge.getHealthScore() < 80 ? 0.3 + random.nextDouble() * 0.5 : 0));
            default:
                return 10 + random.nextDouble() * 50;
        }
    }

    @Scheduled(cron = "${simulation.weathering.scheduled-cron:0 30 1 * * ?}")
    @Transactional
    public void simulateDailyWeatheringMeasurements() {
        log.info("[Simulator] 开始每日风化测量模拟");
        List<Bridge> bridges = bridgeRepository.findAll();
        int count = 0;
        for (Bridge bridge : bridges) {
            try {
                List<WeatheringData> existing = weatheringDataRepository.findLatestByBridgeId(bridge.getId());
                int measureCount = existing.isEmpty() ? 8 : existing.size();
                for (int i = 0; i < measureCount; i++) {
                    WeatheringData data = buildWeatheringMeasurement(bridge, i, measureCount);
                    weatheringDataRepository.save(data);
                    count++;
                }
                weatheringEvaluationService.autoEvaluateFromLatestData(bridge.getId());
            } catch (Exception e) {
                log.warn("[Simulator] 风化测量模拟失败 bridge={}, err={}", bridge.getName(), e.getMessage());
            }
        }
        log.info("[Simulator] 完成每日风化测量，生成 {} 条数据", count);
    }

    private WeatheringData buildWeatheringMeasurement(Bridge bridge, int idx, int total) {
        double t = (double) idx / Math.max(1, total - 1);
        double x = -bridge.getSpanLength() / 2 + t * bridge.getSpanLength();
        double rise = bridge.getSpanLength() * (bridge.getRiseSpanRatio() != null ? bridge.getRiseSpanRatio() : 0.2);
        double y = 4 * rise * (0.25 - (x * x) / (bridge.getSpanLength() * bridge.getSpanLength()));

        double healthFactor = (100 - bridge.getHealthScore()) / 100.0;
        double hardness = Math.max(15, 50 - healthFactor * 30 + random.nextGaussian() * 5);
        double velocity = Math.max(1.8, 4.2 - healthFactor * 1.8 + random.nextGaussian() * 0.3);

        WeatheringData data = new WeatheringData();
        data.setBridgeId(bridge.getId());
        data.setLocation("测点-" + (idx + 1));
        data.setLocX(x);
        data.setLocY(Math.max(0.2, y));
        data.setLocZ((random.nextDouble() - 0.5) * 3);
        data.setSurfaceHardness(hardness);
        data.setUltrasonicVelocity(velocity);
        data.setEstimatedDepth(0.0);
        data.setWeatheringGrade("pending");
        return data;
    }

    @Scheduled(cron = "${simulation.traffic.scheduled-cron:0 0 * * * ?}")
    @Transactional
    public void simulateHourlyTrafficFlow() {
        log.info("[Simulator] 开始逐小时交通流量模拟");
        List<Bridge> bridges = bridgeRepository.findAll();
        int hour = LocalDateTime.now().getHour();
        int count = 0;
        for (Bridge bridge : bridges) {
            try {
                String[] vehicleTypes = {"passenger", "truck_light", "truck_medium", "truck_heavy", "bus"};
                for (String vehicleType : vehicleTypes) {
                    TrafficFlowData data = buildTrafficFlowRecord(bridge, hour, vehicleType);
                    trafficFlowDataRepository.save(data);
                    count++;
                }
            } catch (Exception e) {
                log.warn("[Simulator] 交通流量模拟失败 bridge={}, err={}", bridge.getName(), e.getMessage());
            }
        }
        log.info("[Simulator] 完成逐小时交通流量，生成 {} 条数据", count);
    }

    private TrafficFlowData buildTrafficFlowRecord(Bridge bridge, int hour, String vehicleType) {
        int peakFactor = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19) ? 3 :
                (hour >= 10 && hour <= 16) ? 2 :
                (hour >= 6 && hour <= 22) ? 1 : 0;
        int baseCount = switch (vehicleType) {
            case "passenger" -> 50 + peakFactor * 80;
            case "bus" -> 3 + peakFactor * 10;
            case "truck_light" -> 5 + peakFactor * 15;
            case "truck_medium" -> 3 + peakFactor * 8;
            case "truck_heavy" -> 1 + peakFactor * 4;
            default -> 10;
        };

        double avgSpeed = switch (vehicleType) {
            case "passenger" -> 50 + random.nextDouble() * 30;
            case "bus" -> 40 + random.nextDouble() * 20;
            case "truck_light" -> 45 + random.nextDouble() * 25;
            case "truck_medium" -> 35 + random.nextDouble() * 20;
            case "truck_heavy" -> 30 + random.nextDouble() * 15;
            default -> 40;
        };

        double avgWeight = switch (vehicleType) {
            case "passenger" -> 1.5 + random.nextDouble() * 0.5;
            case "bus" -> 10 + random.nextDouble() * 5;
            case "truck_light" -> 5 + random.nextDouble() * 5;
            case "truck_medium" -> 12 + random.nextDouble() * 10;
            case "truck_heavy" -> 25 + random.nextDouble() * 20;
            default -> 3;
        };

        int vehicleCount = Math.max(0, baseCount + (int) (random.nextGaussian() * baseCount * 0.2));
        double totalWeight = vehicleCount * avgWeight;

        TrafficFlowData data = new TrafficFlowData();
        data.setBridgeId(bridge.getId());
        data.setRecordDate(LocalDate.now());
        data.setHourOfDay(hour);
        data.setVehicleType(vehicleType);
        data.setVehicleCount(vehicleCount);
        data.setAvgSpeed(avgSpeed);
        data.setAvgWeight(avgWeight);
        data.setTotalWeight(totalWeight);
        data.setSource("simulated");
        return data;
    }

    @Scheduled(cron = "${simulation.traffic.vibration-cron:0 15 * * * ?}")
    @Transactional
    public void triggerVibrationAnalysis() {
        log.info("[Simulator] 触发车桥耦合振动分析");
        List<Bridge> bridges = bridgeRepository.findAll();
        for (Bridge bridge : bridges) {
            try {
                trafficVibrationService.autoAnalyzeFromTraffic(bridge.getId());
            } catch (Exception e) {
                log.warn("[Simulator] 振动分析失败 bridge={}, err={}", bridge.getName(), e.getMessage());
            }
        }
    }
}
