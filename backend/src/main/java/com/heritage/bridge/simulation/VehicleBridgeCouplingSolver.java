package com.heritage.bridge.simulation;

import com.heritage.bridge.dto.TrafficVibrationRequestDTO;
import com.heritage.bridge.dto.TrafficVibrationResultDTO;
import com.heritage.bridge.config.TrafficVibrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class VehicleBridgeCouplingSolver {

    private final TrafficVibrationProperties properties;
    private static final double GRAVITY = 9.81;
    private static final int TIME_STEPS = 1000;
    private static final double DT = 0.01;

    public VehicleBridgeCouplingSolver(TrafficVibrationProperties properties) {
        this.properties = properties;
    }

    public TrafficVibrationResultDTO solve(TrafficVibrationRequestDTO request, String bridgeName) {
        validateInput(request);

        double omega0 = request.getNaturalFrequency() != null ?
                request.getNaturalFrequency() : properties.getDefaultNaturalFrequency();
        double zeta = request.getDampingRatio() != null ?
                request.getDampingRatio() : properties.getDefaultDampingRatio();
        double M = request.getBridgeMass() != null ?
                request.getBridgeMass() : properties.getDefaultBridgeMass();
        double K = request.getSpanStiffness() != null ?
                request.getSpanStiffness() : properties.getDefaultSpanStiffness();

        List<TrafficVibrationResultDTO.VehicleAnalysis> analyses = new ArrayList<>();
        double minWeightLimit = Double.MAX_VALUE;
        double minSpeedLimit = Double.MAX_VALUE;
        boolean hasExceed = false;

        for (TrafficVibrationRequestDTO.VehicleLoad load : request.getVehicleLoads()) {
            int count = load.getCount() != null ? load.getCount() : 1;
            for (int i = 0; i < count; i++) {
                TrafficVibrationResultDTO.VehicleAnalysis analysis =
                        analyzeSingleVehicle(load, omega0, zeta, M, K, request.getBridgeId());
                analyses.add(analysis);

                if (analysis.getSafetyMargin() < 1.0) {
                    hasExceed = true;
                }

                if (analysis.getAllowableWeightLimit() != null) {
                    minWeightLimit = Math.min(minWeightLimit, analysis.getAllowableWeightLimit());
                }
                if (analysis.getAllowableSpeedLimit() != null) {
                    minSpeedLimit = Math.min(minSpeedLimit, analysis.getAllowableSpeedLimit());
                }
            }
        }

        String safetyLevel = determineSafetyLevel(analyses);
        String recommendation = generateRecommendation(safetyLevel, minWeightLimit, minSpeedLimit, hasExceed);

        return TrafficVibrationResultDTO.builder()
                .bridgeId(request.getBridgeId())
                .analyses(analyses)
                .allowableWeightLimit(minWeightLimit == Double.MAX_VALUE ? null : Math.round(minWeightLimit * 10.0) / 10.0)
                .allowableSpeedLimit(minSpeedLimit == Double.MAX_VALUE ? null : Math.round(minSpeedLimit * 10.0) / 10.0)
                .overallSafetyLevel(safetyLevel)
                .recommendation(recommendation)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private void validateInput(TrafficVibrationRequestDTO request) {
        if (request.getVehicleLoads() == null || request.getVehicleLoads().isEmpty()) {
            throw new IllegalArgumentException("车辆荷载数据不能为空");
        }
        for (TrafficVibrationRequestDTO.VehicleLoad load : request.getVehicleLoads()) {
            if (load.getVehicleWeight() == null || load.getVehicleSpeed() == null) {
                throw new IllegalArgumentException("车辆重量和速度不能为空");
            }
            if (load.getVehicleWeight() <= 0 || load.getVehicleWeight() > 100) {
                throw new IllegalArgumentException("车辆重量应在0-100吨范围内");
            }
            if (load.getVehicleSpeed() <= 0 || load.getVehicleSpeed() > 120) {
                throw new IllegalArgumentException("车辆速度应在0-120 km/h范围内");
            }
        }
    }

    private TrafficVibrationResultDTO.VehicleAnalysis analyzeSingleVehicle(
            TrafficVibrationRequestDTO.VehicleLoad load, double omega0, double zeta,
            double M, double K, Long bridgeId) {

        double m = load.getVehicleWeight() * 1000;
        double v = load.getVehicleSpeed() / 3.6;

        double omegaD = omega0 * Math.sqrt(1 - zeta * zeta);
        double omegaV = v / 10.0;

        double staticDisplacement = m * GRAVITY / K;

        double[] time = new double[TIME_STEPS];
        double[] displacement = new double[TIME_STEPS];
        double[] acceleration = new double[TIME_STEPS];
        double[] dynamicForce = new double[TIME_STEPS];

        double uPrev = 0;
        double uPrevPrev = 0;

        for (int i = 0; i < TIME_STEPS; i++) {
            time[i] = i * DT;
            double x = v * time[i];
            double loadPosition = Math.sin(Math.PI * x / 30.0);

            double excitation = m * GRAVITY * loadPosition * (1 + 0.1 * Math.sin(omegaV * time[i]));

            double uNew = (excitation * DT * DT + (2 * M + K * DT * DT) * uPrev - M * uPrevPrev) /
                    (M + 2 * zeta * omega0 * M * DT);

            double vel = (uNew - uPrevPrev) / (2 * DT);
            double acc = (uNew - 2 * uPrev + uPrevPrev) / (DT * DT);

            displacement[i] = uNew;
            acceleration[i] = acc;
            dynamicForce[i] = K * uNew + 2 * zeta * omega0 * M * vel + M * acc;

            uPrevPrev = uPrev;
            uPrev = uNew;
        }

        double maxDisplacement = Arrays.stream(displacement).map(Math::abs).max().orElse(0);
        double maxAcceleration = Arrays.stream(acceleration).map(Math::abs).max().orElse(0);
        double maxDynamicForce = Arrays.stream(dynamicForce).map(Math::abs).max().orElse(0);

        double dynamicAmplificationFactor = maxDynamicForce / (m * GRAVITY);

        double displacementRatio = maxDisplacement / properties.getAllowableDisplacement();
        double accelerationRatio = maxAcceleration / properties.getAllowableAcceleration();

        double safetyMargin = 1.0 / Math.max(displacementRatio, accelerationRatio);

        double allowableWeight = load.getVehicleWeight() * safetyMargin;
        double allowableSpeed = load.getVehicleSpeed() * Math.sqrt(safetyMargin);

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("time_series", Arrays.copyOfRange(time, 0, 200));
        responseData.put("displacement_series", Arrays.copyOfRange(displacement, 0, 200));
        responseData.put("acceleration_series", Arrays.copyOfRange(acceleration, 0, 200));
        responseData.put("dynamic_force_series", Arrays.copyOfRange(dynamicForce, 0, 200));
        responseData.put("natural_frequency", omega0);
        responseData.put("damped_frequency", omegaD);
        responseData.put("static_displacement", staticDisplacement);

        return TrafficVibrationResultDTO.VehicleAnalysis.builder()
                .vehicleType(load.getVehicleType())
                .vehicleWeight(load.getVehicleWeight())
                .vehicleSpeed(load.getVehicleSpeed())
                .naturalFrequency(omega0)
                .dampingRatio(zeta)
                .maxAcceleration(Math.round(maxAcceleration * 10000.0) / 10000.0)
                .maxDynamicDisplacement(Math.round(maxDisplacement * 1000000.0) / 1000000.0)
                .dynamicAmplificationFactor(Math.round(dynamicAmplificationFactor * 1000.0) / 1000.0)
                .safetyMargin(Math.round(safetyMargin * 1000.0) / 1000.0)
                .allowableWeightLimit(Math.round(allowableWeight * 10.0) / 10.0)
                .allowableSpeedLimit(Math.round(allowableSpeed * 10.0) / 10.0)
                .exceedLimit(safetyMargin < 1.0)
                .build();
    }

    private String determineSafetyLevel(List<TrafficVibrationResultDTO.VehicleAnalysis> analyses) {
        double minSafety = analyses.stream()
                .mapToDouble(TrafficVibrationResultDTO.VehicleAnalysis::getSafetyMargin)
                .min().orElse(1.0);

        if (minSafety >= 2.0) return "excellent";
        if (minSafety >= 1.5) return "good";
        if (minSafety >= 1.0) return "fair";
        if (minSafety >= 0.7) return "poor";
        return "critical";
    }

    private String generateRecommendation(String safetyLevel, double weightLimit, double speedLimit, boolean hasExceed) {
        StringBuilder sb = new StringBuilder();

        switch (safetyLevel) {
            case "excellent":
                sb.append("【优秀】桥梁抗振性能优异。");
                sb.append(String.format("建议限重%.0f吨，限速%.0fkm/h。", weightLimit, speedLimit));
                sb.append("定期进行振动监测即可。");
                break;
            case "good":
                sb.append("【良好】桥梁抗振性能良好。");
                sb.append(String.format("建议限重%.0f吨，限速%.0fkm/h。", weightLimit, speedLimit));
                sb.append("重点监控超载车辆，每季度进行一次振动检测。");
                break;
            case "fair":
                sb.append("【一般】桥梁抗振性能一般，存在潜在风险。");
                sb.append(String.format("建议限重%.0f吨，限速%.0fkm/h。", weightLimit, speedLimit));
                sb.append("加强交通管制，严禁超载，每月进行一次振动检测，考虑对关键部位进行加固。");
                break;
            case "poor":
                sb.append("【较差】桥梁抗振性能较差！");
                sb.append(String.format("建议限重%.0f吨，限速%.0fkm/h。", weightLimit, speedLimit));
                sb.append("立即启动交通管制，限制重型车辆通行，组织专项检测评估，制定加固方案。");
                break;
            case "critical":
                sb.append("【危险】桥梁抗振性能严重不足，存在重大安全隐患！");
                sb.append(String.format("紧急建议限重%.0f吨，限速%.0fkm/h。", weightLimit, speedLimit));
                sb.append("【警报】立即禁止重型车辆通行，组织专家论证，实施紧急加固或交通封闭！");
                break;
        }

        return sb.toString();
    }

    public double calculateComfortIndex(double maxAcceleration) {
        double aGal = maxAcceleration * 100;
        if (aGal < 5) return 1.0;
        if (aGal < 10) return 0.9;
        if (aGal < 20) return 0.7;
        if (aGal < 50) return 0.5;
        if (aGal < 100) return 0.3;
        return 0.1;
    }
}
