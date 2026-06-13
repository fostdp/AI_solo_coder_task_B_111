package com.heritage.bridge.masonry;

import com.heritage.bridge.config.MasonryDemProperties;
import com.heritage.bridge.dto.MasonryDemRequestDTO;
import com.heritage.bridge.dto.MasonryDemResultDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.MasonryDemResult;
import com.heritage.bridge.entity.MasonryParams;
import com.heritage.bridge.event.MasonrySimulatedEvent;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.MasonryDemResultRepository;
import com.heritage.bridge.repository.MasonryParamsRepository;
import com.heritage.bridge.simulation.DiscreteElementSolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class MasonrySimulationService {

    private final MasonryDemResultRepository demRepository;
    private final MasonryParamsRepository paramsRepository;
    private final BridgeRepository bridgeRepository;
    private final DiscreteElementSolver solver;
    private final MasonryDemProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor demTaskExecutor;

    public MasonrySimulationService(MasonryDemResultRepository demRepository,
                                    MasonryParamsRepository paramsRepository,
                                    BridgeRepository bridgeRepository,
                                    DiscreteElementSolver solver,
                                    MasonryDemProperties properties,
                                    ApplicationEventPublisher eventPublisher,
                                    @Qualifier("demTaskExecutor") Executor demTaskExecutor) {
        this.demRepository = demRepository;
        this.paramsRepository = paramsRepository;
        this.bridgeRepository = bridgeRepository;
        this.solver = solver;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.demTaskExecutor = demTaskExecutor;
    }

    @Transactional
    public MasonryDemResultDTO simulateMasonry(MasonryDemRequestDTO request) {
        Bridge bridge = bridgeRepository.findById(request.getBridgeId())
                .orElseThrow(() -> new IllegalArgumentException("桥梁不存在: " + request.getBridgeId()));

        MasonryParams params = paramsRepository.findTopByBridgeIdOrderByMeasuredAtDesc(request.getBridgeId())
                .orElseGet(() -> createDefaultParams(bridge));

        MasonryDemResultDTO result = solver.solve(request, params, bridge.getName());
        saveResult(request.getBridgeId(), result, params);

        publishSimulatedEvent(request.getBridgeId(), result,
                MasonrySimulatedEvent.Trigger.ON_DEMAND);

        log.info("完成桥梁{}砌筑工艺DEM仿真，分析类型:{}，完整性指数:{:.3f}，荷载传递效率:{:.3f}",
                bridge.getName(), result.getAnalysisType(),
                result.getStructuralIntegrityIndex(), result.getLoadTransferEfficiency());

        return result;
    }

    @Transactional
    public MasonryParams saveMasonryParams(MasonryParams params) {
        return paramsRepository.save(params);
    }

    public List<MasonryParams> getParamsHistory(Long bridgeId) {
        return paramsRepository.findByBridgeIdOrderByMeasuredAtDesc(bridgeId);
    }

    public Optional<MasonryParams> getLatestParams(Long bridgeId) {
        return paramsRepository.findTopByBridgeIdOrderByMeasuredAtDesc(bridgeId);
    }

    public List<MasonryDemResult> getSimulationHistory(Long bridgeId, String analysisType) {
        if (analysisType != null && !analysisType.isEmpty()) {
            return demRepository.findByBridgeIdAndAnalysisTypeOrderByCalculatedAtDesc(bridgeId, analysisType);
        }
        return demRepository.findByBridgeIdOrderByCalculatedAtDesc(bridgeId);
    }

    public Optional<MasonryDemResult> getLatestSimulation(Long bridgeId, String analysisType) {
        if (analysisType != null && !analysisType.isEmpty()) {
            return demRepository.findTopByBridgeIdAndAnalysisTypeOrderByCalculatedAtDesc(bridgeId, analysisType);
        }
        return demRepository.findTopByBridgeIdOrderByCalculatedAtDesc(bridgeId);
    }

    public List<Object[]> getAnalysisSummary(Long bridgeId) {
        return demRepository.summarizeByAnalysisType(bridgeId);
    }

    @Scheduled(cron = "${masonry.dem.scheduled-cron:0 0 5 * * ?}")
    public void scheduledDailySimulation() {
        if (!properties.isScheduledEnabled()) return;
        log.info("开始每日砌筑工艺DEM仿真调度任务");

        List<Bridge> bridges = bridgeRepository.findAll();
        String[] analysisTypes = properties.getAnalysisTypes().split(",");

        for (Bridge bridge : bridges) {
            for (String analysisType : analysisTypes) {
                try {
                    String type = analysisType.trim();
                    MasonryDemRequestDTO request = createDefaultRequest(bridge.getId(), type);
                    MasonryParams params = paramsRepository.findTopByBridgeIdOrderByMeasuredAtDesc(bridge.getId())
                            .orElseGet(() -> createDefaultParams(bridge));

                    MasonryDemResultDTO result = solver.solve(request, params, bridge.getName());
                    saveResult(bridge.getId(), result, params);
                    publishSimulatedEvent(bridge.getId(), result,
                            MasonrySimulatedEvent.Trigger.SCHEDULED_DAILY);

                } catch (Exception e) {
                    log.error("桥梁{}砌筑仿真分析失败: {}", bridge.getName(), e.getMessage());
                }
            }
        }
        log.info("每日砌筑工艺DEM仿真调度任务完成");
    }

    private MasonryDemRequestDTO createDefaultRequest(Long bridgeId, String analysisType) {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(bridgeId);
        request.setAnalysisType(analysisType);
        request.setElementCount(properties.getDefaultElementCount());
        request.setTimeStep(properties.getDefaultTimeStep());
        request.setGravityFactor(properties.getDefaultGravityFactor());
        request.setContactStiffness(properties.getDefaultContactStiffness());
        request.setDampingCoefficient(properties.getDefaultDampingCoefficient());
        return request;
    }

    private MasonryParams createDefaultParams(Bridge bridge) {
        MasonryParams params = new MasonryParams();
        params.setBridgeId(bridge.getId());
        params.setStoneShape("矩形条石");
        params.setStoneArrangement("错缝平砌");
        params.setMortarType("传统石灰砂浆");
        params.setMortarCompressiveStrength(5.0);
        params.setMortarTensileStrength(0.35);
        params.setJointThickness(0.02);
        params.setStoneFrictionCoefficient(0.65);
        params.setCohesion(0.5);
        return paramsRepository.save(params);
    }

    private void saveResult(Long bridgeId, MasonryDemResultDTO result, MasonryParams params) {
        MasonryDemResult entity = new MasonryDemResult();
        entity.setBridgeId(bridgeId);
        entity.setAnalysisType(result.getAnalysisType());
        entity.setElementCount(result.getElementCount());
        entity.setContactCount(result.getContactCount());
        entity.setMaxContactForce(result.getMaxContactForce());
        entity.setAvgContactForce(result.getAvgContactForce());
        entity.setForceChainData(result.getForceChainData());
        entity.setStoneDisplacements(result.getStoneDisplacements());
        entity.setJointStresses(result.getJointStresses());
        entity.setStructuralIntegrityIndex(result.getStructuralIntegrityIndex());
        entity.setLoadTransferEfficiency(result.getLoadTransferEfficiency());
        entity.setRecommendation(result.getRecommendation());
        demRepository.save(entity);
    }

    private void publishSimulatedEvent(Long bridgeId, MasonryDemResultDTO result,
                                        MasonrySimulatedEvent.Trigger trigger) {
        MasonrySimulatedEvent event = MasonrySimulatedEvent.builder()
                .bridgeId(bridgeId)
                .analysisType(result.getAnalysisType())
                .structuralIntegrityIndex(result.getStructuralIntegrityIndex())
                .loadTransferEfficiency(result.getLoadTransferEfficiency())
                .elementCount(result.getElementCount())
                .contactCount(result.getContactCount())
                .simulatedAt(LocalDateTime.now())
                .trigger(trigger)
                .build();
        eventPublisher.publishEvent(event);
    }

    @Async("demTaskExecutor")
    public CompletableFuture<MasonryDemResultDTO> simulateMasonryAsync(MasonryDemRequestDTO request) {
        log.info("DEM异步模拟提交到独立线程池，bridgeId={}", request.getBridgeId());
        try {
            MasonryDemResultDTO result = simulateMasonry(request);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("DEM异步模拟失败, bridgeId={}", request.getBridgeId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public List<String> getAvailableAnalysisTypes() {
        return Arrays.asList(properties.getAnalysisTypes().split(","));
    }
}
