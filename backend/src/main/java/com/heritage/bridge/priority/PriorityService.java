package com.heritage.bridge.priority;

import com.heritage.bridge.config.PriorityTopsisProperties;
import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.entity.AnnualProtectionPlan;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.BridgePriorityResult;
import com.heritage.bridge.event.DamagePredictedEvent;
import com.heritage.bridge.event.FemResultEvent;
import com.heritage.bridge.event.PriorityCalculatedEvent;
import com.heritage.bridge.event.WeatheringEvaluatedEvent;
import com.heritage.bridge.event.VibrationAnalyzedEvent;
import com.heritage.bridge.repository.AnnualProtectionPlanRepository;
import com.heritage.bridge.repository.BridgePriorityResultRepository;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.simulation.TopsisDecisionMaker;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriorityService {

    private final BridgePriorityResultRepository priorityRepository;
    private final AnnualProtectionPlanRepository planRepository;
    private final BridgeRepository bridgeRepository;
    private final TopsisDecisionMaker decisionMaker;
    private final PriorityTopsisProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PriorityTopsisResultDTO calculatePriorities(PriorityTopsisRequestDTO request) {
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        savePriorityResults(result);
        publishPriorityCalculatedEvent(result, PriorityCalculatedEvent.Trigger.ON_DEMAND);

        log.info("完成桥梁保护优先级排序，共{}座桥梁参与排序", result.getRankings().size());
        return result;
    }

    @Scheduled(cron = "${priority.topsis.scheduled-cron:0 0 2 1 * ?}")
    @Transactional
    public void scheduledMonthlyPriorityCalculation() {
        if (!properties.isScheduledEnabled()) {
            return;
        }

        log.info("开始执行月度桥梁保护优先级自动计算");
        try {
            PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
            request.setGenerateProtectionPlan(true);
            PriorityTopsisResultDTO result = decisionMaker.calculate(request);

            savePriorityResults(result);
            publishPriorityCalculatedEvent(result, PriorityCalculatedEvent.Trigger.SCHEDULED);

            log.info("月度桥梁保护优先级计算完成，共{}座桥梁", result.getRankings().size());
        } catch (Exception e) {
            log.error("月度桥梁保护优先级计算失败", e);
        }
    }

    @Async
    @EventListener
    public void onFemResultEvent(FemResultEvent event) {
        if (properties.isAutoTriggerOnFemResult()) {
            triggerRecalculation(event.getBridgeId(), "FEM结果更新");
        }
    }

    @Async
    @EventListener
    public void onDamagePredictedEvent(DamagePredictedEvent event) {
        if (properties.isAutoTriggerOnDamagePrediction()) {
            triggerRecalculation(event.getBridgeId(), "损伤预测更新");
        }
    }

    @Async
    @EventListener
    public void onWeatheringEvaluatedEvent(WeatheringEvaluatedEvent event) {
        if (properties.isAutoTriggerOnWeathering()) {
            triggerRecalculation(event.getBridgeId(), "风化评估更新");
        }
    }

    @Async
    @EventListener
    public void onVibrationAnalyzedEvent(VibrationAnalyzedEvent event) {
        if (properties.isAutoTriggerOnVibration()) {
            triggerRecalculation(event.getBridgeId(), "振动分析更新");
        }
    }

    private void triggerRecalculation(Long bridgeId, String reason) {
        log.info("因{}触发桥梁{}优先级重计算", reason, bridgeId);
        try {
            PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
            request.setGenerateProtectionPlan(false);
            PriorityTopsisResultDTO result = decisionMaker.calculate(request);

            savePriorityResults(result);
            publishPriorityCalculatedEvent(result, PriorityCalculatedEvent.Trigger.DATA_TRIGGERED);
        } catch (Exception e) {
            log.error("触发优先级重计算失败", e);
        }
    }

    private void savePriorityResults(PriorityTopsisResultDTO result) {
        int planYear = result.getPlanYear();

        priorityRepository.deleteByPlanYear(planYear);
        planRepository.deleteByPlanYear(planYear);

        for (PriorityTopsisResultDTO.BridgePriority bp : result.getRankings()) {
            BridgePriorityResult entity = new BridgePriorityResult();
            entity.setBridgeId(bp.getBridgeId());
            entity.setPlanYear(planYear);
            entity.setRanking(bp.getRanking());
            entity.setTopsisScore(bp.getTopsisScore());
            entity.setStructureSafetyScore(bp.getStructureSafetyScore());
            entity.setDamageTrendScore(bp.getDamageTrendScore());
            entity.setWeatheringScore(bp.getWeatheringScore());
            entity.setTrafficImpactScore(bp.getTrafficImpactScore());
            entity.setHistoricalValueScore(bp.getHistoricalValueScore());
            entity.setMaintenanceUrgency(bp.getMaintenanceUrgency());
            entity.setEstimatedCost(bp.getEstimatedCost());
            entity.setPriorityLevel(bp.getPriorityLevel());
            entity.setActionRecommendation(bp.getActionRecommendation());
            entity.setWeights(result.getWeights());
            entity.setCalculatedAt(result.getCalculatedAt());
            priorityRepository.save(entity);
        }
    }

    private void publishPriorityCalculatedEvent(PriorityTopsisResultDTO result,
                                                PriorityCalculatedEvent.Trigger trigger) {
        PriorityCalculatedEvent event = new PriorityCalculatedEvent(this, result, trigger);
        eventPublisher.publishEvent(event);
    }

    public List<PriorityTopsisResultDTO.BridgePriority> getLatestRankings(Integer planYear) {
        int year = planYear != null ? planYear : properties.getDefaultPlanYear();
        List<BridgePriorityResult> results = priorityRepository.findByPlanYearOrderByRankingAsc(year);

        if (results.isEmpty()) {
            return new ArrayList<>();
        }

        return results.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<PriorityTopsisResultDTO.BridgePriority> getBridgeRanking(Long bridgeId, Integer planYear) {
        int year = planYear != null ? planYear : properties.getDefaultPlanYear();
        return priorityRepository.findByBridgeIdAndPlanYear(bridgeId, year)
                .map(this::convertToDTO);
    }

    public List<PriorityTopsisResultDTO.AnnualPlanItem> getAnnualProtectionPlan(Integer planYear) {
        int year = planYear != null ? planYear : properties.getDefaultPlanYear();
        List<AnnualProtectionPlan> plans = planRepository.findByPlanYearOrderByPriorityRankingAsc(year);

        return plans.stream()
                .map(this::convertPlanToDTO)
                .collect(Collectors.toList());
    }

    public List<PriorityTopsisResultDTO.AnnualPlanItem> getBridgeProtectionPlan(Long bridgeId) {
        List<AnnualProtectionPlan> plans = planRepository.findByBridgeIdOrderByPlanYearDesc(bridgeId);

        return plans.stream()
                .map(this::convertPlanToDTO)
                .collect(Collectors.toList());
    }

    public List<Integer> getAvailablePlanYears() {
        return priorityRepository.findDistinctPlanYears();
    }

    private PriorityTopsisResultDTO.BridgePriority convertToDTO(BridgePriorityResult entity) {
        Bridge bridge = bridgeRepository.findById(entity.getBridgeId()).orElse(null);
        String bridgeName = bridge != null ? bridge.getName() : "未知桥梁";

        return PriorityTopsisResultDTO.BridgePriority.builder()
                .id(entity.getId())
                .bridgeId(entity.getBridgeId())
                .bridgeName(bridgeName)
                .ranking(entity.getRanking())
                .topsisScore(entity.getTopsisScore())
                .structureSafetyScore(entity.getStructureSafetyScore())
                .damageTrendScore(entity.getDamageTrendScore())
                .weatheringScore(entity.getWeatheringScore())
                .trafficImpactScore(entity.getTrafficImpactScore())
                .historicalValueScore(entity.getHistoricalValueScore())
                .maintenanceUrgency(entity.getMaintenanceUrgency())
                .estimatedCost(entity.getEstimatedCost())
                .priorityLevel(entity.getPriorityLevel())
                .actionRecommendation(entity.getActionRecommendation())
                .build();
    }

    private PriorityTopsisResultDTO.AnnualPlanItem convertPlanToDTO(AnnualProtectionPlan entity) {
        Bridge bridge = bridgeRepository.findById(entity.getBridgeId()).orElse(null);
        String bridgeName = bridge != null ? bridge.getName() : "未知桥梁";

        return PriorityTopsisResultDTO.AnnualPlanItem.builder()
                .id(entity.getId())
                .planYear(entity.getPlanYear())
                .bridgeId(entity.getBridgeId())
                .bridgeName(bridgeName)
                .priorityRanking(entity.getPriorityRanking())
                .projectName(entity.getProjectName())
                .projectType(entity.getProjectType())
                .estimatedBudget(entity.getEstimatedBudget())
                .timeline(entity.getTimeline())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .build();
    }
}
