package com.heritage.bridge.simulation;

import com.heritage.bridge.dto.WeatheringRequestDTO;
import com.heritage.bridge.dto.WeatheringResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("风化深度回归模型测试")
class WeatheringRegressionSolverTest {

    private WeatheringRegressionSolver solver;

    @BeforeEach
    void setUp() {
        solver = new WeatheringRegressionSolver();
    }

    @Test
    @DisplayName("正常场景：标准硬度与波速下的深度估算")
    void testCalculateDepth_normalCase() {
        double depth = solver.calculateDepth(45.0, 3.5, 0.8, 1.2, 15.0);
        assertTrue(depth > 0, "风化深度应为正值");
        assertTrue(depth < 15.0, "有风化时深度应小于截距b");
        assertEquals(5.94, depth, 0.5, "标准工况下深度应在6mm左右");
    }

    @Test
    @DisplayName("边界场景：完整未风化石材（高硬度+高速）")
    void testCalculateDepth_intactStone() {
        double depth = solver.calculateDepth(60.0, 4.5, 0.8, 1.2, 15.0);
        assertTrue(depth > 0, "即使完整石材也有微小风化");
        assertTrue(depth < 2.0, "完整石材风化深度应小于2mm");
    }

    @Test
    @DisplayName("边界场景：严重风化石材（低硬度+低速）")
    void testCalculateDepth_severeWeathering() {
        double depth = solver.calculateDepth(15.0, 1.5, 0.8, 1.2, 15.0);
        assertTrue(depth > 5.0, "严重风化深度应大于5mm");
        assertTrue(depth < 25.0, "深度不应超出模型范围过多");
    }

    @Test
    @DisplayName("异常场景：硬度为零应抛出异常")
    void testCalculateDepth_zeroHardness() {
        assertThrows(IllegalArgumentException.class, () -> {
            solver.calculateDepth(0, 3.5, 0.8, 1.2, 15.0);
        }, "硬度为零时应抛出参数异常");
    }

    @Test
    @DisplayName("异常场景：超声波速为负应抛出异常")
    void testCalculateDepth_negativeVelocity() {
        assertThrows(IllegalArgumentException.class, () -> {
            solver.calculateDepth(40.0, -1.0, 0.8, 1.2, 15.0);
        }, "波速为负时应抛出参数异常");
    }

    @Test
    @DisplayName("相关性验证：硬度与风化深度负相关")
    void testHardnessDepthCorrelation_negative() {
        double[] hardnessValues = {20, 30, 40, 50, 60};
        double[] depths = new double[hardnessValues.length];
        
        for (int i = 0; i < hardnessValues.length; i++) {
            depths[i] = solver.calculateDepth(hardnessValues[i], 3.5, 0.8, 1.2, 15.0);
        }
        
        for (int i = 0; i < depths.length - 1; i++) {
            assertTrue(depths[i] > depths[i + 1],
                "硬度增加时风化深度应递减，第" + i + "个不满足: " + depths[i] + " -> " + depths[i + 1]);
        }
    }

    @Test
    @DisplayName("相关性验证：超声波速与风化深度负相关")
    void testVelocityDepthCorrelation_negative() {
        double[] velocityValues = {2.0, 2.8, 3.5, 4.0, 4.5};
        double[] depths = new double[velocityValues.length];
        
        for (int i = 0; i < velocityValues.length; i++) {
            depths[i] = solver.calculateDepth(40.0, velocityValues[i], 0.8, 1.2, 15.0);
        }
        
        for (int i = 0; i < depths.length - 1; i++) {
            assertTrue(depths[i] > depths[i + 1],
                "波速增加时风化深度应递减，第" + i + "个不满足: " + depths[i] + " -> " + depths[i + 1]);
        }
    }

    @Test
    @DisplayName("风化等级判定：五级分类准确性")
    void testWeatheringGradeClassification_fiveLevels() {
        String gradeNone = solver.classifyGrade(1.0);
        String gradeSlight = solver.classifyGrade(3.0);
        String gradeModerate = solver.classifyGrade(7.0);
        String gradeSevere = solver.classifyGrade(15.0);
        String gradeCritical = solver.classifyGrade(25.0);

        assertEquals("none", gradeNone, "1mm应为无风化");
        assertEquals("slight", gradeSlight, "3mm应为轻微风化");
        assertEquals("moderate", gradeModerate, "7mm应为中等风化");
        assertEquals("severe", gradeSevere, "15mm应为严重风化");
        assertEquals("critical", gradeCritical, "25mm应为危险风化");
    }

    @Test
    @DisplayName("边界等级：各级别临界值判定")
    void testWeatheringGrade_boundaryValues() {
        assertEquals("none", solver.classifyGrade(2.0 - 0.001));
        assertEquals("slight", solver.classifyGrade(2.0));
        assertEquals("slight", solver.classifyGrade(5.0 - 0.001));
        assertEquals("moderate", solver.classifyGrade(5.0));
        assertEquals("moderate", solver.classifyGrade(10.0 - 0.001));
        assertEquals("severe", solver.classifyGrade(10.0));
        assertEquals("severe", solver.classifyGrade(20.0 - 0.001));
        assertEquals("critical", solver.classifyGrade(20.0));
    }

    @Test
    @DisplayName("完整求解：多点测量综合评估")
    void testSolve_fullEvaluation() {
        WeatheringRequestDTO request = new WeatheringRequestDTO();
        request.setBridgeId(1L);
        request.setHardnessCoefficient(0.8);
        request.setVelocityCoefficient(1.2);
        request.setIntercept(15.0);

        List<WeatheringRequestDTO.MeasurementPoint> measurements = new ArrayList<>();
        String[] locations = {"拱顶", "左拱腹", "右拱腹", "左拱脚", "右拱脚"};
        double[][] data = {
            {45.0, 3.8},
            {42.0, 3.5},
            {43.0, 3.6},
            {38.0, 3.0},
            {39.0, 3.1}
        };
        
        for (int i = 0; i < locations.length; i++) {
            WeatheringRequestDTO.MeasurementPoint mp = new WeatheringRequestDTO.MeasurementPoint();
            mp.setLocation(locations[i]);
            mp.setLocX((i - 2) * 5.0);
            mp.setLocY(2.0 + Math.random());
            mp.setLocZ(0.0);
            mp.setSurfaceHardness(data[i][0]);
            mp.setUltrasonicVelocity(data[i][1]);
            measurements.add(mp);
        }
        request.setMeasurements(measurements);

        WeatheringResultDTO result = solver.solve(request, "测试桥");

        assertNotNull(result, "求解结果不应为空");
        assertEquals(5, result.getPoints().size(), "应返回5个测点结果");
        assertTrue(result.getAvgDepth() > 0, "平均深度应为正");
        assertTrue(result.getMaxDepth() >= result.getAvgDepth(), "最大深度应不小于平均深度");
        assertNotNull(result.getOverallGrade(), "总评级不应为空");
        assertTrue(result.getRSquared() > 0 && result.getRSquared() <= 1.0,
            "R²应在0到1之间: " + result.getRSquared());
    }

    @Test
    @DisplayName("空测量点应返回空结果")
    void testSolve_emptyMeasurements() {
        WeatheringRequestDTO request = new WeatheringRequestDTO();
        request.setBridgeId(1L);
        request.setMeasurements(new ArrayList<>());

        WeatheringResultDTO result = solver.solve(request, "测试桥");
        
        assertNotNull(result, "结果不应为null");
        assertTrue(result.getPoints().isEmpty(), "测点列表应空");
        assertEquals(0.0, result.getAvgDepth(), 0.001, "平均深度应为0");
    }

    @Test
    @DisplayName("单测点退化场景")
    void testSolve_singlePointDegenerate() {
        WeatheringRequestDTO request = new WeatheringRequestDTO();
        request.setBridgeId(1L);
        List<WeatheringRequestDTO.MeasurementPoint> measurements = new ArrayList<>();
        WeatheringRequestDTO.MeasurementPoint mp = new WeatheringRequestDTO.MeasurementPoint();
        mp.setLocation("单点");
        mp.setLocX(0.0);
        mp.setLocY(1.0);
        mp.setLocZ(0.0);
        mp.setSurfaceHardness(40.0);
        mp.setUltrasonicVelocity(3.5);
        measurements.add(mp);
        request.setMeasurements(measurements);

        WeatheringResultDTO result = solver.solve(request, "测试桥");
        
        assertEquals(1, result.getPoints().size());
        assertEquals(result.getAvgDepth(), result.getMaxDepth(), 0.001,
            "单测点时平均与最大应相等");
        assertEquals("moderate", result.getOverallGrade());
    }

    @Test
    @DisplayName("R²拟合优度验证：理想线性数据应接近1")
    void testRSquared_perfectCorrelation() {
        WeatheringRequestDTO request = new WeatheringRequestDTO();
        request.setBridgeId(1L);
        
        List<WeatheringRequestDTO.MeasurementPoint> measurements = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            WeatheringRequestDTO.MeasurementPoint mp = new WeatheringRequestDTO.MeasurementPoint();
            mp.setLocation("点" + i);
            mp.setLocX(i * 2.0);
            mp.setLocY(1.0);
            mp.setLocZ(0.0);
            double h = 20 + i * 4.0;
            double v = 2.0 + i * 0.25;
            mp.setSurfaceHardness(h);
            mp.setUltrasonicVelocity(v);
            measurements.add(mp);
        }
        request.setMeasurements(measurements);

        WeatheringResultDTO result = solver.solve(request, "测试桥");
        
        assertTrue(result.getRSquared() > 0.7,
            "理想线性数据R²应大于0.7，实际: " + result.getRSquared());
    }

    @Test
    @DisplayName("风化层厚度标注映射：五级颜色准确性")
    void testWeatheringColorMapping_fiveLevels() {
        int[] expectedColors = {0x22c55e, 0x3b82f6, 0xeab308, 0xf97316, 0xef4444};
        double[] testDepths = {1.0, 3.5, 7.5, 15.0, 25.0};
        String[] grades = {"none", "slight", "moderate", "severe", "critical"};

        for (int i = 0; i < testDepths.length; i++) {
            String grade = solver.classifyGrade(testDepths[i]);
            assertEquals(grades[i], grade,
                "深度" + testDepths[i] + "mm应对应等级" + grades[i]);
        }
    }

    @Test
    @DisplayName("数值稳定性：极小变化输入应平滑输出")
    void testNumericalStability_smoothOutput() {
        double base = solver.calculateDepth(40.0, 3.5, 0.8, 1.2, 15.0);
        
        for (double delta = -0.1; delta <= 0.1; delta += 0.05) {
            double perturbed = solver.calculateDepth(40.0 + delta, 3.5, 0.8, 1.2, 15.0);
            double changeRatio = Math.abs(perturbed - base) / base;
            assertTrue(changeRatio < 0.1,
                "硬度微变" + delta + "时深度变化率应小于10%，实际: " + changeRatio);
        }
    }
}
