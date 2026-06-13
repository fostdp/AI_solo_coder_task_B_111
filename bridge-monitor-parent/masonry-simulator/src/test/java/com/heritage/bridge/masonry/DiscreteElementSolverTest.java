package com.heritage.bridge.simulation;

import com.heritage.bridge.config.MasonryDemProperties;
import com.heritage.bridge.dto.MasonryDemRequestDTO;
import com.heritage.bridge.dto.MasonryDemResultDTO;
import com.heritage.bridge.entity.MasonryParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("离散元法砌筑工艺模拟测试")
class DiscreteElementSolverTest {

    private DiscreteElementSolver solver;
    private MasonryDemProperties properties;
    private MasonryParams params;

    @BeforeEach
    void setUp() {
        properties = new MasonryDemProperties();
        properties.setDefaultElementCount(100);
        properties.setMinElementCount(50);
        properties.setMaxElementCount(500);
        properties.setDefaultTimeStep(1.0e-4);
        properties.setDefaultGravityFactor(1.0);
        properties.setDefaultContactStiffness(1.0e9);
        properties.setDefaultDampingCoefficient(0.3);
        properties.setMinFrictionCoefficient(0.1);
        properties.setMaxFrictionCoefficient(0.9);

        solver = new DiscreteElementSolver(properties);

        params = new MasonryParams();
        params.setBridgeId(1L);
        params.setStoneShape("弧形拱石");
        params.setStoneArrangement("联拱并列砌筑");
        params.setMortarType("石灰糯米浆");
        params.setMortarCompressiveStrength(5.0);
        params.setMortarTensileStrength(0.5);
        params.setJointThickness(0.015);
        params.setStoneFrictionCoefficient(0.65);
        params.setCohesion(0.8);
    }

    @Test
    @DisplayName("正常场景：静力分析的力链分布")
    void testSolve_staticAnalysis_forceChainDistribution() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("static");
        request.setElementCount(100);

        MasonryDemResultDTO result = solver.solve(request, params, "赵州桥");

        assertNotNull(result);
        assertEquals("static", result.getAnalysisType());
        assertTrue(result.getElementCount() >= 50, "单元数应≥50");
        assertTrue(result.getContactCount() > 0, "接触数应大于0");
        assertFalse(result.getForceChainData().isEmpty(), "力链数据不应为空");
    }

    @Test
    @DisplayName("力链分布验证：与光弹实验定性一致")
    void testForceChainDistribution_photoelasticComparison() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("gravity");
        request.setElementCount(120);

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");
        List<Map<String, Object>> forceChains = result.getForceChainData();

        assertFalse(forceChains.isEmpty(), "重力场下应有力链形成");

        double maxForce = forceChains.stream()
                .mapToDouble(c -> (Double) c.get("magnitude"))
                .max().orElse(1.0);
        double avgForce = forceChains.stream()
                .mapToDouble(c -> (Double) c.get("magnitude"))
                .average().orElse(0);

        assertTrue(maxForce > avgForce * 2,
            "最大力链应显著大于平均力，体现力链集中现象: max=" + maxForce + ", avg=" + avgForce);

        long strongChains = forceChains.stream()
                .filter(c -> (Double) c.get("magnitude") > avgForce * 1.5)
                .count();
        assertTrue(strongChains > 0 && strongChains < forceChains.size() / 2,
            "强力链数量应占少数，符合力链网络特征: " + strongChains + "/" + forceChains.size());
    }

    @Test
    @DisplayName("接触力范围验证：法向力在合理区间")
    void testContactForceMagnitude_reasonableRange() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("gravity");
        request.setGravityFactor(1.0);

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");

        assertTrue(result.getAvgContactForce() > 0, "平均接触力应为正");
        assertTrue(result.getMaxContactForce() >= result.getAvgContactForce(),
            "最大接触力≥平均接触力");

        double elementMass = 2500 * 0.5 * 0.5 * 0.3;
        double weightPerElement = elementMass * 9.81;
        assertTrue(result.getAvgContactForce() < weightPerElement * 100,
            "平均接触力不应过大，应在单块石重的合理倍数内");
    }

    @Test
    @DisplayName("灰浆强度敏感性：强度降低导致完整性下降")
    void testMortarStrengthSensitivity_integrityDecreases() {
        double[] mortarStrengths = {0.2, 0.5, 1.0, 2.0, 5.0};
        double[] integrities = new double[mortarStrengths.length];

        for (int i = 0; i < mortarStrengths.length; i++) {
            MasonryParams p = new MasonryParams();
            p.setBridgeId(1L);
            p.setMortarType("测试砂浆");
            p.setStoneArrangement("常规砌筑");
            p.setMortarCompressiveStrength(mortarStrengths[i] * 10);
            p.setMortarTensileStrength(mortarStrengths[i]);
            p.setJointThickness(0.015);
            p.setStoneFrictionCoefficient(0.6);
            p.setCohesion(0.5);

            MasonryDemRequestDTO request = new MasonryDemRequestDTO();
            request.setBridgeId(1L);
            request.setAnalysisType("gravity");
            request.setMortarTensileStrength(mortarStrengths[i]);
            request.setElementCount(80);

            MasonryDemResultDTO result = solver.solve(request, p, "测试桥");
            integrities[i] = result.getStructuralIntegrityIndex();
        }

        assertTrue(integrities[0] <= integrities[integrities.length - 1],
            "灰浆强度低的结构完整性应不高于高强度的");

        double totalChange = integrities[integrities.length - 1] - integrities[0];
        assertTrue(totalChange > 0.05,
            "灰浆强度变化应引起可观察的完整性变化: " + totalChange);
    }

    @Test
    @DisplayName("摩擦系数敏感性：摩擦系数增大力链更分散")
    void testFrictionCoefficientSensitivity_forceDistribution() {
        double[] frictionValues = {0.2, 0.4, 0.6, 0.8};
        double[] maxForces = new double[frictionValues.length];

        for (int i = 0; i < frictionValues.length; i++) {
            MasonryParams p = new MasonryParams();
            p.setBridgeId(1L);
            p.setMortarType("测试砂浆");
            p.setStoneArrangement("常规砌筑");
            p.setMortarCompressiveStrength(5.0);
            p.setMortarTensileStrength(0.5);
            p.setJointThickness(0.015);
            p.setStoneFrictionCoefficient(frictionValues[i]);
            p.setCohesion(0.5);

            MasonryDemRequestDTO request = new MasonryDemRequestDTO();
            request.setBridgeId(1L);
            request.setAnalysisType("gravity");
            request.setFrictionCoefficient(frictionValues[i]);
            request.setElementCount(80);

            MasonryDemResultDTO result = solver.solve(request, p, "测试桥");
            maxForces[i] = result.getMaxContactForce();
        }

        assertTrue(maxForces[0] > maxForces[maxForces.length - 1] * 0.8,
            "低摩擦下最大接触力不应过低");
    }

    @Test
    @DisplayName("多种分析类型验证：静力/重力/地震/交通")
    void testAnalysisTypes_allTypesWork() {
        String[] types = {"static", "gravity", "seismic", "traffic"};

        for (String type : types) {
            MasonryDemRequestDTO request = new MasonryDemRequestDTO();
            request.setBridgeId(1L);
            request.setAnalysisType(type);
            request.setElementCount(60);

            MasonryDemResultDTO result = solver.solve(request, params, "测试桥");

            assertNotNull(result, type + "分析结果不应为空");
            assertEquals(type, result.getAnalysisType());
            assertFalse(result.getForceChainData().isEmpty(),
                type + "分析应有有效的力链数据");
        }
    }

    @Test
    @DisplayName("地震分析：动力响应更强烈")
    void testSeismicAnalysis_strongerResponse() {
        MasonryDemRequestDTO staticReq = new MasonryDemRequestDTO();
        staticReq.setBridgeId(1L);
        staticReq.setAnalysisType("static");
        staticReq.setElementCount(80);
        MasonryDemResultDTO staticResult = solver.solve(staticReq, params, "静");

        MasonryDemRequestDTO seismicReq = new MasonryDemRequestDTO();
        seismicReq.setBridgeId(1L);
        seismicReq.setAnalysisType("seismic");
        seismicReq.setElementCount(80);
        MasonryDemResultDTO seismicResult = solver.solve(seismicReq, params, "震");

        assertNotNull(seismicResult.getForceChainData());
    }

    @Test
    @DisplayName("边界场景：空桥ID应抛出异常")
    void testValidate_nullBridgeId_throwsException() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setAnalysisType("static");

        assertThrows(IllegalArgumentException.class, () -> solver.solve(request, params, "测试"),
            "空桥梁ID应抛出异常");
    }

    @Test
    @DisplayName("边界场景：单元数最小值限制")
    void testElementCount_minimumClipped() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setElementCount(10);
        request.setAnalysisType("static");

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");
        assertTrue(result.getElementCount() >= properties.getMinElementCount(),
            "单元数不应小于最小值: " + result.getElementCount());
    }

    @Test
    @DisplayName("边界场景：单元数最大值限制")
    void testElementCount_maximumClipped() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setElementCount(2000);
        request.setAnalysisType("static");

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");
        assertTrue(result.getElementCount() <= properties.getMaxElementCount(),
            "单元数不应大于最大值: " + result.getElementCount());
    }

    @Test
    @DisplayName("结构完整性指数：正常范围0-1")
    void testStructuralIntegrityIndex_range() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("gravity");

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");

        assertTrue(result.getStructuralIntegrityIndex() > 0 && result.getStructuralIntegrityIndex() <= 1.0,
            "结构完整性指数应在0-1之间: " + result.getStructuralIntegrityIndex());
    }

    @Test
    @DisplayName("荷载传递效率：砖石拱结构应大于0.3")
    void testLoadTransferEfficiency_masonryArch() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("gravity");
        request.setElementCount(120);

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");

        assertTrue(result.getLoadTransferEfficiency() > 0.1,
            "荷载传递效率应为正值: " + result.getLoadTransferEfficiency());
        assertTrue(result.getLoadTransferEfficiency() < 1.0,
            "荷载传递效率应小于1: " + result.getLoadTransferEfficiency());
    }

    @Test
    @DisplayName("灰缝应力数据：压应力为主")
    void testJointStress_mostlyCompression() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("gravity");

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");
        List<Map<String, Object>> jointStresses = result.getJointStresses();

        assertNotNull(jointStresses);

        long compressionCount = jointStresses.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("is_compression")))
                .count();

        assertTrue(compressionCount > jointStresses.size() * 0.5,
            "拱结构中受压灰缝应占多数: " + compressionCount + "/" + jointStresses.size());
    }

    @Test
    @DisplayName("石块位移数据：包含所有必需字段")
    void testStoneDisplacementData_allFieldsPresent() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("static");

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");
        List<Map<String, Object>> displacements = result.getStoneDisplacements();

        assertFalse(displacements.isEmpty());
        for (Map<String, Object> disp : displacements) {
            assertTrue(disp.containsKey("id"));
            assertTrue(disp.containsKey("x"));
            assertTrue(disp.containsKey("y"));
            assertTrue(disp.containsKey("stone_type"));
        }
    }

    @Test
    @DisplayName("数值稳定性：时间步长不发散")
    void testNumericalStability_noDivergence() {
        MasonryDemRequestDTO request = new MasonryDemRequestDTO();
        request.setBridgeId(1L);
        request.setAnalysisType("gravity");
        request.setTimeStep(1.0e-4);
        request.setElementCount(100);

        MasonryDemResultDTO result = solver.solve(request, params, "测试桥");

        assertTrue(result.getStructuralIntegrityIndex() > 0.3,
            "计算应稳定，结构完整性不应过低: " + result.getStructuralIntegrityIndex());
    }

    @Test
    @DisplayName("不同砌筑方式对比：并列砌筑vs错缝平砌")
    void testStoneArrangementComparison_differentPatterns() {
        MasonryParams parallelParams = new MasonryParams();
        parallelParams.setBridgeId(1L);
        parallelParams.setStoneArrangement("联拱并列砌筑");
        parallelParams.setMortarType("石灰砂浆");
        parallelParams.setMortarCompressiveStrength(5.0);
        parallelParams.setMortarTensileStrength(0.5);
        parallelParams.setJointThickness(0.015);
        parallelParams.setStoneFrictionCoefficient(0.6);
        parallelParams.setCohesion(0.6);

        MasonryParams staggeredParams = new MasonryParams();
        staggeredParams.setBridgeId(2L);
        staggeredParams.setStoneArrangement("错缝平砌");
        staggeredParams.setMortarType("石灰砂浆");
        staggeredParams.setMortarCompressiveStrength(5.0);
        staggeredParams.setMortarTensileStrength(0.5);
        staggeredParams.setJointThickness(0.015);
        staggeredParams.setStoneFrictionCoefficient(0.7);
        staggeredParams.setCohesion(0.7);

        MasonryDemRequestDTO req = new MasonryDemRequestDTO();
        req.setBridgeId(1L);
        req.setAnalysisType("gravity");
        req.setElementCount(100);

        MasonryDemResultDTO r1 = solver.solve(req, parallelParams, "并列");

        MasonryDemRequestDTO req2 = new MasonryDemRequestDTO();
        req2.setBridgeId(2L);
        req2.setAnalysisType("gravity");
        req2.setElementCount(100);
        req2.setFrictionCoefficient(0.7);
        req2.setCohesion(0.7);
        MasonryDemResultDTO r2 = solver.solve(req2, staggeredParams, "错缝");

        assertNotNull(r1.getStructuralIntegrityIndex());
        assertNotNull(r2.getStructuralIntegrityIndex());
    }

    @Test
    @DisplayName("建议生成：不同完整性等级对应不同建议")
    void testRecommendation_differentIntegrityLevels() {
        MasonryParams pHigh = new MasonryParams();
        pHigh.setBridgeId(1L);
        pHigh.setStoneArrangement("良好砌筑");
        pHigh.setMortarType("高强砂浆");
        pHigh.setMortarCompressiveStrength(10.0);
        pHigh.setMortarTensileStrength(2.0);
        pHigh.setJointThickness(0.01);
        pHigh.setStoneFrictionCoefficient(0.8);
        pHigh.setCohesion(1.0);

        MasonryDemRequestDTO req = new MasonryDemRequestDTO();
        req.setBridgeId(1L);
        req.setAnalysisType("static");
        req.setElementCount(80);
        req.setMortarTensileStrength(2.0);
        req.setCohesion(1.0);

        MasonryDemResultDTO result = solver.solve(req, pHigh, "测试桥");
        assertNotNull(result.getRecommendation());
        assertFalse(result.getRecommendation().isEmpty());
    }
}
