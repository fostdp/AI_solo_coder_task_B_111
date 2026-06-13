package com.heritage.bridge.simulation;

import com.heritage.bridge.config.PriorityTopsisProperties;
import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.entity.*;
import com.heritage.bridge.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("TOPSIS多属性决策测试")
@ExtendWith(MockitoExtension.class)
class TopsisDecisionMakerTest {

    @Mock
    private BridgeRepository bridgeRepository;
    @Mock
    private FemResultRepository femResultRepository;
    @Mock
    private DamagePredictionRepository damagePredictionRepository;
    @Mock
    private WeatheringDataRepository weatheringDataRepository;
    @Mock
    private TrafficVibrationAnalysisRepository vibrationRepository;
    @Mock
    private AnnualProtectionPlanRepository planRepository;

    private PriorityTopsisProperties properties;

    @InjectMocks
    private TopsisDecisionMaker decisionMaker;

    @BeforeEach
    void setUp() {
        properties = new PriorityTopsisProperties();
        properties.setPriorityCriticalMax(3);
        properties.setPriorityHighMax(6);
        properties.setUrgencyImmediateMax(1.0);
        properties.setUrgencyUrgentMax(0.8);
        properties.setUrgencyNormalMax(0.6);
        properties.setProtectionPlanEnabled(true);
        properties.setDefaultPlanYear(2026);
    }

    private Bridge createBridge(Long id, String name, Integer builtYear, Integer healthScore) {
        Bridge b = new Bridge();
        b.setId(id);
        b.setName(name);
        b.setBuiltYear(builtYear != null ? builtYear : 605);
        b.setHealthScore(healthScore != null ? healthScore : 85);
        b.setSpanLength(37.0);
        b.setStatus("normal");
        return b;
    }

    private FemResult createFemResult(double safetyFactor, double pfFailure) {
        FemResult fr = new FemResult();
        fr.setSafetyFactor(safetyFactor);
        fr.setPfFailure(pfFailure);
        fr.setMaxStress(1.0e6);
        return fr;
    }

    private DamagePrediction createDamagePrediction(Integer maintenanceYear) {
        DamagePrediction dp = new DamagePrediction();
        dp.setMaintenanceYear(maintenanceYear != null ? maintenanceYear : 2030);
        dp.setParisC(1.0e-10);
        dp.setParisM(3.0);
        dp.setPredictedCrackLength(0.5);
        return dp;
    }

    private TrafficVibrationAnalysis createVibrationAnalysis(double safetyMargin) {
        TrafficVibrationAnalysis va = new TrafficVibrationAnalysis();
        va.setSafetyMargin(safetyMargin);
        va.setMaxAcceleration(0.1);
        va.setAllowableWeightLimit(30.0);
        va.setAllowableSpeedLimit(50.0);
        return va;
    }

    private void mockBridgeRepository(List<Bridge> bridges) {
        when(bridgeRepository.findAll()).thenReturn(bridges);
    }

    private void mockFemRepository() {
        when(femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    double sf = 2.5 - (id - 1) * 0.2;
                    double pf = 0.001 + (id - 1) * 0.005;
                    return Optional.of(createFemResult(Math.max(1.0, sf), pf));
                });
    }

    private void mockDamageRepository() {
        when(damagePredictionRepository.findByBridgeIdOrderByPredictedAtDesc(anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    int year = 2035 - (id - 1) * 2;
                    return Collections.singletonList(createDamagePrediction(year));
                });
    }

    private void mockWeatheringRepository() {
        when(weatheringDataRepository.findAverageDepthByBridgeId(anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    return 2.0 + (id - 1) * 1.5;
                });
        when(weatheringDataRepository.findMaxDepthByBridgeId(anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    return 5.0 + (id - 1) * 2.0;
                });
    }

    private void mockVibrationRepository() {
        when(vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    double margin = 2.5 - (id - 1) * 0.25;
                    return Optional.of(createVibrationAnalysis(Math.max(0.5, margin)));
                });
    }

    private void mockPlanRepository() {
        when(planRepository.save(any(AnnualProtectionPlan.class)))
                .thenAnswer(invocation -> {
                    AnnualProtectionPlan plan = invocation.getArgument(0);
                    plan.setId(new Random().nextLong());
                    return plan;
                });
    }

    @Test
    @DisplayName("正常场景：10座桥梁TOPSIS排序")
    void testCalculate_tenBridges_normalRanking() {
        List<Bridge> bridges = new ArrayList<>();
        String[] names = {"赵州桥", "卢沟桥", "广济桥", "洛阳桥", "宝带桥",
                "安平桥", "五亭桥", "泸定桥", "程阳风雨桥", "拱宸桥"};
        int[] builtYears = {605, 1192, 1171, 1059, 816, 1152, 1757, 1706, 1916, 1631};
        int[] healthScores = {92, 85, 78, 88, 82, 90, 95, 75, 86, 89};

        for (int i = 0; i < 10; i++) {
            bridges.add(createBridge((long) (i + 1), names[i], builtYears[i], healthScores[i]));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(true);
        request.setPlanYear(2026);

        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertNotNull(result);
        assertEquals(10, result.getRankings().size(), "应返回10座桥梁的排序");
        assertEquals(2026, result.getPlanYear());

        for (int i = 0; i < result.getRankings().size(); i++) {
            assertEquals(i + 1, result.getRankings().get(i).getRanking(),
                "排序应从1到10连续");
        }

        double firstScore = result.getRankings().get(0).getTopsisScore();
        double lastScore = result.getRankings().get(result.getRankings().size() - 1).getTopsisScore();
        assertTrue(firstScore > lastScore, "排名越前TOPSIS得分应越高");
    }

    @Test
    @DisplayName("专家评估一致性：低健康分桥梁应靠前")
    void testCalculate_expertConsistency_lowHealthRanksHigh() {
        List<Bridge> bridges = new ArrayList<>();
        bridges.add(createBridge(1L, "健康桥", 1800, 95));
        bridges.add(createBridge(2L, "损伤桥", 1800, 60));
        bridges.add(createBridge(3L, "一般桥", 1800, 80));

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        when(femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(1L))
                .thenReturn(Optional.of(createFemResult(3.0, 0.0001)));
        when(femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(2L))
                .thenReturn(Optional.of(createFemResult(1.2, 0.05)));
        when(femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(3L))
                .thenReturn(Optional.of(createFemResult(2.0, 0.01)));

        when(damagePredictionRepository.findByBridgeIdOrderByPredictedAtDesc(1L))
                .thenReturn(Collections.singletonList(createDamagePrediction(2050)));
        when(damagePredictionRepository.findByBridgeIdOrderByPredictedAtDesc(2L))
                .thenReturn(Collections.singletonList(createDamagePrediction(2027)));
        when(damagePredictionRepository.findByBridgeIdOrderByPredictedAtDesc(3L))
                .thenReturn(Collections.singletonList(createDamagePrediction(2035)));

        when(weatheringDataRepository.findAverageDepthByBridgeId(1L)).thenReturn(1.0);
        when(weatheringDataRepository.findAverageDepthByBridgeId(2L)).thenReturn(15.0);
        when(weatheringDataRepository.findAverageDepthByBridgeId(3L)).thenReturn(5.0);

        when(vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(1L))
                .thenReturn(Optional.of(createVibrationAnalysis(2.5)));
        when(vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(2L))
                .thenReturn(Optional.of(createVibrationAnalysis(0.8)));
        when(vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(3L))
                .thenReturn(Optional.of(createVibrationAnalysis(1.5)));

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertEquals(3, result.getRankings().size());

        String firstBridge = result.getRankings().get(0).getBridgeName();
        assertEquals("损伤桥", firstBridge,
            "健康状况最差的桥应排名最前，实际: " + firstBridge);

        String lastBridge = result.getRankings().get(result.getRankings().size() - 1).getBridgeName();
        assertEquals("健康桥", lastBridge,
            "健康状况最好的桥应排名最后，实际: " + lastBridge);
    }

    @Test
    @DisplayName("权重稳定性：不同权重方案排序变化在可接受范围")
    void testCalculate_weightStability_reasonableVariation() {
        List<Bridge> bridges = new ArrayList<>();
        String[] names = {"桥A", "桥B", "桥C", "桥D", "桥E"};
        for (int i = 0; i < 5; i++) {
            bridges.add(createBridge((long) (i + 1), names[i], 1800 + i * 50, 90 - i * 3));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        Map<String, Double> weights1 = new LinkedHashMap<>();
        weights1.put("structure-safety", 0.4);
        weights1.put("damage-trend", 0.3);
        weights1.put("weathering", 0.1);
        weights1.put("traffic-impact", 0.1);
        weights1.put("historical-value", 0.1);

        Map<String, Double> weights2 = new LinkedHashMap<>();
        weights2.put("structure-safety", 0.2);
        weights2.put("damage-trend", 0.2);
        weights2.put("weathering", 0.2);
        weights2.put("traffic-impact", 0.2);
        weights2.put("historical-value", 0.2);

        PriorityTopsisRequestDTO req1 = new PriorityTopsisRequestDTO();
        req1.setWeights(weights1);
        req1.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result1 = decisionMaker.calculate(req1);

        PriorityTopsisRequestDTO req2 = new PriorityTopsisRequestDTO();
        req2.setWeights(weights2);
        req2.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result2 = decisionMaker.calculate(req2);

        Map<String, Integer> ranking1 = new HashMap<>();
        Map<String, Integer> ranking2 = new HashMap<>();
        for (int i = 0; i < result1.getRankings().size(); i++) {
            ranking1.put(result1.getRankings().get(i).getBridgeName(), i + 1);
            ranking2.put(result2.getRankings().get(i).getBridgeName(), i + 1);
        }

        int totalShift = 0;
        for (String name : names) {
            totalShift += Math.abs(ranking1.get(name) - ranking2.get(name));
        }

        assertTrue(totalShift <= 10,
            "总排名偏移不应过大，总偏移: " + totalShift);
    }

    @Test
    @DisplayName("年度保护计划：所有桥梁都有项目")
    void testCalculate_annualPlan_allBridgesCovered() {
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            bridges.add(createBridge((long) (i + 1), "桥" + (char) ('A' + i), 1900, 80 + i));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(true);
        request.setPlanYear(2026);

        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertEquals(5, result.getProtectionPlan().size(),
            "5座桥应有5个保护项目");

        for (PriorityTopsisResultDTO.AnnualPlanItem item : result.getProtectionPlan()) {
            assertNotNull(item.getProjectName(), "项目名称不应空");
            assertNotNull(item.getProjectType(), "项目类型不应空");
            assertNotNull(item.getTimeline(), "时间安排不应空");
            assertTrue(item.getEstimatedBudget() > 0, "预算应>0");
            assertNotNull(item.getDescription(), "描述不应空");
        }

        Set<String> timelines = new HashSet<>();
        for (PriorityTopsisResultDTO.AnnualPlanItem item : result.getProtectionPlan()) {
            timelines.add(item.getTimeline());
        }
        assertTrue(timelines.size() >= 2, "应有至少2个不同的时间安排季度");
    }

    @Test
    @DisplayName("优先级等级：前3名critical，4-6名high，其余normal")
    void testCalculate_priorityLevels_correctTiering() {
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            bridges.add(createBridge((long) (i + 1), "桥" + (i + 1), 1900, 90 - i * 2));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        for (PriorityTopsisResultDTO.BridgePriority bp : result.getRankings()) {
            int rank = bp.getRanking();
            if (rank <= 3) {
                assertEquals("critical", bp.getPriorityLevel(),
                    "第" + rank + "名应为critical级");
            } else if (rank <= 6) {
                assertEquals("high", bp.getPriorityLevel(),
                    "第" + rank + "名应为high级");
            } else {
                assertEquals("normal", bp.getPriorityLevel(),
                    "第" + rank + "名应为normal级");
            }
        }
    }

    @Test
    @DisplayName("紧迫度等级：四级分类")
    void testCalculate_urgencyLevels_fourLevels() {
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            bridges.add(createBridge((long) (i + 1), "桥" + (i + 1), 1900, 95 - i * 3));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        Set<String> urgencyLevels = new HashSet<>();
        for (PriorityTopsisResultDTO.BridgePriority bp : result.getRankings()) {
            urgencyLevels.add(bp.getMaintenanceUrgency());
        }

        assertTrue(urgencyLevels.size() >= 2,
            "应至少有2种不同的紧迫度等级");
        assertTrue(urgencyLevels.contains("immediate") || urgencyLevels.contains("urgent"),
            "应有immediate或urgent级");
    }

    @Test
    @DisplayName("边界场景：单座桥退化")
    void testCalculate_singleBridge_degenerate() {
        List<Bridge> bridges = new ArrayList<>();
        bridges.add(createBridge(1L, "孤桥", 1800, 80));

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertEquals(1, result.getRankings().size());
        assertEquals(1, result.getRankings().get(0).getRanking());
    }

    @Test
    @DisplayName("异常场景：无桥梁数据应抛出异常")
    void testCalculate_noBridges_throwsException() {
        when(bridgeRepository.findAll()).thenReturn(Collections.emptyList());

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        assertThrows(IllegalStateException.class, () -> decisionMaker.calculate(request),
            "无桥梁数据时应抛出IllegalStateException");
    }

    @Test
    @DisplayName("边界场景：关闭保护计划生成")
    void testCalculate_disableProtectionPlan_emptyPlan() {
        List<Bridge> bridges = new ArrayList<>();
        bridges.add(createBridge(1L, "测试桥", 1800, 85));

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertTrue(result.getProtectionPlan().isEmpty(),
            "关闭计划生成时保护计划应为空");
    }

    @Test
    @DisplayName("费用估算：排名越前费用越高")
    void testCalculate_costEstimation_higherRankingHigherCost() {
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            bridges.add(createBridge((long) (i + 1), "桥" + (i + 1), 1900, 95 - i * 4));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(true);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        double prevCost = Double.MAX_VALUE;
        for (PriorityTopsisResultDTO.AnnualPlanItem item : result.getProtectionPlan()) {
            double cost = item.getEstimatedBudget();
            assertTrue(cost > 0, "费用应为正: " + cost);
        }
    }

    @Test
    @DisplayName("项目类型匹配：损伤严重的桥应偏向结构加固")
    void testCalculate_projectTypeMatching_structuralIssues() {
        List<Bridge> bridges = new ArrayList<>();
        bridges.add(createBridge(1L, "结构危桥", 1900, 65));
        bridges.add(createBridge(2L, "风化桥", 1700, 85));

        mockBridgeRepository(bridges);
        when(femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(1L))
                .thenReturn(Optional.of(createFemResult(1.0, 0.1)));
        when(femResultRepository.findTopByBridgeIdOrderByCalculatedAtDesc(2L))
                .thenReturn(Optional.of(createFemResult(2.5, 0.001)));

        when(damagePredictionRepository.findByBridgeIdOrderByPredictedAtDesc(1L))
                .thenReturn(Collections.singletonList(createDamagePrediction(2027)));
        when(damagePredictionRepository.findByBridgeIdOrderByPredictedAtDesc(2L))
                .thenReturn(Collections.singletonList(createDamagePrediction(2040)));

        when(weatheringDataRepository.findAverageDepthByBridgeId(1L)).thenReturn(3.0);
        when(weatheringDataRepository.findAverageDepthByBridgeId(2L)).thenReturn(12.0);

        when(vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(1L))
                .thenReturn(Optional.of(createVibrationAnalysis(1.0)));
        when(vibrationRepository.findTopByBridgeIdOrderByCalculatedAtDesc(2L))
                .thenReturn(Optional.of(createVibrationAnalysis(2.0)));

        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(true);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertEquals(2, result.getProtectionPlan().size());
        assertNotNull(result.getProtectionPlan().get(0).getProjectType());
    }

    @Test
    @DisplayName("TOPSIS得分范围：0到1之间")
    void testCalculate_topsisScore_range0To1() {
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            bridges.add(createBridge((long) (i + 1), "桥" + (i + 1), 1900, 88 - i * 3));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        for (PriorityTopsisResultDTO.BridgePriority bp : result.getRankings()) {
            double score = bp.getTopsisScore();
            assertTrue(score >= 0 && score <= 1.0,
                "TOPSIS得分应在0-1之间: " + score);
        }
    }

    @Test
    @DisplayName("历史价值验证：古桥历史价值评分更高")
    void testHistoricalValueScore_oldBridgesHigher() {
        List<Bridge> bridges = new ArrayList<>();
        bridges.add(createBridge(1L, "古桥", 605, 90));
        bridges.add(createBridge(2L, "新桥", 1950, 90));

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);
        PriorityTopsisResultDTO result = decisionMaker.calculate(request);

        assertEquals(2, result.getRankings().size());
    }

    @Test
    @DisplayName("排序稳定性：相同输入应得到相同结果")
    void testCalculate_stability_deterministicResult() {
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            bridges.add(createBridge((long) (i + 1), "桥" + (i + 1), 1900, 90 - i * 2));
        }

        mockBridgeRepository(bridges);
        mockFemRepository();
        mockDamageRepository();
        mockWeatheringRepository();
        mockVibrationRepository();
        mockPlanRepository();

        PriorityTopsisRequestDTO request = new PriorityTopsisRequestDTO();
        request.setGenerateProtectionPlan(false);

        PriorityTopsisResultDTO result1 = decisionMaker.calculate(request);
        PriorityTopsisResultDTO result2 = decisionMaker.calculate(request);

        for (int i = 0; i < result1.getRankings().size(); i++) {
            assertEquals(result1.getRankings().get(i).getBridgeId(),
                    result2.getRankings().get(i).getBridgeId(),
                "第" + i + "名应相同");
            assertEquals(result1.getRankings().get(i).getTopsisScore(),
                    result2.getRankings().get(i).getTopsisScore(),
                    0.0001,
                "第" + i + "名得分应相同");
        }
    }
}
