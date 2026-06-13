package com.heritage.bridge.simulation;

import com.heritage.bridge.config.PriorityTopsisProperties;
import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.entity.*;
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
import java.util.stream.IntStream;

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
        Map<String, Double> originalWeights = request.getWeights() != null && !request.getWeights().isEmpty() ?
                request.getWeights() : properties.getDefaultWeights();
        boolean generatePlan = request.getGenerateProtectionPlan() != null ?
                request.getGenerateProtectionPlan() : properties.isProtectionPlanEnabled();

        boolean enableDelphi = request.getEnableDelphiMethod() != null ?
                request.getEnableDelphiMethod() : properties.isDelphiMethodEnabled();
        boolean enableSensitivity = request.getEnableSensitivityAnalysis() != null ?
                request.getEnableSensitivityAnalysis() : properties.isSensitivityAnalysisEnabled();
        boolean useGroupDecision = request.getUseGroupDecision() != null ?
                request.getUseGroupDecision() : (enableDelphi && request.getExpertRatings() != null && !request.getExpertRatings().isEmpty());

        List<Bridge> bridges = bridgeRepository.findAll();
        if (bridges.isEmpty()) {
            throw new IllegalStateException("没有桥梁数据可用于优先级排序");
        }

        String[] criteria = {"structure-safety", "damage-trend", "weathering",
                "traffic-impact", "historical-value"};
        int n = bridges.size();
        int m = criteria.length;

        Map<String, Double> finalWeights = new LinkedHashMap<>(originalWeights);
        DelphiResult delphiResult = null;
        if (enableDelphi && useGroupDecision) {
            delphiResult = performDelphiMethod(request, criteria, bridges);
            double influence = request.getExpertWeightInfluence() != null ?
                    request.getExpertWeightInfluence() : properties.getExpertWeightInfluence();
            finalWeights = mergeWeights(originalWeights, delphiResult.aggregatedWeights, influence);
            log.info("德尔菲法完成，专家共识系数: {:.3f}，合并后权重已应用", delphiResult.consensusCoefficient);
        }

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
        RealMatrix weighted = applyWeights(normalized, finalWeights, criteria);

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

        List<Integer> baseRankings = scores.stream()
                .map(s -> Math.toIntExact(s.bridge.getId()))
                .collect(Collectors.toList());

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
            result.setWeights(finalWeights);
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

        PriorityTopsisResultDTO.PriorityTopsisResultDTOBuilder resultBuilder = PriorityTopsisResultDTO.builder()
                .planYear(planYear)
                .rankings(rankings)
                .weights(finalWeights)
                .protectionPlan(protectionPlan)
                .calculatedAt(LocalDateTime.now())
                .delphiMethodUsed(enableDelphi)
                .groupDecisionUsed(useGroupDecision);

        if (delphiResult != null) {
            resultBuilder
                    .expertCount(delphiResult.expertCount)
                    .expertConsensusCoefficient(delphiResult.consensusCoefficient)
                    .expertAggregatedWeights(delphiResult.aggregatedWeights)
                    .expertResults(delphiResult.expertResults)
                    .groupDecisionReport(buildGroupDecisionReport(delphiResult, rankings));
        }

        if (enableSensitivity) {
            SensitivityAnalysisResult sensitivity = performSensitivityAnalysis(
                    rawData, criteria, finalWeights, baseRankings, bridges, criteriaData);
            resultBuilder
                    .sensitivityAnalysisPerformed(true)
                    .rankingStabilityIndex(sensitivity.overallStability)
                    .criteriaSensitivity(sensitivity.criteriaSensitivity)
                    .sensitivityResults(sensitivity.results);
        } else {
            resultBuilder.sensitivityAnalysisPerformed(false);
        }

        return resultBuilder.build();
    }

    private DelphiResult performDelphiMethod(PriorityTopsisRequestDTO request,
                                             String[] criteria,
                                             List<Bridge> bridges) {
        DelphiResult result = new DelphiResult();
        List<PriorityTopsisRequestDTO.ExpertRating> expertRatings = request.getExpertRatings();

        if (expertRatings == null || expertRatings.isEmpty()) {
            int expertCount = request.getExpertCount() != null ?
                    request.getExpertCount() : properties.getDefaultExpertCount();
            expertRatings = generateSyntheticExperts(expertCount, criteria, bridges);
            result.syntheticExperts = true;
        } else {
            result.syntheticExperts = false;
        }

        result.expertCount = expertRatings.size();

        double[][] expertWeightMatrix = new double[result.expertCount][criteria.length];
        int[][] expertRankingMatrix = new int[result.expertCount][bridges.size()];
        double[] expertWeights = new double[result.expertCount];

        for (int e = 0; e < result.expertCount; e++) {
            PriorityTopsisRequestDTO.ExpertRating rating = expertRatings.get(e);

            if (rating.getExpertWeight() != null) {
                expertWeights[e] = rating.getExpertWeight();
            } else {
                expertWeights[e] = 1.0 / result.expertCount;
            }

            Map<String, Double> expertCritWeights = rating.getCriteriaWeights();
            for (int c = 0; c < criteria.length; c++) {
                if (expertCritWeights != null && expertCritWeights.containsKey(criteria[c])) {
                    expertWeightMatrix[e][c] = expertCritWeights.get(criteria[c]);
                } else {
                    expertWeightMatrix[e][c] = 1.0 / criteria.length;
                }
            }
            expertWeightMatrix[e] = normalizeWeights(expertWeightMatrix[e]);

            Map<String, Double> bridgeScores = rating.getBridgeScores();
            if (bridgeScores != null && !bridgeScores.isEmpty()) {
                List<Map.Entry<String, Double>> sorted = bridgeScores.entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .collect(Collectors.toList());
                for (int r = 0; r < Math.min(sorted.size(), bridges.size()); r++) {
                    Long bridgeId = Long.parseLong(sorted.get(r).getKey());
                    int bridgeIdx = IntStream.range(0, bridges.size())
                            .filter(i -> bridges.get(i).getId().equals(bridgeId))
                            .findFirst().orElse(-1);
                    if (bridgeIdx >= 0) {
                        expertRankingMatrix[e][bridgeIdx] = r + 1;
                    }
                }
            } else {
                for (int b = 0; b < bridges.size(); b++) {
                    expertRankingMatrix[e][b] = b + 1;
                }
            }
        }

        result.consensusCoefficient = calculateKendallW(expertRankingMatrix);

        result.aggregatedWeights = new LinkedHashMap<>();
        for (int c = 0; c < criteria.length; c++) {
            double aggregated = 0;
            for (int e = 0; e < result.expertCount; e++) {
                aggregated += expertWeights[e] * expertWeightMatrix[e][c];
            }
            result.aggregatedWeights.put(criteria[c], aggregated);
        }

        result.expertResults = new ArrayList<>();
        for (int e = 0; e < result.expertCount; e++) {
            PriorityTopsisRequestDTO.ExpertRating rating = expertRatings.get(e);
            Map<String, Double> critW = new LinkedHashMap<>();
            for (int c = 0; c < criteria.length; c++) {
                critW.put(criteria[c], expertWeightMatrix[e][c]);
            }
            List<Integer> rankings = new ArrayList<>();
            for (int b = 0; b < bridges.size(); b++) {
                rankings.add(expertRankingMatrix[e][b]);
            }
            double agreement = calculateExpertAgreement(expertRankingMatrix, e);

            result.expertResults.add(PriorityTopsisResultDTO.ExpertResult.builder()
                    .expertName(rating.getExpertName() != null ? rating.getExpertName() : "专家" + (e + 1))
                    .expertTitle(rating.getExpertTitle() != null ? rating.getExpertTitle() : "未指定")
                    .expertWeight(expertWeights[e])
                    .criteriaWeights(critW)
                    .bridgeRankings(rankings)
                    .rankingAgreement(agreement)
                    .comments(rating.getComments())
                    .build());
        }

        return result;
    }

    private List<PriorityTopsisRequestDTO.ExpertRating> generateSyntheticExperts(
            int count, String[] criteria, List<Bridge> bridges) {
        List<PriorityTopsisRequestDTO.ExpertRating> experts = new ArrayList<>();
        Random random = new Random(42);
        String[] titles = {"结构工程教授", "文物保护专家", "桥梁检测工程师", "历史建筑研究员", "材料科学专家"};

        for (int e = 0; e < count; e++) {
            PriorityTopsisRequestDTO.ExpertRating expert = new PriorityTopsisRequestDTO.ExpertRating();
            expert.setExpertName("专家" + (e + 1));
            expert.setExpertTitle(titles[e % titles.length]);
            expert.setExpertWeight(0.8 + random.nextDouble() * 0.4);

            Map<String, Double> weights = new LinkedHashMap<>();
            for (int c = 0; c < criteria.length; c++) {
                double base = 1.0 / criteria.length;
                double perturbation = (random.nextDouble() - 0.5) * 0.3;
                weights.put(criteria[c], Math.max(0.05, base + perturbation));
            }
            expert.setCriteriaWeights(weights);

            Map<String, Double> bridgeScores = new LinkedHashMap<>();
            for (Bridge bridge : bridges) {
                double baseScore = 50 + (100 - bridge.getHealthScore()) * 0.4;
                double expertBias = (random.nextDouble() - 0.5) * 20;
                bridgeScores.put(String.valueOf(bridge.getId()),
                        Math.max(0, Math.min(100, baseScore + expertBias)));
            }
            expert.setBridgeScores(bridgeScores);
            expert.setComments("基于专业经验的综合评估");
            experts.add(expert);
        }
        return experts;
    }

    private double[] normalizeWeights(double[] weights) {
        double sum = Arrays.stream(weights).sum();
        if (sum <= 0) return weights;
        return Arrays.stream(weights).map(w -> w / sum).toArray();
    }

    private Map<String, Double> mergeWeights(Map<String, Double> original,
                                             Map<String, Double> expert,
                                             double influence) {
        Map<String, Double> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : original.entrySet()) {
            String key = entry.getKey();
            double origVal = entry.getValue();
            double expertVal = expert.getOrDefault(key, origVal);
            double mergedVal = origVal * (1 - influence) + expertVal * influence;
            merged.put(key, mergedVal);
        }

        double sum = merged.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Map.Entry<String, Double> entry : merged.entrySet()) {
            entry.setValue(entry.getValue() / sum);
        }
        return merged;
    }

    private double calculateKendallW(int[][] rankings) {
        int m = rankings.length;
        int n = rankings[0].length;

        double[] sumRanks = new double[n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sumRanks[j] += rankings[i][j];
            }
        }

        double meanRank = m * (n + 1) / 2.0;
        double S = 0;
        for (double r : sumRanks) {
            S += Math.pow(r - meanRank, 2);
        }

        double maxS = m * m * n * (n * n - 1) / 12.0;

        return maxS > 0 ? S / maxS : 0;
    }

    private double calculateExpertAgreement(int[][] rankings, int expertIdx) {
        int n = rankings.length;
        double totalAgreement = 0;
        for (int i = 0; i < n; i++) {
            if (i == expertIdx) continue;
            double correlation = calculateSpearmanRankCorrelation(
                    rankings[expertIdx], rankings[i]);
            totalAgreement += (correlation + 1) / 2;
        }
        return totalAgreement / (n - 1);
    }

    private double calculateSpearmanRankCorrelation(int[] ranks1, int[] ranks2) {
        int n = ranks1.length;
        double sumD2 = 0;
        for (int i = 0; i < n; i++) {
            sumD2 += Math.pow(ranks1[i] - ranks2[i], 2);
        }
        return 1 - (6 * sumD2) / (double) (n * (n * n - 1));
    }

    private PriorityTopsisResultDTO.GroupDecisionReport buildGroupDecisionReport(
            DelphiResult delphi,
            List<PriorityTopsisResultDTO.BridgePriority> finalRankings) {

        double consensus = delphi.consensusCoefficient;
        String interpretation;
        if (consensus >= 0.8) interpretation = "专家共识度极高，意见高度统一";
        else if (consensus >= 0.6) interpretation = "专家共识度良好，意见基本一致";
        else if (consensus >= 0.4) interpretation = "专家共识度一般，存在一定分歧";
        else interpretation = "专家共识度较低，存在较大分歧，建议组织专家研讨会";

        StringBuilder recommendation = new StringBuilder();
        recommendation.append("基于").append(delphi.expertCount).append("位专家的德尔菲法群决策，");
        recommendation.append("最终排序结果已综合专家意见和客观数据。");
        if (consensus < properties.getMinExpertConsensus()) {
            recommendation.append("【注意】专家共识度低于阈值(").append(properties.getMinExpertConsensus()).append(")，");
            recommendation.append("建议进一步咨询或组织现场勘察。");
        }

        StringBuilder minorityOpinions = new StringBuilder();
        for (PriorityTopsisResultDTO.ExpertResult er : delphi.expertResults) {
            if (er.getRankingAgreement() != null && er.getRankingAgreement() < 0.5) {
                minorityOpinions.append(er.getExpertName()).append("(").append(er.getExpertTitle()).append(")");
                if (er.getComments() != null) minorityOpinions.append(": ").append(er.getComments());
                minorityOpinions.append("; ");
            }
        }

        return PriorityTopsisResultDTO.GroupDecisionReport.builder()
                .totalExperts(delphi.expertCount)
                .consensusLevel(consensus)
                .consensusInterpretation(interpretation)
                .finalRecommendation(recommendation.toString())
                .minorityOpinions(minorityOpinions.length() > 0 ? minorityOpinions.toString() : "无显著分歧意见")
                .build();
    }

    private SensitivityAnalysisResult performSensitivityAnalysis(
            double[][] rawData, String[] criteria, Map<String, Double> weights,
            List<Integer> baseRankings, List<Bridge> bridges,
            Map<String, Object>[] criteriaData) {

        SensitivityAnalysisResult result = new SensitivityAnalysisResult();
        int n = rawData.length;
        int m = criteria.length;

        int perturbations = properties.getSensitivityPerturbationCount();
        double range = properties.getSensitivityPerturbationRange();
        Random random = new Random(12345);

        result.criteriaSensitivity = new LinkedHashMap<>();
        result.results = new ArrayList<>();

        int[] baseRankArray = baseRankings.stream().mapToInt(Integer::intValue).toArray();
        double totalStability = 0;

        for (int c = 0; c < m; c++) {
            String criterion = criteria[c];
            DescriptiveStatistics correlationStats = new DescriptiveStatistics();
            DescriptiveStatistics stabilityStats = new DescriptiveStatistics();

            for (int p = 0; p < perturbations; p++) {
                Map<String, Double> perturbedWeights = new LinkedHashMap<>(weights);
                double originalWeight = weights.get(criterion);
                double delta = (random.nextDouble() - 0.5) * 2 * range;
                double newWeight = Math.max(0.01, Math.min(0.9, originalWeight * (1 + delta)));
                perturbedWeights.put(criterion, newWeight);

                double sum = perturbedWeights.values().stream().mapToDouble(Double::doubleValue).sum();
                for (Map.Entry<String, Double> e : perturbedWeights.entrySet()) {
                    e.setValue(e.getValue() / sum);
                }

                List<Integer> perturbedRanking = calculateRanking(rawData, criteria, perturbedWeights, bridges, criteriaData);
                int[] perturbedArray = perturbedRanking.stream().mapToInt(Integer::intValue).toArray();

                double correlation = calculateSpearmanRankCorrelation(baseRankArray, perturbedArray);
                double stability = countStableRanks(baseRankArray, perturbedArray, 3);

                correlationStats.addValue(Math.max(0, correlation));
                stabilityStats.addValue(stability);
            }

            double avgCorrelation = correlationStats.getMean();
            result.criteriaSensitivity.put(criterion, 1.0 - avgCorrelation);
            totalStability += stabilityStats.getMean();

            result.results.add(PriorityTopsisResultDTO.SensitivityResult.builder()
                    .criteriaName(criterion)
                    .weightPerturbation(range)
                    .originalRankings(Arrays.stream(baseRankArray).boxed().collect(Collectors.toList()))
                    .perturbedRankings(null)
                    .rankingCorrelation(avgCorrelation)
                    .stabilityScore(stabilityStats.getMean() / (double) n)
                    .build());
        }

        result.overallStability = totalStability / m / n;

        return result;
    }

    private List<Integer> calculateRanking(double[][] rawData, String[] criteria,
                                           Map<String, Double> weights,
                                           List<Bridge> bridges,
                                           Map<String, Object>[] criteriaData) {
        RealMatrix normalized = normalizeMatrix(rawData);
        RealMatrix weighted = applyWeights(normalized, weights, criteria);
        double[] positiveIdeal = findIdealSolution(weighted, true);
        double[] negativeIdeal = findIdealSolution(weighted, false);

        List<BridgePriorityScore> scores = new ArrayList<>();
        for (int i = 0; i < bridges.size(); i++) {
            double dPlus = calculateDistance(weighted.getRow(i), positiveIdeal);
            double dMinus = calculateDistance(weighted.getRow(i), negativeIdeal);
            double closeness = dMinus / (dPlus + dMinus);
            scores.add(new BridgePriorityScore(bridges.get(i), closeness, rawData[i], criteriaData[i]));
        }

        scores.sort((a, b) -> Double.compare(b.closeness, a.closeness));
        return scores.stream().map(s -> Math.toIntExact(s.bridge.getId())).collect(Collectors.toList());
    }

    private int countStableRanks(int[] ranks1, int[] ranks2, int topN) {
        int stable = 0;
        Map<Integer, Integer> pos1 = new HashMap<>();
        Map<Integer, Integer> pos2 = new HashMap<>();
        for (int i = 0; i < ranks1.length; i++) {
            pos1.put(ranks1[i], i + 1);
            pos2.put(ranks2[i], i + 1);
        }
        for (int id : ranks1) {
            if (pos1.containsKey(id) && pos2.containsKey(id)) {
                int r1 = pos1.get(id);
                int r2 = pos2.get(id);
                if (r1 <= topN && r2 <= topN) stable++;
            }
        }
        return stable;
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
            double safetyMargin = va.getSafetyMargin() != null ? va.getSafetyMargin() : 1.5;

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

    private static class DelphiResult {
        int expertCount;
        double consensusCoefficient;
        Map<String, Double> aggregatedWeights;
        List<PriorityTopsisResultDTO.ExpertResult> expertResults;
        boolean syntheticExperts;
    }

    private static class SensitivityAnalysisResult {
        double overallStability;
        Map<String, Double> criteriaSensitivity;
        List<PriorityTopsisResultDTO.SensitivityResult> results;
    }
}
