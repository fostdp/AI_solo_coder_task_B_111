package com.heritage.bridge.damage;

import com.heritage.bridge.config.ParisFormulaProperties;
import com.heritage.bridge.dto.DamagePredictionRequestDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.DamagePrediction;
import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.entity.SensorData;
import com.heritage.bridge.event.DataIngestedEvent;
import com.heritage.bridge.event.DamagePredictedEvent;
import com.heritage.bridge.event.FemResultEvent;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.repository.DamagePredictionRepository;
import com.heritage.bridge.repository.SensorDataRepository;
import com.heritage.bridge.repository.SensorRepository;
import com.heritage.bridge.simulation.BayesianParisCalibrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "damage-predictor")
@Service
@RequiredArgsConstructor
public class DamagePredictorService {

    private final BayesianParisCalibrator calibrator;
    private final ParisFormulaProperties props;
    private final BridgeRepository bridgeRepository;
    private final SensorRepository sensorRepository;
    private final SensorDataRepository dataRepository;
    private final DamagePredictionRepository predictionRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<Long, AtomicInteger> crackIngestCounter = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastPredictTime = new ConcurrentHashMap<>();

    public DamagePrediction calculateOnDemand(DamagePredictionRequestDTO dto) {
        Long bridgeId = dto.getBridgeId();
        Long crackSensorId = dto.getCrackSensorId();

        Bridge bridge = bridgeRepository.findById(bridgeId)
                .orElseThrow(() -> new IllegalArgumentException("桥梁不存在: " + bridgeId));

        Sensor crackSensor = null;
        if (crackSensorId != null) {
            crackSensor = sensorRepository.findById(crackSensorId).orElse(null);
        }
        if (crackSensor == null) {
            crackSensor = sensorRepository.findByBridgeIdAndType(bridgeId, "crack").stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未找到裂缝传感器 bridge=" + bridgeId));
        }
        crackSensorId = crackSensor.getId();

        PredictionContext ctx = buildContext(dto, crackSensor);
        PredictionResult pr = runPrediction(ctx, crackSensor);
        DamagePrediction saved = persistPrediction(bridge, crackSensor, ctx, pr);
        publishEvent(saved, crackSensor, pr, DamagePredictedEvent.Source.ON_DEMAND);
        return saved;
    }

    public List<DamagePrediction> listByBridge(Long bridgeId, int limit) {
        return predictionRepository.findByBridgeIdOrderByPredictedAtDesc(bridgeId)
                .stream()
                .limit(Math.max(1, Math.min(100, limit)))
                .toList();
    }

    public Optional<DamagePrediction> getLatest(Long bridgeId) {
        return predictionRepository.findTopByBridgeIdOrderByPredictedAtDesc(bridgeId);
    }

    @Async
    @EventListener
    public void onFemResult(FemResultEvent event) {
        log.trace("[DAMAGE] 收到FemResultEvent bridge={}, stress={}", event.getBridgeId(), event.getMaxStress());
    }

    @Async
    @EventListener
    public void onDataIngested(DataIngestedEvent event) {
        if (!props.isDataTriggeredAutoPredict()) return;
        if (!"crack".equals(event.getSensorType())) return;

        Long sensorId = event.getSensorId();
        AtomicInteger counter = crackIngestCounter.computeIfAbsent(sensorId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        LocalDateTime last = lastPredictTime.get(sensorId);
        if (last != null && last.plusDays(7).isAfter(LocalDateTime.now())) return;

        if (count >= 100) {
            try {
                autoPredict(event.getBridgeId(), sensorId);
                counter.set(0);
            } catch (Exception e) {
                log.warn("[DAMAGE] 数据触发自动预测失败 sensor={}: {}", sensorId, e.getMessage());
            }
        }
    }

    @Scheduled(cron = "${damage.paris.scheduled-cron:0 0 3 * * MON}")
    @Transactional
    public void scheduledMonthlyPrediction() {
        if (!props.isScheduledEnabled()) return;
        log.info("[DAMAGE] 启动每月损伤预测任务");
        List<Sensor> allCracks = sensorRepository.findByType("crack");
        int ok = 0, fail = 0;
        for (Sensor sensor : allCracks) {
            try {
                autoPredict(sensor.getBridgeId(), sensor.getId());
                ok++;
            } catch (Exception e) {
                log.warn("[DAMAGE] 预测失败 sensor={}, bridge={}: {}",
                        sensor.getId(), sensor.getBridgeId(), e.getMessage());
                fail++;
            }
        }
        log.info("[DAMAGE] 每月损伤预测完成: 成功{} / 失败{}", ok, fail);
    }

    private void autoPredict(Long bridgeId, Long sensorId) {
        bridgeRepository.findById(bridgeId).ifPresent(bridge ->
                sensorRepository.findById(sensorId).ifPresent(sensor -> {
                    DamagePredictionRequestDTO dto = new DamagePredictionRequestDTO();
                    dto.setBridgeId(bridgeId);
                    dto.setCrackSensorId(sensorId);
                    dto.setEnableBayesian(props.isEnableBayesianDefault());
                    dto.setYearsToPredict(props.getDefaultYearsToPredict());
                    PredictionContext ctx = buildContext(dto, sensor);
                    PredictionResult pr = runPrediction(ctx, sensor);
                    DamagePrediction saved = persistPrediction(bridge, sensor, ctx, pr);
                    publishEvent(saved, sensor, pr, DamagePredictedEvent.Source.SCHEDULED_MONTHLY);
                    lastPredictTime.put(sensorId, LocalDateTime.now());
                })
        );
    }

    private PredictionContext buildContext(DamagePredictionRequestDTO dto, Sensor sensor) {
        PredictionContext ctx = new PredictionContext();
        ctx.setYearsToPredict(dto.getYearsToPredict() != null ? dto.getYearsToPredict() : props.getDefaultYearsToPredict());
        ctx.setAnnualCycles(props.getDefaultAnnualCycles());
        ctx.setStressAmplitude(dto.getStressAmplitude() != null ? dto.getStressAmplitude().doubleValue() : props.getDefaultStressAmplitude());
        ctx.setEnableBayesian(dto.getEnableBayesian() != null ? dto.getEnableBayesian() : props.isEnableBayesianDefault());
        ctx.setMcmcSamples(dto.getMcmcSamples() != null ? dto.getMcmcSamples() : props.getDefaultMcmcSamples());
        ctx.setBurnin(props.getDefaultBurnin());
        ctx.setPriorCMean(props.getPriorCMean());
        ctx.setPriorCStd(props.getPriorCStd());
        ctx.setPriorMMean(props.getPriorMMean());
        ctx.setPriorMStd(props.getPriorMStd());

        if (dto.getInitialLength() != null && dto.getInitialLength().doubleValue() > 0) {
            ctx.setInitialLength(dto.getInitialLength().doubleValue() * 1e-3);
        } else {
            double initFromSensor = fetchLatestCrackLengthMm(sensor);
            ctx.setInitialLength(Math.max(0.5e-3, initFromSensor * 1e-3));
        }

        if (dto.getParisC() != null && dto.getParisC().doubleValue() > 0) {
            ctx.setParisC(clamp(dto.getParisC().doubleValue(), props.getMinC(), props.getMaxC()));
        } else {
            ctx.setParisC(props.getDefaultC());
        }
        if (dto.getParisM() != null && dto.getParisM().doubleValue() > 0) {
            ctx.setParisM(clamp(dto.getParisM().doubleValue(), props.getMinM(), props.getMaxM()));
        } else {
            ctx.setParisM(props.getDefaultM());
        }

        if (dto.getPriorC_mean() != null) ctx.setPriorCMean(dto.getPriorC_mean().doubleValue());
        if (dto.getPriorM_mean() != null) ctx.setPriorMMean(dto.getPriorM_mean().doubleValue());

        return ctx;
    }

    private double fetchLatestCrackLengthMm(Sensor sensor) {
        return dataRepository.findFirstBySensorIdOrderByTimestampDesc(sensor.getId())
                .map(sd -> sd.getValue().doubleValue())
                .orElse(1.0);
    }

    private PredictionResult runPrediction(PredictionContext ctx, Sensor sensor) {
        PredictionResult pr = new PredictionResult();
        double C = ctx.getParisC();
        double m = ctx.getParisM();

        if (ctx.isEnableBayesian()) {
            List<SensorData> history = dataRepository.findTrendDataBySensorId(
                    sensor.getId(), LocalDateTime.now().minusYears(1));
            List<double[]> hist = calibrator.buildHistoryFromData(history);
            if (hist.size() >= 2) {
                BayesianParisCalibrator.CalibrationInput in = new BayesianParisCalibrator.CalibrationInput();
                in.setInitialC(C);
                in.setInitialM(m);
                in.setPriorC_mean(ctx.getPriorCMean());
                in.setPriorC_std(ctx.getPriorCStd());
                in.setPriorM_mean(ctx.getPriorMMean());
                in.setPriorM_std(ctx.getPriorMStd());
                in.setMcmcSamples(clampInt(ctx.getMcmcSamples(), props.getMinMcmcSamples(), props.getMaxMcmcSamples()));
                in.setBurnin(ctx.getBurnin());
                in.setStressAmplitude(ctx.getStressAmplitude());
                in.setAnnualCycles(ctx.getAnnualCycles());
                in.setHistory(hist);
                BayesianParisCalibrator.CalibrationResult cal = calibrator.calibrate(in);
                C = cal.getCPostMean();
                m = cal.getMPostMean();
                pr.setBayesian(true);
                pr.setParisC_postMean(cal.getCPostMean());
                pr.setParisC_postStd(cal.getCPostStd());
                pr.setParisM_postMean(cal.getMPostMean());
                pr.setParisM_postStd(cal.getMPostStd());
                pr.setMcmcSamples(cal.getSamples());
                log.info("[DAMAGE] 贝叶斯标定完成: C={:.3e}, m={:.2f} ({} samples)", C, m, cal.getSamples());
            } else {
                log.info("[DAMAGE] 历史数据不足,使用默认参数 C={:.2e}, m={:.2f}", C, m);
            }
        }

        pr.setParisC(C);
        pr.setParisM(m);

        int years = ctx.getYearsToPredict();
        double a = ctx.getInitialLength();
        int thisYear = LocalDateTime.now().getYear();
        double maintenanceThresholdM = props.getMaintenanceThresholdMm() * 1e-3;
        double dangerThresholdM = props.getDangerThresholdMm() * 1e-3;
        Integer maintenanceYear = null;
        List<DamagePrediction.YearPrediction> predictions = new ArrayList<>();
        String risk = "low";
        String recommendation;

        for (int y = 1; y <= years; y++) {
            double aFinal = BayesianParisCalibrator.integrateParis(
                    a, C, m, ctx.getStressAmplitude(), y, ctx.getAnnualCycles());
            int calendarYear = thisYear + y;
            double lenMm = aFinal * 1000;
            risk = lenMm > props.getDangerThresholdMm() ? "danger"
                    : lenMm > props.getMaintenanceThresholdMm() ? "warning" : "low";
            DamagePrediction.YearPrediction yp = new DamagePrediction.YearPrediction();
            yp.setYear(calendarYear);
            yp.setLength(bd(lenMm, 3));
            yp.setRisk(risk);
            predictions.add(yp);
            if (maintenanceYear == null && lenMm > props.getMaintenanceThresholdMm()) {
                maintenanceYear = calendarYear;
            }
        }

        double finalLenMm = predictions.get(predictions.size() - 1).getLength().doubleValue();
        double initLenMm = a * 1000;
        double growthRate = (finalLenMm - initLenMm) / Math.max(1, years);

        if (pr.isBayesian()) {
            recommendation = String.format(
                    "基于贝叶斯MCMC(%d样)标定参数C=%s,m=%.2f预测：%d年预计裂缝扩展至%.2fmm，%s",
                    pr.getMcmcSamples(),
                    formatSci(C), m,
                    years, finalLenMm,
                    maintenanceYear != null ? String.format("建议%d年前完成预防性维修", maintenanceYear) : "暂无维修必要"
            );
        } else {
            recommendation = String.format(
                    "使用默认Paris参数预测(%d年)：最终裂缝%.2fmm。建议积累6个月以上监测数据后启用贝叶斯在线标定。%s",
                    years, finalLenMm,
                    maintenanceYear != null ? String.format("建议%d年前维修", maintenanceYear) : ""
            );
        }

        pr.setPredictions(predictions);
        pr.setFinalLengthMm(finalLenMm);
        pr.setGrowthRateMmPerYear(growthRate);
        pr.setMaintenanceYear(maintenanceYear);
        pr.setRiskLevel(risk);
        pr.setRecommendation(recommendation);
        pr.setInitialLengthMm(initLenMm);
        return pr;
    }

    @Transactional
    public DamagePrediction persistPrediction(Bridge bridge, Sensor crack, PredictionContext ctx, PredictionResult pr) {
        DamagePrediction dp = new DamagePrediction();
        dp.setBridgeId(bridge.getId());
        dp.setCrackSensorId(crack.getId());
        dp.setInitialLength(bd(pr.getInitialLengthMm(), 6));
        dp.setParisC(bd(pr.getParisC(), 12));
        dp.setParisM(bd(pr.getParisM(), 4));
        if (pr.isBayesian()) {
            dp.setParisCPosteriorMean(bd(pr.getParisC_postMean(), 12));
            dp.setParisCPosteriorStd(bd(pr.getParisC_postStd(), 12));
            dp.setParisMPosteriorMean(bd(pr.getParisM_postMean(), 6));
            dp.setParisMPosteriorStd(bd(pr.getParisM_postStd(), 6));
            dp.setMcmcSamples(pr.getMcmcSamples());
        }
        dp.setIsBayesian(pr.isBayesian());
        dp.setPredictionData(pr.getPredictions());
        dp.setMaintenanceYear(pr.getMaintenanceYear());
        dp.setRecommendation(pr.getRecommendation());
        dp.setPredictedAt(LocalDateTime.now());
        return predictionRepository.save(dp);
    }

    private void publishEvent(DamagePrediction saved, Sensor sensor, PredictionResult pr, DamagePredictedEvent.Source source) {
        try {
            DamagePredictedEvent event = DamagePredictedEvent.builder()
                    .bridgeId(saved.getBridgeId())
                    .crackSensorId(sensor.getId())
                    .prediction(saved)
                    .predictedLengthIn5Years(pr.getFinalLengthMm())
                    .annualGrowthRate(pr.getGrowthRateMmPerYear())
                    .maintenanceYear(pr.getMaintenanceYear())
                    .recommendation(pr.getRecommendation())
                    .riskLevel(pr.getRiskLevel())
                    .parisC(pr.getParisC())
                    .parisM(pr.getParisM())
                    .bayesianCalibrated(pr.isBayesian())
                    .predictedAt(saved.getPredictedAt())
                    .source(source)
                    .build();
            eventPublisher.publishEvent(event);
            log.debug("[DAMAGE] 发布DamagePredictedEvent bridge={}, finalLen={:.2f}mm, risk={}",
                    saved.getBridgeId(), pr.getFinalLengthMm(), pr.getRiskLevel());
        } catch (Exception e) {
            log.warn("[DAMAGE] 发布事件失败: {}", e.getMessage());
        }
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static int clampInt(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static BigDecimal bd(double v, int scale) { return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP); }
    private static String formatSci(double v) { return String.format("%.2e", v); }

    @lombok.Data
    public static class PredictionContext {
        int yearsToPredict;
        int annualCycles;
        double stressAmplitude;
        double initialLength;
        double parisC;
        double parisM;
        boolean enableBayesian;
        int mcmcSamples;
        int burnin;
        double priorCMean;
        double priorCStd;
        double priorMMean;
        double priorMStd;
    }

    @lombok.Data
    public static class PredictionResult {
        List<DamagePrediction.YearPrediction> predictions;
        double initialLengthMm;
        double finalLengthMm;
        double growthRateMmPerYear;
        double parisC;
        double parisM;
        boolean bayesian;
        double parisC_postMean;
        double parisC_postStd;
        double parisM_postMean;
        double parisM_postStd;
        int mcmcSamples;
        Integer maintenanceYear;
        String riskLevel;
        String recommendation;
    }
}
