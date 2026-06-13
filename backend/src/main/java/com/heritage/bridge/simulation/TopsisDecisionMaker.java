package com.heritage.bridge.simulation;

import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.entity.*;
import com.heritage.bridge.config.PriorityTopsisProperties;
import com.heritage.bridge.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TopsisDecisionMaker {

    private final PriorityTopsisProperties properties;
    private final BridgeRepository bridgeRepository;
    private final FemResultRepository femResultRepository;
    private final DamagePredictionRepository damagePredictionRepository;
    private final WeatheringDataRepository weatheringDataRepository;
    private final TrafficVibrationAnalysisRepository vibrationRepository;
    private final AnnualProtectionPlanRepository planRepository;

    public TopsisDecisionMaker(PriorityTopsisProperties properties,
                               BridgeRepository bridgeRepository,
                               FemResultRepository femResultRepository,
                               DamagePredictionRepository damagePredictionRepository,
                               WeatheringDataRepository weatheringDataRepository,
                               TrafficVibrationAnalysisRepository vibrationRepository,
                               AnnualProtectionPlanRepository planRepository) {
        this.properties = properties;
        this.bridgeRepository = bridgeRepository;
        this.femResultRepository = femResultRepository;
        this.damagePredictionRepository = damagePredictionRepository;
        this.weatheringDataRepository = weatheringDataRepository;
        this.vibrationRepository = vibrationRepository;
        this.planRepository = planRepository;
    }

    public PriorityTopsisResultDTO calculate(PriorityTopsisRequestDTO request) {
        int planYear = request.getPlanYear() != null ? request.getPlanYear() : properties.getDefaultPlanYear();
        Map<String, Double> weights = request.getWeights() != null && !request.getWeights().isEmpty() ?
                request.getWeights() : properties.getDefaultWeights();
        boolean generatePlan = request.getGenerateProtectionPlan() != null ?
                request.getGenerateProtectionPlan() : properties.isProtectionPlanEnabled();

        List<Bridge> bridges = bridgeRepository.findAll();
        if (bridges.isEmpty()) {
            throw new IllegalStateException("没有桥梁数据可用于优先级排序");
        }

        String[] criteria = {"structure-safety", "damage-trend", "weathering",
                "traffic-impact", "historical-value"};
        int n = bridges.size();
        int m = criteria.length;

        double[][] rawData = new double[n][m];
        Map<String, Object>[] criteriaData = new Map[n];
        List<BridgePriorityResult> results = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Bridge bridge = bridges.get(i);
            criteriaData[i] = new LinkedHashMap<>();

            rawData[i][0] = calculateStructureSafetyScore(bridge, criteriaData[i]);
            rawData[i][1] = calculateDamageTrendScore(bridge, criteriaData[i]);
            rawData[i][2] = calculateWeatheringScore(bridge, criteriaData[i]);
            rawData[i][3] = calculateTrafficImpactScore(bridge, criteriaData[i]);
            rawData[i][4] = calculateHistoricalValueScore(bridge, criteriaData[i]);
        }

        RealMatrix normalized = normalizeMatrix(rawData);
        RealMatrix weighted = applyWeights(normalized, weights, criteria);

        double[] positiveIdeal = findIdealSolution(weighted, true);
        double[] negativeIdeal = findIdealSolution(weighted, false);

        List<BridgePriorityScore> scores = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double dPlus = calculateDistance(weighted.getRow(i), positiveIdeal);
            double dMinus = calculateDistance(weighted.getRow(i), negativeIdeal);
            double closeness = dMinus / (dPlus + dMinus);

            scores.add(new BridgePriorityScore(bridges.get(i), closeness, rawData[i], criteriaData[i]));
        }

        scores.sort((a, b) -> Double.compare(b.closeness, a.closeness));

        List<PriorityTopsisResultDTO.BridgePriority> rankings = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            BridgePriorityScore bps = scores.get(i);
            int ranking = i + 1;

            String urgency = determineUrgency(bps.closeness);
            String priorityLevel = determinePriorityLevel(ranking);
            double estimatedCost = estimateCost(bps.closeness, bps.rawScores);
            String recommendation = generateActionRecommendation(ranking, urgency, bps.bridge, bps.rawScores);

            BridgePriorityResult result = new BridgePriorityResult();
            result.setBridgeId(bps.bridge.getId());
            result.setRanking(ranking);
            result.setTopsisScore(bps.closeness);
            result.setStructureSafetyScore(bps.rawScores[0]);
            result.setDamageTrendScore(bps.rawScores[1]);
            result.setWeatheringScore(bps.rawScores[2]);
            result.setTrafficImpactScore(bps.rawScores[3]);
            result.setHistoricalValueScore(bps.rawScores[4]);
            result.setMaintenanceUrgency(urgency);
            result.setEstimatedCost(estimatedCost);
            result.setPriorityLevel(priorityLevel);
            result.setActionRecommendation(recommendation);
            result.setWeights(weights);
            result.setCriteriaData(bps.criteriaData);

            results.add(result);

            rankings.add(PriorityTopsisResultDTO.BridgePriority.builder()
                    .bridgeId(bps.bridge.getId())
                    .bridgeName(bps.bridge.getName())
                    .ranking(ranking)
                    .topsisScore(Math.round(bps.closeness * 10000.0) / 10000.0)
                    .structureSafetyScore(Math.round(bps.rawScores[0] * 100.0) / 100.0)
                    .damageTrendScore(Math.round(bps.rawScores[1] * 100.0) / 100.0)
                    .weatheringScore(Math.round(bps.rawScores[2] * 100.0) / 100.0)
                    .trafficImpactScore(Math.round(bps.rawScores[3] * 100.0) / 100.0)
                    .historicalValueScore(Math.round(bps.rawScores[4] * 100.0) / 100.0)
                    .maintenanceUrgency(urgency)
                    .estimatedCost(estimatedCost)
                    .priorityLevel(priorityLevel)
                    .actionRecommendation(recommendation)
                    .build());
        }

        List<PriorityTopsisResultDTO.AnnualPlanItem> protectionPlan = new ArrayList<>();
        if (generatePlan) {
            protectionPlan = generateAnnualProtectionPlan(results, rankings, planYear);
        }

        return PriorityTopsisResultDTO.builder()
                .planYear(planYear)
                .rankings(rankings)
                .weights(weights)
                .protectionPlan(protectionPlan)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private double calculateStructureSafetyScore(Bridge bridge, Map<String, Object> criteriaData) {
        Optional<FemResult> latestFem = femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(bridge.getId());

        double safetyFactor = latestFem.map(FemResult::getSafetyFactor).orElse(2.0);
        double healthScore = bridge.getHealthScore() / 100.0;
        double pfFailure = latestFem.map(FemResult::getPfFailure).orElse(0.001);

        double score = 0.4 * Math.min(1.0, safetyFactor / 3.0)
                + 0.4 * healthScore
                + 0.2 * (1.0 - Math.min(1.0, pfFailure / 0.1));

        criteriaData.put("safety_factor", safetyFactor);
        criteriaData.put("health_score", bridge.getHealthScore());
        criteriaData.put("pf_failure", pfFailure);

        return Math.max(0, Math.min(1, score));
    }

    private double calculateDamageTrendScore(Bridge bridge, Map<String, Object> criteriaData) {
        List<DamagePrediction> predictions = damagePredictionRepository
                .findByBridgeIdOrderByPredictedAtDesc(bridge.getId());

        double score;
        int maintenanceYear = 9999;

        if (!predictions.isEmpty()) {
            DamagePrediction latest = predictions.get(0);
            maintenanceYear = latest.getMaintenanceYear() != null ? latest.getMaintenanceYear() : 9999;
            int yearsToMaintenance = maintenanceYear - Year.now().getValue();

            if (yearsToMaintenance <= 0) score = 1.0;
            else if (yearsToMaintenance <= 1) score = 0.85;
            else if (yearsToMaintenance <= 3) score = 0.6;
            else if (yearsToMaintenance <= 5) score = 0.35;
            else score = 0.1;

            criteriaData.put("paris_c", latest.getParisC());
            criteriaData.put("paris_m", latest.getParisM());
            criteriaData.put("maintenance_year", maintenanceYear);
            criteriaData.put("years_to_maintenance", yearsToMaintenance);
        } else {
            score = 0.3;
            criteriaData.put("prediction_available", false);
        }

        criteriaData.put("damage_score", score);
        return score;
    }

    private double calculateWeatheringScore(Bridge bridge, Map<String, Object> criteriaData) {
        Double avgDepth = weatheringDataRepository.findAverageDepthByBridgeId(bridge.getId());
        Double maxDepth = weatheringDataRepository.findMaxDepthByBridgeId(bridge.getId());

        double score;
        if (avgDepth != null) {
            if (avgDepth >= 15) score = 1.0;
            else if (avgDepth >= 10) score = 0.8;
            else if (avgDepth >= 5) score = 0.55;
            else if (avgDepth >= 2) score = 0.3;
            else score = 0.1;

            criteriaData.put("avg_weathering_depth", avgDepth);
            criteriaData.put("max_weathering_depth", maxDepth);
        } else {
            score = 0.3;
            criteriaData.put("weathering_data_available", false);
        }

        criteriaData.put("weathering_score", score);
        return score;
    }

    private double calculateTrafficImpactScore(Bridge bridge, Map<String, Object> criteriaData) {
        Optional<TrafficVibrationAnalysis> latestVib =
                vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(bridge.getId());

        double score;
        if (latestVib.isPresent()) {
            TrafficVibrationAnalysis va = latestVib.get();
            double safetyMargin = va.getSafetyMargin();

            if (safetyMargin < 0.7) score = 1.0;
            else if (safetyMargin < 1.0) score = 0.8;
            else if (safetyMargin < 1.5) score = 0.55;
            else if (safetyMargin < 2.0) score = 0.3;
            else score = 0.1;

            criteriaData.put("vibration_safety_margin", safetyMargin);
            criteriaData.put("max_acceleration", va.getMaxAcceleration());
            criteriaData.put("weight_limit", va.getAllowableWeightLimit());
            criteriaData.put("speed_limit", va.getAllowableSpeedLimit());
        } else {
            score = 0.3;
            criteriaData.put("vibration_data_available", false);
        }

        criteriaData.put("traffic_impact_score", score);
        return score;
    }

    private double calculateHistoricalValueScore(Bridge bridge, Map<String, Object> criteriaData) {
        int builtYear = bridge.getBuiltYear() != null ? bridge.getBuiltYear() : 1900;
        int age = Year.now().getValue() - builtYear;

        double ageScore;
        if (age >= 1000) ageScore = 1.0;
        else if (age >= 500) ageScore = 0.85;
        else if (age >= 200) ageScore = 0.65;
        else if (age >= 100) ageScore = 0.45;
        else ageScore = 0.2;

        String name = bridge.getName();
        double fameScore = 0.5;
        if (name.contains("赵州") || name.contains("卢沟") || name.contains("广济") || name.contains("洛阳")) {
            fameScore = 1.0;
        } else if (name.contains("宝带") || name.contains("安平") || name.contains("五亭")) {
            fameScore = 0.8;
        } else if (name.contains("泸定") || name.contains("程阳") || name.contains("拱宸")) {
            fameScore = 0.7;
        }

        double score = 0.6 * ageScore + 0.4 * fameScore;

        criteriaData.put("built_year", builtYear);
        criteriaData.put("bridge_age", age);
        criteriaData.put("age_score", ageScore);
        criteriaData.put("fame_score", fameScore);
        criteriaData.put("historical_value_score", score);

        return score;
    }

    private RealMatrix normalizeMatrix(double[][] raw) {
        int n = raw.length;
        int m = raw[0].length;
        double[][] normalized = new double[n][m];

        for (int j = 0; j < m; j++) {
            double sumSq = 0;
            for (int i = 0; i < n; i++) {
                sumSq += raw[i][j] * raw[i][j];
            }
            double norm = Math.sqrt(sumSq);
            for (int i = 0; i < n; i++) {
                normalized[i][j] = norm > 0 ? raw[i][j] / norm : 0;
            }
        }
        return new Array2DRowRealMatrix(normalized);
    }

    private RealMatrix applyWeights(RealMatrix normalized, Map<String, Double> weights, String[] criteria) {
        double[][] data = normalized.getData();
        for (int j = 0; j < criteria.length; j++) {
            double w = weights.getOrDefault(criteria[j], 1.0 / criteria.length);
            for (int i = 0; i < data.length; i++) {
                data[i][j] *= w;
            }
        }
        return new Array2DRowRealMatrix(data);
    }

    private double[] findIdealSolution(RealMatrix weighted, boolean isPositive) {
        int m = weighted.getColumnDimension();
        double[] ideal = new double[m];

        for (int j = 0; j < m; j++) {
            double[] col = weighted.getColumn(j);
            if (isPositive) {
                ideal[j] = Arrays.stream(col).max().orElse(0);
            } else {
                ideal[j] = Arrays.stream(col).min().orElse(0);
            }
        }
        return ideal;
    }

    private double calculateDistance(double[] point, double[] ideal) {
        double sum = 0;
        for (int i = 0; i < point.length; i++) {
            sum += Math.pow(point[i] - ideal[i], 2);
        }
        return Math.sqrt(sum);
    }

    private String determineUrgency(double closeness) {
        if (closeness >= properties.getUrgencyImmediateMax()) return "immediate";
        if (closeness >= properties.getUrgencyUrgentMax()) return "urgent";
        if (closeness >= properties.getUrgencyNormalMax()) return "normal";
        return "low";
    }

    private String determinePriorityLevel(int ranking) {
        if (ranking <= properties.getPriorityCriticalMax()) return "critical";
        if (ranking <= properties.getPriorityHighMax()) return "high";
        return "normal";
    }

    private double estimateCost(double closeness, double[] rawScores) {
        double baseCost = 50000.0;
        double urgencyMultiplier = 1.0 + closeness * 2.0;

        double conditionFactor = 0;
        conditionFactor += rawScores[0] * 0.3;
        conditionFactor += rawScores[1] * 0.3;
        conditionFactor += rawScores[2] * 0.2;
        conditionFactor += rawScores[3] * 0.2;

        return Math.round(baseCost * urgencyMultiplier * (0.5 + conditionFactor) * 100.0) / 100.0;
    }

    private String generateActionRecommendation(int ranking, String urgency, Bridge bridge, double[] scores) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("【排名第%d - %s】", ranking, getUrgencyName(urgency)));

        List<String> issues = new ArrayList<>();
        if (scores[0] > 0.7) issues.add("结构安全度不足");
        if (scores[1] > 0.7) issues.add("损伤演化趋势明显");
        if (scores[2] > 0.7) issues.add("风化严重");
        if (scores[3] > 0.7) issues.add("交通振动影响大");

        if (!issues.isEmpty()) {
            sb.append("主要问题：").append(String.join("、", issues)).append("。");
        }

        switch (urgency) {
            case "immediate":
                sb.append("建议立即启动抢险加固工程，组织专家论证会，编制专项保护方案。");
                break;
            case "urgent":
                sb.append("建议6个月内安排专项加固，增加监测频率，限制重型交通。");
                break;
            case "normal":
                sb.append("建议1-2年内安排常规维护，继续定期监测。");
                break;
            default:
                sb.append("建议维持常规监测，每2-3年进行一次全面检测。");
                break;
        }

        return sb.toString();
    }

    private String getUrgencyName(String urgency) {
        switch (urgency) {
            case "immediate": return "立即处理";
            case "urgent": return "紧急";
            case "normal": return "一般";
            default: return "低";
        }
    }

    private List<PriorityTopsisResultDTO.AnnualPlanItem> generateAnnualProtectionPlan(
            List<BridgePriorityResult> results,
            List<PriorityTopsisResultDTO.BridgePriority> rankings,
            int planYear) {

        List<PriorityTopsisResultDTO.AnnualPlanItem> plan = new ArrayList<>();
        Map<Long, String> bridgeNames = rankings.stream()
                .collect(Collectors.toMap(
                        PriorityTopsisResultDTO.BridgePriority::getBridgeId,
                        PriorityTopsisResultDTO.BridgePriority::getBridgeName));

        for (BridgePriorityResult result : results) {
            String bridgeName = bridgeNames.get(result.getBridgeId());
            String projectType = determineProjectType(result);
            String projectName = generateProjectName(bridgeName, result.getPriorityLevel(), projectType);
            String timeline = determineTimeline(result.getRanking());

            AnnualProtectionPlan planEntity = new AnnualProtectionPlan();
            planEntity.setPlanYear(planYear);
            planEntity.setBridgeId(result.getBridgeId());
            planEntity.setPriorityRanking(result.getRanking());
            planEntity.setProjectName(projectName);
            planEntity.setProjectType(projectType);
            planEntity.setEstimatedBudget(result.getEstimatedCost());
            planEntity.setTimeline(timeline);
            planEntity.setDescription(result.getActionRecommendation());

            planRepository.save(planEntity);

            plan.add(PriorityTopsisResultDTO.AnnualPlanItem.builder()
                    .id(planEntity.getId())
                    .planYear(planYear)
                    .bridgeId(result.getBridgeId())
                    .bridgeName(bridgeName)
                    .priorityRanking(result.getRanking())
                    .projectName(projectName)
                    .projectType(projectType)
                    .estimatedBudget(result.getEstimatedCost())
                    .timeline(timeline)
                    .status(planEntity.getStatus())
                    .description(result.getActionRecommendation())
                    .build());
        }

        log.info("成功生成{}年度保护计划，共{}个项目", planYear, plan.size());
        return plan;
    }

    private String determineProjectType(BridgePriorityResult result) {
        double[] scores = {
                result.getStructureSafetyScore(),
                result.getDamageTrendScore(),
                result.getWeatheringScore(),
                result.getTrafficImpactScore()
        };

        int maxIdx = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[maxIdx]) maxIdx = i;
        }

        switch (maxIdx) {
            case 0: return "structural_reinforcement";
            case 1: return "crack_repair";
            case 2: return "weathering_protection";
            case 3: return "vibration_control";
            default: return "comprehensive_maintenance";
        }
    }

    private String generateProjectName(String bridgeName, String priorityLevel, String projectType) {
        String typeName;
        switch (projectType) {
            case "structural_reinforcement": typeName = "结构加固工程"; break;
            case "crack_repair": typeName = "裂缝修复工程"; break;
            case "weathering_protection": typeName = "防风化保护工程"; break;
            case "vibration_control": typeName = "减振控制工程"; break;
            default: typeName = "综合养护工程";
        }

        String levelPrefix = "";
        if ("critical".equals(priorityLevel)) levelPrefix = "【紧急】";
        else if ("high".equals(priorityLevel)) levelPrefix = "【重点】";

        return levelPrefix + bridgeName + typeName;
    }

    private String determineTimeline(int ranking) {
        if (ranking <= 3) return "Q1-Q2";
        if (ranking <= 6) return "Q2-Q3";
        return "Q3-Q4";
    }

    private static class BridgePriorityScore {
        Bridge bridge;
        double closeness;
        double[] rawScores;
        Map<String, Object> criteriaData;

        BridgePriorityScore(Bridge bridge, double closeness, double[] rawScores,
                            Map<String, Object> criteriaData) {
            this.bridge = bridge;
            this.closeness = closeness;
            this.rawScores = rawScores;
            this.criteriaData = criteriaData;
        }
    }
}