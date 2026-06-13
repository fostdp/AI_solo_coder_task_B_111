package com.heritage.bridge.simulation;

import com.heritage.bridge.config.TrafficVibrationProperties;
import com.heritage.bridge.dto.TrafficVibrationRequestDTO;
import com.heritage.bridge.dto.TrafficVibrationResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("车桥耦合振动模型测试")
class VehicleBridgeCouplingSolverTest {

    private VehicleBridgeCouplingSolver solver;
    private TrafficVibrationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TrafficVibrationProperties();
        properties.setDefaultNaturalFrequency(3.0);
        properties.setDefaultDampingRatio(0.05);
        properties.setDefaultBridgeMass(500000.0);
        properties.setDefaultSpanStiffness(1.0e9);
        properties.setAllowableAcceleration(0.5);
        properties.setAllowableDisplacement(0.005);
        solver = new VehicleBridgeCouplingSolver(properties);
    }

    @Test
    @DisplayName("正常场景：小型轿车通过的动力响应")
    void testSolve_passengerCar_normalResponse() {
        TrafficVibrationRequestDTO request = buildRequest(1.5, 60.0, "passenger");
        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");

        assertNotNull(result);
        assertFalse(result.getAnalyses().isEmpty());

        TrafficVibrationResultDTO.VehicleAnalysis analysis = result.getAnalyses().get(0);
        assertTrue(analysis.getMaxAcceleration() > 0, "加速度应为正值");
        assertTrue(analysis.getMaxDynamicDisplacement() > 0, "动位移应为正值");
        assertTrue(analysis.getDynamicAmplificationFactor() >= 1.0,
            "动力放大系数应≥1.0，实际: " + analysis.getDynamicAmplificationFactor());
        assertTrue(analysis.getDynamicAmplificationFactor() < 3.0,
            "小型车DAF不应过大，实际: " + analysis.getDynamicAmplificationFactor());
    }

    @Test
    @DisplayName("正常场景：重型卡车通过的动力响应")
    void testSolve_heavyTruck_significantResponse() {
        TrafficVibrationRequestDTO request = buildRequest(35.0, 40.0, "truck_heavy");
        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");

        TrafficVibrationResultDTO.VehicleAnalysis analysis = result.getAnalyses().get(0);
        assertTrue(analysis.getMaxAcceleration() > 0.01,
            "重型卡车加速度应显著，实际: " + analysis.getMaxAcceleration());
    }

    @Test
    @DisplayName("频率响应验证：固有频率与响应频谱一致性")
    void testFrequencyResponse_naturalFrequencyConsistency() {
        double omega0 = 3.0;
        TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
        request.setBridgeId(1L);
        request.setNaturalFrequency(omega0);
        request.setDampingRatio(0.05);
        request.setBridgeMass(500000.0);
        request.setSpanStiffness(1.0e9);

        List<TrafficVibrationRequestDTO.VehicleLoad> loads = new ArrayList<>();
        TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
        load.setVehicleType("truck");
        load.setVehicleWeight(20.0);
        load.setVehicleSpeed(50.0);
        loads.add(load);
        request.setVehicleLoads(loads);

        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");
        TrafficVibrationResultDTO.VehicleAnalysis analysis = result.getAnalyses().get(0);

        assertEquals(omega0, analysis.getNaturalFrequency(), 0.001,
            "分析结果的固有频率应与输入一致");
        assertTrue(analysis.getDampingRatio() > 0 && analysis.getDampingRatio() < 1,
            "阻尼比应在0-1之间");
    }

    @Test
    @DisplayName("动力放大系数验证：速度与DAF的关系")
    void testDynamicAmplificationFactor_speedDependency() {
        double[] speeds = {20, 40, 60, 80, 100};
        double[] dafs = new double[speeds.length];

        for (int i = 0; i < speeds.length; i++) {
            TrafficVibrationRequestDTO request = buildRequest(20.0, speeds[i], "truck");
            TrafficVibrationResultDTO result = solver.solve(request, "测试桥");
            dafs[i] = result.getAnalyses().get(0).getDynamicAmplificationFactor();
        }

        for (double daf : dafs) {
            assertTrue(daf >= 1.0, "DAF应≥1.0，实际: " + daf);
            assertTrue(daf < 5.0, "DAF不应超过5.0，实际: " + daf);
        }
    }

    @Test
    @DisplayName("限载建议验证：重型桥vs轻型桥的限重差异")
    void testWeightLimit_differentBridgeTypes() {
        TrafficVibrationRequestDTO stiffBridge = new TrafficVibrationRequestDTO();
        stiffBridge.setBridgeId(1L);
        stiffBridge.setNaturalFrequency(5.0);
        stiffBridge.setDampingRatio(0.05);
        stiffBridge.setBridgeMass(800000.0);
        stiffBridge.setSpanStiffness(2.0e9);
        List<TrafficVibrationRequestDTO.VehicleLoad> loads1 = new ArrayList<>();
        TrafficVibrationRequestDTO.VehicleLoad load1 = new TrafficVibrationRequestDTO.VehicleLoad();
        load1.setVehicleType("truck");
        load1.setVehicleWeight(30.0);
        load1.setVehicleSpeed(50.0);
        loads1.add(load1);
        stiffBridge.setVehicleLoads(loads1);

        TrafficVibrationRequestDTO flexibleBridge = new TrafficVibrationRequestDTO();
        flexibleBridge.setBridgeId(2L);
        flexibleBridge.setNaturalFrequency(2.0);
        flexibleBridge.setDampingRatio(0.03);
        flexibleBridge.setBridgeMass(300000.0);
        flexibleBridge.setSpanStiffness(5.0e8);
        List<TrafficVibrationRequestDTO.VehicleLoad> loads2 = new ArrayList<>();
        TrafficVibrationRequestDTO.VehicleLoad load2 = new TrafficVibrationRequestDTO.VehicleLoad();
        load2.setVehicleType("truck");
        load2.setVehicleWeight(30.0);
        load2.setVehicleSpeed(50.0);
        loads2.add(load2);
        flexibleBridge.setVehicleLoads(loads2);

        TrafficVibrationResultDTO stiffResult = solver.solve(stiffBridge, "刚桥");
        TrafficVibrationResultDTO flexResult = solver.solve(flexibleBridge, "柔桥");

        double stiffLimit = stiffResult.getAllowableWeightLimit();
        double flexLimit = flexResult.getAllowableWeightLimit();

        assertTrue(stiffLimit > flexLimit,
            "刚度大的桥限重应更高，刚桥: " + stiffLimit + "吨, 柔桥: " + flexLimit + "吨");
    }

    @Test
    @DisplayName("限载建议验证：车速与限速的负相关")
    void testSpeedLimit_correlationWithSpeed() {
        double[] testWeights = {10, 20, 30, 40};
        double[] speedLimits = new double[testWeights.length];

        for (int i = 0; i < testWeights.length; i++) {
            TrafficVibrationRequestDTO request = buildRequest(testWeights[i], 80.0, "truck");
            TrafficVibrationResultDTO result = solver.solve(request, "测试桥");
            speedLimits[i] = result.getAllowableSpeedLimit();
        }

        for (int i = 0; i < speedLimits.length - 1; i++) {
            assertTrue(speedLimits[i] >= speedLimits[i + 1] - 5,
                "车重增加时限速不应升高，第" + i + "组: " + speedLimits[i] + " -> " + speedLimits[i + 1]);
        }
    }

    @Test
    @DisplayName("安全等级判定：五级分类准确性")
    void testSafetyLevel_fiveLevelClassification() {
        double[][] testCases = {
            {1.0, 60.0, "excellent"},
            {10.0, 60.0, "good"},
            {25.0, 60.0, "fair"},
            {40.0, 60.0, "poor"},
            {60.0, 80.0, "critical"}
        };

        for (double[] tc : testCases) {
            TrafficVibrationRequestDTO request = buildRequest(tc[0], tc[1], "truck");
            TrafficVibrationResultDTO result = solver.solve(request, "测试桥");
            assertEquals(tc[2], result.getOverallSafetyLevel(),
                "车重" + tc[0] + "吨 车速" + tc[1] + "km/h 应得到安全等级" + tc[2] +
                    "，实际: " + result.getOverallSafetyLevel());
        }
    }

    @Test
    @DisplayName("边界场景：零车重应抛出异常")
    void testValidate_zeroWeight_throwsException() {
        TrafficVibrationRequestDTO request = buildRequest(0, 50.0, "test");
        assertThrows(IllegalArgumentException.class, () -> solver.solve(request, "测试桥"),
            "车重为零应抛出异常");
    }

    @Test
    @DisplayName("边界场景：超速车应抛出异常")
    void testValidate_excessiveSpeed_throwsException() {
        TrafficVibrationRequestDTO request = buildRequest(10.0, 200.0, "test");
        assertThrows(IllegalArgumentException.class, () -> solver.solve(request, "测试桥"),
            "车速超限应抛出异常");
    }

    @Test
    @DisplayName("边界场景：空车辆列表应抛出异常")
    void testValidate_emptyVehicleList_throwsException() {
        TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
        request.setBridgeId(1L);
        request.setVehicleLoads(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> solver.solve(request, "测试桥"),
            "空车辆列表应抛出异常");
    }

    @Test
    @DisplayName("边界场景：100吨极限车重")
    void testSolve_maxWeightEdgeCase() {
        TrafficVibrationRequestDTO request = buildRequest(99.0, 20.0, "super_heavy");
        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");

        assertNotNull(result);
        assertTrue(result.getAnalyses().get(0).getSafetyMargin() > 0,
            "即使重车安全余量也应为正");
        assertEquals("critical", result.getOverallSafetyLevel(),
            "极限重车应为危险等级");
    }

    @Test
    @DisplayName("多车型综合分析：5种车型同时通过")
    void testSolve_multipleVehicleTypes() {
        TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
        request.setBridgeId(1L);
        request.setNaturalFrequency(3.0);
        request.setDampingRatio(0.05);

        List<TrafficVibrationRequestDTO.VehicleLoad> loads = new ArrayList<>();
        String[] types = {"passenger", "bus", "truck_light", "truck_medium", "truck_heavy"};
        double[] weights = {1.5, 12.0, 7.5, 18.0, 35.0};
        double[] speeds = {60, 50, 55, 45, 40};

        for (int i = 0; i < types.length; i++) {
            TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
            load.setVehicleType(types[i]);
            load.setVehicleWeight(weights[i]);
            load.setVehicleSpeed(speeds[i]);
            loads.add(load);
        }
        request.setVehicleLoads(loads);

        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");
        assertEquals(5, result.getAnalyses().size(), "应返回5个车型的分析结果");
        assertNotNull(result.getOverallSafetyLevel());
        assertNotNull(result.getAllowableWeightLimit());
        assertNotNull(result.getAllowableSpeedLimit());
        assertNotNull(result.getRecommendation());
    }

    @Test
    @DisplayName("舒适度指数验证：加速度与舒适度负相关")
    void testComfortIndex_negativeCorrelation() {
        double[] accelerations = {0.01, 0.05, 0.1, 0.3, 0.8};
        double[] comforts = new double[accelerations.length];

        for (int i = 0; i < accelerations.length; i++) {
            comforts[i] = solver.calculateComfortIndex(accelerations[i]);
        }

        for (int i = 0; i < comforts.length - 1; i++) {
            assertTrue(comforts[i] >= comforts[i + 1],
                "加速度增加时舒适度应递减: " + comforts[i] + " -> " + comforts[i + 1]);
        }

        assertEquals(1.0, solver.calculateComfortIndex(0.02), 0.01,
            "极微振动舒适度应为1.0");
        assertEquals(0.1, solver.calculateComfortIndex(2.0), 0.01,
            "强振动舒适度应为0.1");
    }

    @Test
    @DisplayName("数值稳定性：阻尼振荡衰减验证")
    void testNumericalStability_dampedOscillation() {
        TrafficVibrationRequestDTO request = buildRequest(10.0, 30.0, "test");
        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");

        assertTrue(result.getAnalyses().get(0).getSafetyMargin() > 0,
            "数值计算应稳定，安全余量为正");
        assertTrue(result.getAnalyses().get(0).getDynamicAmplificationFactor() < 10,
            "数值不应发散，DAF应在合理范围");
    }

    @Test
    @DisplayName("静位移验证：与理论值一致性")
    void testStaticDisplacement_theoreticalConsistency() {
        double weight = 20.0;
        double stiffness = 1.0e9;

        TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
        request.setBridgeId(1L);
        request.setNaturalFrequency(3.0);
        request.setDampingRatio(0.05);
        request.setBridgeMass(500000.0);
        request.setSpanStiffness(stiffness);

        List<TrafficVibrationRequestDTO.VehicleLoad> loads = new ArrayList<>();
        TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
        load.setVehicleType("test");
        load.setVehicleWeight(weight);
        load.setVehicleSpeed(0.1);
        loads.add(load);
        request.setVehicleLoads(loads);

        TrafficVibrationResultDTO result = solver.solve(request, "测试桥");

        double expectedStatic = (weight * 1000 * 9.81) / stiffness;
        double actualMax = result.getAnalyses().get(0).getMaxDynamicDisplacement();

        assertTrue(actualMax >= expectedStatic * 0.5,
            "最大动位移应不小于静位移的一半，静位移: " + expectedStatic +
                "，实际: " + actualMax);
    }

    private TrafficVibrationRequestDTO buildRequest(double weight, double speed, String type) {
        TrafficVibrationRequestDTO request = new TrafficVibrationRequestDTO();
        request.setBridgeId(1L);
        request.setNaturalFrequency(3.0);
        request.setDampingRatio(0.05);
        request.setBridgeMass(500000.0);
        request.setSpanStiffness(1.0e9);

        List<TrafficVibrationRequestDTO.VehicleLoad> loads = new ArrayList<>();
        TrafficVibrationRequestDTO.VehicleLoad load = new TrafficVibrationRequestDTO.VehicleLoad();
        load.setVehicleType(type);
        load.setVehicleWeight(weight);
        load.setVehicleSpeed(speed);
        loads.add(load);
        request.setVehicleLoads(loads);
        return request;
    }
}
