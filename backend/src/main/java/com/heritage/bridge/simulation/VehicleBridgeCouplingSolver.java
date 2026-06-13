package com.heritage.bridge.simulation;

import com.heritage.bridge.config.TrafficVibrationProperties;
import com.heritage.bridge.dto.TrafficVibrationRequestDTO;
import com.heritage.bridge.dto.TrafficVibrationResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleBridgeCouplingSolver {

    private final TrafficVibrationProperties properties;

    private static final double GRAVITY = 9.81;
    private static final double DT = 0.005;
    private static final int TIME_STEPS = 400;

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

        PavementDampingContext pavementCtx = calculatePavementDampingContext(request, omega0, zeta);

        List<TrafficVibrationResultDTO.VehicleAnalysis> analyses = new ArrayList<>();
        double minSafetyMargin = Double.MAX_VALUE;
        double maxDAF = 0;

        for (TrafficVibrationRequestDTO.VehicleLoad load : request.getVehicleLoads()) {
            TrafficVibrationResultDTO.VehicleAnalysis analysis =
                    simulateVehicleBridgeInteraction(load, omega0, zeta, M, K, pavementCtx);
            analysis.setNaturalFrequency(omega0);
            analysis.setDampingRatio(zeta);
            analysis.setPavementDampingFactor(pavementCtx.dampingFactor);
            analysis.setPavementMaterialType(pavementCtx.materialType);

            analyses.add(analysis);

            if (analysis.getSafetyMargin() < minSafetyMargin) {
                minSafetyMargin = analysis.getSafetyMargin();
            }
            if (analysis.getDynamicAmplificationFactor() > maxDAF) {
                maxDAF = analysis.getDynamicAmplificationFactor();
            }
        }

        analyses.sort(Comparator.comparingDouble(
                TrafficVibrationResultDTO.VehicleAnalysis::getSafetyMargin));

        String overallSafetyLevel = determineSafetyLevel(minSafetyMargin);
        double allowableWeightLimit = calculateAllowableWeightLimit(
                omega0, zeta, K, pavementCtx);
        double allowableSpeedLimit = calculateAllowableSpeedLimit(
                maxDAF, analyses);
        String recommendation = generateRecommendation(
                overallSafetyLevel, analyses, allowableWeightLimit,
                allowableSpeedLimit, pavementCtx);

        return TrafficVibrationResultDTO.builder()
                .bridgeId(request.getBridgeId())
                .naturalFrequency(omega0)
                .dampingRatio(zeta)
                .bridgeMass(M)
                .spanStiffness(K)
                .pavementThickness(pavementCtx.thickness)
                .pavementDampingRatio(pavementCtx.dampingRatio)
                .pavementDampingFactor(pavementCtx.dampingFactor)
                .overallSafetyLevel(overallSafetyLevel)
                .overallSafetyMargin(minSafetyMargin)
                .maxDynamicAmplificationFactor(maxDAF)
                .allowableWeightLimit(allowableWeightLimit)
                .allowableSpeedLimit(allowableSpeedLimit)
                .analyses(analyses)
                .recommendation(recommendation)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private PavementDampingContext calculatePavementDampingContext(
            TrafficVibrationRequestDTO request, double omega0, double bridgeZeta) {

        PavementDampingContext ctx = new PavementDampingContext();

        if (!properties.isPavementDampingEnabled()) {
            ctx.dampingFactor = 1.0;
            ctx.materialType = "未考虑";
            return ctx;
        }

        TrafficVibrationRequestDTO.PavementParams params = request.getPavement();
        double thickness = params != null && params.getThickness() != null ?
                params.getThickness() : properties.getDefaultPavementThickness();
        double pavementDampingRatio = params != null && params.getDampingRatio() != null ?
                params.getDampingRatio() : properties.getDefaultPavementDampingRatio();
        String materialType = params != null && params.getMaterialType() != null ?
                params.getMaterialType() : "沥青混凝土";

        double thicknessFactor = Math.max(0.5, Math.min(1.0, thickness / 0.15));

        double compositeDamping = bridgeZeta + pavementDampingRatio * thicknessFactor;
        double dampingFactor = Math.sqrt(bridgeZeta / compositeDamping);

        dampingFactor = Math.max(properties.getMaxPavementDampingFactor(),
                Math.min(properties.getMinPavementDampingFactor(), dampingFactor));

        if (materialType.contains("木") || materialType.contains("wood")) {
            dampingFactor *= 0.9;
        } else if (materialType.contains("石") || materialType.contains("stone")) {
            dampingFactor *= 0.95;
        }

        double period = 2 * Math.PI / omega0;
        double resonanceAdjust = 1.0;
        if (period > 0.3 && period < 0.8) {
            resonanceAdjust = 0.92;
        }
        dampingFactor *= resonanceAdjust;

        dampingFactor = Math.max(0.55, Math.min(0.98, dampingFactor));

        ctx.thickness = thickness;
        ctx.dampingRatio = pavementDampingRatio;
        ctx.dampingFactor = dampingFactor;
        ctx.materialType = materialType;
        ctx.compositeDamping = compositeDamping;

        log.debug("[{}] 铺装层阻尼修正: 厚度={}m, 铺装阻尼比={}, 综合阻尼比={}, 修正系数={:.3f}",
                materialType, thickness, pavementDampingRatio, compositeDamping, dampingFactor);

        return ctx;
    }

    private TrafficVibrationResultDTO.VehicleAnalysis simulateVehicleBridgeInteraction(
            TrafficVibrationRequestDTO.VehicleLoad load,
            double omega0, double zeta, double M, double K,
            PavementDampingContext pavementCtx) {

        double vehicleWeight = load.getVehicleWeight();
        double vehicleSpeed = load.getVehicleSpeed();

        double F0 = vehicleWeight * 1000 * GRAVITY;
        double staticDisplacement = F0 / K;

        double v = vehicleSpeed / 3.6;
        double[] time = new double[TIME_STEPS];
        double[] excitation = new double[TIME_STEPS];

        double dynamicLoadFactor = 1.0 + 0.2 * Math.sin(2 * Math.PI * 3.0 * 0);
        for (int i = 0; i < TIME_STEPS; i++) {
            time[i] = i * DT;
            double position = v * time[i];
            double dynamicLoad = 1.0 + 0.2 * Math.sin(2 * Math.PI * 3.0 * time[i]);
            if (position > 40) dynamicLoad *= 0;
            excitation[i] = F0 * dynamicLoad;
        }

        double[] u = new double[TIME_STEPS];
        double[] uDot = new double[TIME_STEPS];
        double[] uDotDot = new double[TIME_STEPS];

        u[0] = staticDisplacement;
        uDot[0] = 0;
        uDotDot[0] = (excitation[0] - K * u[0] - 2 * zeta * omega0 * M * uDot[0]) / M;

        double maxDynamicDisplacement = staticDisplacement;
        double maxAcceleration = Math.abs(uDotDot[0]);

        for (int i = 1; i < TIME_STEPS; i++) {
            double uNew = (excitation[i] * DT * DT + (2 * M + K * DT * DT) * u[i - 1]
                    - M * (i > 1 ? u[i - 2] : u[i - 1])) / (M + 2 * zeta * omega0 * M * DT);
            double uDotNew = (uNew - u[i - 1]) / DT;
            double uDotDotNew = (uDotNew - uDot[i - 1]) / DT;

            u[i] = uNew;
            uDot[i] = uDotNew;
            uDotDot[i] = uDotDotNew;

            if (Math.abs(uNew) > maxDynamicDisplacement) {
                maxDynamicDisplacement = Math.abs(uNew);
            }
            if (Math.abs(uDotDotNew) > maxAcceleration) {
                maxAcceleration = Math.abs(uDotDotNew);
            }
        }

        double rawDAF = maxDynamicDisplacement / staticDisplacement;

        double DAF = rawDAF * pavementCtx.dampingFactor;

        DAF = Math.max(1.0, Math.min(4.5, DAF));

        maxDynamicDisplacement = staticDisplacement * DAF;
        maxAcceleration = maxAcceleration * pavementCtx.dampingFactor;

        double dynamicDisplacement = maxDynamicDisplacement;
        double peakAcceleration = maxAcceleration;

        double accelerationSafetyMargin = properties.getAllowableAcceleration() / peakAcceleration;
        double displacementSafetyMargin = properties.getAllowableDisplacement() / dynamicDisplacement;
        double safetyMargin = Math.min(accelerationSafetyMargin, displacementSafetyMargin);

        String safetyLevel = determineSafetyLevel(safetyMargin);
        double comfortIndex = calculateComfortIndex(peakAcceleration);

        List<double[]> timeHistory = new ArrayList<>();
        int step = Math.max(1, TIME_STEPS / 100);
        for (int i = 0; i < TIME_STEPS; i += step) {
            double dampedDispl = u[i] * pavementCtx.dampingFactor;
            double dampedAccel = uDotDot[i] * pavementCtx.dampingFactor;
            timeHistory.add(new double[]{time[i], dampedDispl, dampedAccel});
        }

        return TrafficVibrationResultDTO.VehicleAnalysis.builder()
                .vehicleType(load.getVehicleType())
                .vehicleWeight(vehicleWeight)
                .vehicleSpeed(vehicleSpeed)
                .staticDisplacement(staticDisplacement)
                .maxDynamicDisplacement(dynamicDisplacement)
                .maxAcceleration(peakAcceleration)
                .dynamicAmplificationFactor(DAF)
                .rawDynamicAmplificationFactor(rawDAF)
                .pavementCorrectionApplied(pavementCtx.dampingFactor != 1.0)
                .safetyMargin(safetyMargin)
                .safetyLevel(safetyLevel)
                .comfortIndex(comfortIndex)
                .timeHistory(timeHistory)
                .build();
    }

    private double calculateAllowableWeightLimit(double omega0, double zeta, double K,
                                                 PavementDampingContext pavementCtx) {
        double allowableDisp = properties.getAllowableDisplacement();
        double DAF_estimate = 1.5 * pavementCtx.dampingFactor;
        double allowableStaticDisp = allowableDisp / DAF_estimate;
        double allowableForce = K * allowableStaticDisp;
        return allowableForce / GRAVITY / 1000;
    }

    private double calculateAllowableSpeedLimit(double maxDAF,
                                                List<TrafficVibrationResultDTO.VehicleAnalysis> analyses) {
        if (analyses.isEmpty()) return 40.0;

        double limit = 60.0;
        for (TrafficVibrationResultDTO.VehicleAnalysis a : analyses) {
            if (a.getSafetyMargin() < 1.0) {
                limit = Math.min(limit, a.getVehicleSpeed() * 0.6);
            } else if (a.getSafetyMargin() < 1.5) {
                limit = Math.min(limit, a.getVehicleSpeed() * 0.8);
            }
        }

        if (maxDAF > 2.5) limit = Math.min(limit, 30);
        else if (maxDAF > 2.0) limit = Math.min(limit, 40);

        return Math.max(10, Math.min(80, limit));
    }

    private String determineSafetyLevel(double safetyMargin) {
        if (safetyMargin >= 3.0) return "excellent";
        if (safetyMargin >= 2.0) return "good";
        if (safetyMargin >= 1.3) return "fair";
        if (safetyMargin >= 1.0) return "poor";
        return "critical";
    }

    public double calculateComfortIndex(double peakAcceleration) {
        if (peakAcceleration <= 0.02) return 1.0;
        if (peakAcceleration <= 0.05) return 0.9;
        if (peakAcceleration <= 0.1) return 0.8;
        if (peakAcceleration <= 0.2) return 0.6;
        if (peakAcceleration <= 0.5) return 0.4;
        if (peakAcceleration <= 1.0) return 0.2;
        return 0.1;
    }

    private String generateRecommendation(String safetyLevel,
                                          List<TrafficVibrationResultDTO.VehicleAnalysis> analyses,
                                          double weightLimit, double speedLimit,
                                          PavementDampingContext pavementCtx) {
        StringBuilder sb = new StringBuilder();

        if (pavementCtx.dampingFactor < 0.9) {
            sb.append(String.format("【铺装层减振】桥面铺装提供%.1f%%的振动衰减效果，",
                    (1 - pavementCtx.dampingFactor) * 100));
            sb.append(String.format("有效降低了%.1f%%的动力响应。",
                    (1 - pavementCtx.dampingFactor) * 100));
            if (pavementCtx.thickness < 0.06) {
                sb.append("注意：铺装层较薄，建议维护时保持厚度≥6cm。");
            }
        }

        switch (safetyLevel) {
            case "excellent":
                sb.append(String.format("【优秀】桥梁动力性能良好，安全余量充足。建议限重%.0f吨，限速%.0fkm/h。",
                        weightLimit, speedLimit));
                break;
            case "good":
                sb.append(String.format("【良好】桥梁动力性能可接受。建议限重%.0f吨，限速%.0fkm/h，定期检测桥面铺装状况。",
                        weightLimit, speedLimit));
                break;
            case "fair":
                sb.append(String.format("【一般】部分车型动力响应偏大。建议限重%.0f吨，限速%.0fkm/h，",
                        weightLimit, speedLimit));
                sb.append("对重型车辆实行错峰通行，每年检测铺装层完整性。");
                break;
            case "poor":
                sb.append(String.format("【较差】动力响应接近限值！建议限重%.0f吨，限速%.0fkm/h，",
                        weightLimit, speedLimit));
                sb.append("加强桥面铺装维护，考虑增设减振层，限制超限车辆通行。");
                break;
            case "critical":
                sb.append(String.format("【危险】动力响应超出安全限值！紧急建议：限重%.0f吨，限速%.0fkm/h，",
                        weightLimit, speedLimit));
                sb.append("立即实施交通管制，开展专项检测评估，必要时进行加固或铺装改造。");
                break;
        }
        return sb.toString();
    }

    private void validateInput(TrafficVibrationRequestDTO request) {
        if (request.getBridgeId() == null) {
            throw new IllegalArgumentException("桥梁ID不能为空");
        }
        if (request.getVehicleLoads() == null || request.getVehicleLoads().isEmpty()) {
            throw new IllegalArgumentException("车辆荷载数据不能为空");
        }
        for (TrafficVibrationRequestDTO.VehicleLoad load : request.getVehicleLoads()) {
            if (load.getVehicleWeight() == null || load.getVehicleWeight() <= 0) {
                throw new IllegalArgumentException("车辆重量必须为正值: " + load.getVehicleType());
            }
            if (load.getVehicleWeight() > 100) {
                throw new IllegalArgumentException("车辆重量超出合理范围(≤100吨): " + load.getVehicleType());
            }
            if (load.getVehicleSpeed() == null || load.getVehicleSpeed() <= 0) {
                throw new IllegalArgumentException("车辆速度必须为正值: " + load.getVehicleType());
            }
            if (load.getVehicleSpeed() > 150) {
                throw new IllegalArgumentException("车辆速度超出合理范围(≤150km/h): " + load.getVehicleType());
            }
        }
    }

    private static class PavementDampingContext {
        double thickness;
        double dampingRatio;
        double dampingFactor = 1.0;
        String materialType;
        double compositeDamping;
    }
}
