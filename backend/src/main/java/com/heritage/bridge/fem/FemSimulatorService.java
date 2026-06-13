package com.heritage.bridge.fem;

import com.heritage.bridge.config.FemSimulationProperties;
import com.heritage.bridge.dto.FemRequestDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.FemResult;
import com.heritage.bridge.event.DataIngestedEvent;
import com.heritage.bridge.event.FemResultEvent;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.FemResultRepository;
import com.heritage.bridge.simulation.FemSolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "fem-simulator")
@Service
@RequiredArgsConstructor
public class FemSimulatorService {

    private final FemSolver femSolver;
    private final BridgeRepository bridgeRepository;
    private final FemResultRepository femResultRepository;
    private final FemSimulationProperties props;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<Long, AtomicInteger> bridgeIngestCounter = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastSimTime = new ConcurrentHashMap<>();

    public FemResult.ResultWrapper simulateOnDemand(FemRequestDTO dto) {
        Bridge bridge = bridgeRepository.findById(dto.getBridgeId())
                .orElseThrow(() -> new IllegalArgumentException("桥梁不存在: " + dto.getBridgeId()));
        FemSolver.SolverParams params = buildSolverParams(dto, bridge);
        FemSolver.Result result = femSolver.solve(bridge, params);
        FemResult saved = persistResult(bridge, dto, result);
        publishFemEvent(bridge, saved, result, FemResultEvent.Source.ON_DEMAND);
        return FemResult.ResultWrapper.builder()
                .id(saved.getId())
                .bridgeId(saved.getBridgeId())
                .loadType(saved.getLoadType())
                .nodeData(saved.getNodeData())
                .maxStress(saved.getMaxStress())
                .maxStrain(saved.getMaxStrain())
                .safetyFactor(saved.getSafetyFactor())
                .stressP95(saved.getStressP95())
                .stressP99(saved.getStressP99())
                .pfFailure(saved.getPfFailure())
                .modulusCov(saved.getModulusCov())
                .isStochastic(saved.getIsStochastic())
                .mcSamples(saved.getMcSamples())
                .calculatedAt(saved.getCalculatedAt())
                .build();
    }

    public FemResult getLatest(Long bridgeId) {
        return femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(bridgeId).orElse(null);
    }

    public List<FemResult> listByBridge(Long bridgeId, int limit) {
        return femResultRepository.findByBridgeIdOrderByCalculatedAtDesc(bridgeId)
                .stream()
                .limit(Math.max(1, Math.min(100, limit)))
                .toList();
    }

    @Async
    @EventListener
    public void onDataIngested(DataIngestedEvent event) {
        if (!props.isDataTriggeredAutoSim()) return;
        if (event.getTrigger() == DataIngestedEvent.Trigger.DAILY_CHECK) return;

        Long bridgeId = event.getBridgeId();
        AtomicInteger counter = bridgeIngestCounter.computeIfAbsent(bridgeId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        LocalDateTime last = lastSimTime.get(bridgeId);
        if (last != null && last.plusHours(1).isAfter(LocalDateTime.now())) return;

        if (count >= props.getAutoSimSensorThreshold() || isStrainOrCrack(event.getSensorType())) {
            try {
                triggerAutoSim(bridgeId);
                counter.set(0);
            } catch (Exception e) {
                log.warn("[FEM] 数据触发自动仿真失败 bridge={}: {}", bridgeId, e.getMessage());
            }
        }
    }

    @Scheduled(cron = "${simulation.fem.scheduled-cron:0 0 2 * * ?}")
    @Transactional
    public void scheduledDailySimulation() {
        if (!props.isScheduledEnabled()) return;
        log.info("[FEM] 启动每日自动仿真任务");
        List<Bridge> bridges = bridgeRepository.findAll();
        int ok = 0, fail = 0;
        for (Bridge bridge : bridges) {
            try {
                FemRequestDTO dto = defaultSimRequest(bridge);
                FemSolver.SolverParams params = buildSolverParams(dto, bridge);
                FemSolver.Result result = femSolver.solve(bridge, params);
                persistResult(bridge, dto, result);
                ok++;
            } catch (Exception e) {
                log.warn("[FEM] 桥梁{}仿真失败: {}", bridge.getName(), e.getMessage());
                fail++;
            }
        }
        log.info("[FEM] 每日仿真完成: 成功{} / 失败{}", ok, fail);
    }

    private void triggerAutoSim(Long bridgeId) {
        bridgeRepository.findById(bridgeId).ifPresent(bridge -> {
            try {
                FemRequestDTO dto = defaultSimRequest(bridge);
                dto.setEnableStochastic(props.isDefaultStochastic());
                FemSolver.SolverParams params = buildSolverParams(dto, bridge);
                FemSolver.Result result = femSolver.solve(bridge, params);
                FemResult saved = persistResult(bridge, dto, result);
                publishFemEvent(bridge, saved, result, FemResultEvent.Source.DATA_TRIGGERED);
                lastSimTime.put(bridgeId, LocalDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private FemRequestDTO defaultSimRequest(Bridge bridge) {
        FemRequestDTO dto = new FemRequestDTO();
        dto.setBridgeId(bridge.getId());
        dto.setLoadType("static");
        dto.setElementCount(props.getDefaultElementCount());
        dto.setEnableStochastic(props.isDefaultStochastic());
        dto.setMonteCarloSamples(props.getDefaultMcSamples());
        dto.setModulusCov(props.getDefaultModulusCov());
        dto.setStrengthCov(props.getDefaultStrengthCov());
        dto.setTemperatureDelta(25.0);
        return dto;
    }

    private FemSolver.SolverParams buildSolverParams(FemRequestDTO dto, Bridge bridge) {
        FemSolver.SolverParams p = new FemSolver.SolverParams();
        p.setElementCount(clamp(dto.getElementCount(), props.getMinElementCount(), props.getMaxElementCount()));
        p.setEmean((bridge.getStoneModulus() != null ? bridge.getStoneModulus().doubleValue() : props.getDefaultEmean()) * 1e9);
        p.setNu(bridge.getStonePoisson() != null ? bridge.getStonePoisson().doubleValue() : props.getDefaultNu());
        p.setFcMean(bridge.getStoneStrength() != null ? bridge.getStoneStrength().doubleValue() : props.getDefaultFcMean());
        p.setRiseSpanRatio(bridge.getRiseSpanRatio() != null ? bridge.getRiseSpanRatio().doubleValue() : 0.20);
        p.setSpanLength(bridge.getSpanLength() != null ? bridge.getSpanLength().doubleValue() : 37.0);
        p.setPierThickness(bridge.getPierThickness() != null ? bridge.getPierThickness().doubleValue() : 1.5);
        p.setTrafficLoad(props.getDefaultTrafficLoad());
        p.setTemperatureDelta(dto.getTemperatureDelta() != null ? dto.getTemperatureDelta() : 25.0);
        p.setStoneStrength(p.getFcMean());
        p.setStochastic(Boolean.TRUE.equals(dto.getEnableStochastic()));
        p.setMcSamples(clamp(dto.getMonteCarloSamples() != null ? dto.getMonteCarloSamples() : props.getDefaultMcSamples(),
                props.getMinMcSamples(), props.getMaxMcSamples()));
        double covE = dto.getModulusCov() != null ? dto.getModulusCov() : props.getDefaultModulusCov();
        double covF = dto.getStrengthCov() != null ? dto.getStrengthCov() : props.getDefaultStrengthCov();
        p.setModulusCov(clamp(covE, props.getMinCov(), props.getMaxCov()));
        p.setStrengthCov(clamp(covF, props.getMinCov(), props.getMaxCov()));
        return p;
    }

    @Transactional
    public FemResult persistResult(Bridge bridge, FemRequestDTO dto, FemSolver.Result result) {
        FemResult fr = new FemResult();
        fr.setBridgeId(bridge.getId());
        fr.setLoadType(dto.getLoadType() != null ? dto.getLoadType() : "static");
        fr.setNodeData(result.getNodes());
        fr.setMaxStress(FemResult.bd(result.getMaxStress()));
        fr.setMaxStrain(FemResult.bd(result.getMaxStrain()));
        fr.setSafetyFactor(FemResult.bd(result.getSafetyFactor(), 4));
        fr.setMcSamples(result.isStochasticCheck() ? FemResult.intOrNull(result.getMcSamples()) : null);
        fr.setStressP95(result.isStochasticCheck() ? FemResult.bd(result.getStressP95()) : null);
        fr.setStressP99(result.isStochasticCheck() ? FemResult.bd(result.getStressP99()) : null);
        fr.setPfFailure(result.isStochasticCheck() ? FemResult.bd(result.getPfFailure(), 10) : null);
        fr.setModulusCov(result.isStochasticCheck() ? FemResult.bd(result.getModulusCov(), 4) : null);
        fr.setIsStochastic(result.isStochasticCheck());
        fr.setCalculatedAt(LocalDateTime.now());
        return femResultRepository.save(fr);
    }

    private void publishFemEvent(Bridge bridge, FemResult saved, FemSolver.Result result, FemResultEvent.Source source) {
        try {
            FemResultEvent event = FemResultEvent.builder()
                    .bridgeId(bridge.getId())
                    .femResult(saved)
                    .maxStress(result.getMaxStress())
                    .maxStrain(result.getMaxStrain())
                    .safetyFactor(result.getSafetyFactor())
                    .pfFailure(result.getPfFailure())
                    .stressP95(result.getStressP95())
                    .stressP99(result.getStressP99())
                    .isStochastic(result.isStochasticCheck())
                    .calculatedAt(saved.getCalculatedAt())
                    .source(source)
                    .build();
            eventPublisher.publishEvent(event);
            log.debug("[FEM] 发布FemResultEvent bridge={}, maxStress={:.2e}, sf={:.2f}",
                    bridge.getId(), result.getMaxStress(), result.getSafetyFactor());
        } catch (Exception e) {
            log.warn("[FEM] 发布事件失败: {}", e.getMessage());
        }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static boolean isStrainOrCrack(String type) {
        return "strain".equals(type) || "crack".equals(type) || "displacement".equals(type);
    }
}
