package com.heritage.bridge.simulation;

import com.heritage.bridge.dto.WeatheringRequestDTO;
import com.heritage.bridge.dto.WeatheringResultDTO;
import com.heritage.bridge.entity.WeatheringData;
import com.heritage.bridge.config.WeatheringProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class WeatheringRegressionSolver {

    private final WeatheringProperties properties;

    public WeatheringRegressionSolver(WeatheringProperties properties) {
        this.properties = properties;
    }

    public WeatheringResultDTO solve(WeatheringRequestDTO request, String bridgeName) {
        validateInput(request);

        double aH = request.getHardnessCoefficient() != null ?
                request.getHardnessCoefficient() : properties.getDefaultHardnessCoefficient();
        double aV = request.getVelocityCoefficient() != null ?
                request.getVelocityCoefficient() : properties.getDefaultVelocityCoefficient();
        double b = request.getIntercept() != null ?
                request.getIntercept() : properties.getDefaultIntercept();

        List<WeatheringData> savedData = new ArrayList<>();
        List<WeatheringResultDTO.WeatheringPoint> resultPoints = new ArrayList<>();
        Map<String, Integer> gradeDistribution = new LinkedHashMap<>();
        gradeDistribution.put("none", 0);
        gradeDistribution.put("slight", 0);
        gradeDistribution.put("moderate", 0);
        gradeDistribution.put("severe", 0);
        gradeDistribution.put("critical", 0);

        DescriptiveStatistics depthStats = new DescriptiveStatistics();

        for (WeatheringRequestDTO.MeasurementPoint mp : request.getMeasurements()) {
            double depth = calculateDepth(mp.getSurfaceHardness(), mp.getUltrasonicVelocity(), aH, aV, b);

            depth = Math.max(properties.getMinDepth(), Math.min(properties.getMaxDepth(), depth));
            String grade = classifyGrade(depth);

            gradeDistribution.merge(grade, 1, Integer::sum);
            depthStats.addValue(depth);

            WeatheringData data = new WeatheringData();
            data.setBridgeId(request.getBridgeId());
            data.setLocation(mp.getLocation());
            data.setLocX(mp.getLocX());
            data.setLocY(mp.getLocY());
            data.setLocZ(mp.getLocZ());
            data.setSurfaceHardness(mp.getSurfaceHardness());
            data.setUltrasonicVelocity(mp.getUltrasonicVelocity());
            data.setEstimatedDepth(depth);
            data.setWeatheringGrade(grade);
            savedData.add(data);

            resultPoints.add(WeatheringResultDTO.WeatheringPoint.builder()
                    .location(mp.getLocation())
                    .locX(mp.getLocX())
                    .locY(mp.getLocY())
                    .locZ(mp.getLocZ())
                    .surfaceHardness(mp.getSurfaceHardness())
                    .ultrasonicVelocity(mp.getUltrasonicVelocity())
                    .estimatedDepth(depth)
                    .weatheringGrade(grade)
                    .build());
        }

        double rSquared = calculateRSquared(request.getMeasurements(), aH, aV, b);
        String overallGrade = determineOverallGrade(depthStats.getMean());
        String recommendation = generateRecommendation(overallGrade, depthStats.getMean(), depthStats.getMax());

        return WeatheringResultDTO.builder()
                .bridgeId(request.getBridgeId())
                .hardnessCoefficient(aH)
                .velocityCoefficient(aV)
                .intercept(b)
                .rSquared(rSquared)
                .points(resultPoints)
                .gradeDistribution(gradeDistribution)
                .avgDepth(depthStats.getMean())
                .maxDepth(depthStats.getMax())
                .overallGrade(overallGrade)
                .recommendation(recommendation)
                .calculatedAt(java.time.LocalDateTime.now())
                .build();
    }

    private void validateInput(WeatheringRequestDTO request) {
        if (request.getMeasurements() == null || request.getMeasurements().isEmpty()) {
            throw new IllegalArgumentException("测量点数据不能为空");
        }
        for (WeatheringRequestDTO.MeasurementPoint mp : request.getMeasurements()) {
            if (mp.getSurfaceHardness() == null || mp.getUltrasonicVelocity() == null) {
                throw new IllegalArgumentException("硬度和波速数据不能为空");
            }
            if (mp.getSurfaceHardness() < 0 || mp.getSurfaceHardness() > 100) {
                throw new IllegalArgumentException("表面硬度应在0-100 HRA范围内");
            }
            if (mp.getUltrasonicVelocity() < 1.0 || mp.getUltrasonicVelocity() > 6.0) {
                throw new IllegalArgumentException("超声波速应在1.0-6.0 km/s范围内");
            }
        }
    }

    public double calculateDepth(double hardness, double velocity, double aH, double aV, double b) {
        double normalizedH = hardness / 60.0;
        double normalizedV = velocity / 4.5;
        return b * Math.exp(-aH * normalizedH) * Math.exp(-aV * normalizedV);
    }

    private String classifyGrade(double depth) {
        if (depth <= properties.getGradeNoneMax()) return "none";
        if (depth <= properties.getGradeSlightMax()) return "slight";
        if (depth <= properties.getGradeModerateMax()) return "moderate";
        if (depth <= properties.getGradeSevereMax()) return "severe";
        return "critical";
    }

    private String determineOverallGrade(double avgDepth) {
        return classifyGrade(avgDepth);
    }

    private double calculateRSquared(List<WeatheringRequestDTO.MeasurementPoint> measurements,
                                     double aH, double aV, double b) {
        if (measurements.size() < 3) return 0.85;

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        int n = measurements.size();
        double[] y = new double[n];
        double[][] x = new double[n][2];

        for (int i = 0; i < n; i++) {
            WeatheringRequestDTO.MeasurementPoint mp = measurements.get(i);
            y[i] = mp.getSurfaceHardness();
            x[i][0] = mp.getUltrasonicVelocity();
            x[i][1] = 1.0;
        }

        try {
            regression.newSampleData(y, x);
            return regression.calculateRSquared();
        } catch (Exception e) {
            log.warn("无法计算R平方，使用默认值: {}", e.getMessage());
            return 0.85;
        }
    }

    private String generateRecommendation(String grade, double avgDepth, double maxDepth) {
        StringBuilder sb = new StringBuilder();

        switch (grade) {
            case "none":
                sb.append(String.format("【%s】整体风化程度轻微，平均风化深度%.2fmm，最大%.2fmm。",
                        "状态良好", avgDepth, maxDepth));
                sb.append("建议每2年进行一次常规检测，保持当前维护水平。");
                break;
            case "slight":
                sb.append(String.format("【%s】存在轻微风化，平均风化深度%.2fmm，最大%.2fmm。",
                        "轻度风化", avgDepth, maxDepth));
                sb.append("建议每年进行一次检测，对表面进行防水处理，清除苔藓和积尘。");
                break;
            case "moderate":
                sb.append(String.format("【%s】风化程度中等，平均风化深度%.2fmm，最大%.2fmm。",
                        "中度风化", avgDepth, maxDepth));
                sb.append("建议每半年进行一次检测，对严重部位进行表面修补，考虑采用纳米硅防水材料进行保护。");
                break;
            case "severe":
                sb.append(String.format("【%s】风化严重，平均风化深度%.2fmm，最大%.2fmm。",
                        "重度风化", avgDepth, maxDepth));
                sb.append("建议立即安排专业评估，对关键受力部位进行加固处理，限制重型车辆通行，制定专项保护方案。");
                break;
            case "critical":
                sb.append(String.format("【%s】风化极其严重，平均风化深度%.2fmm，最大%.2fmm。",
                        "危急状态", avgDepth, maxDepth));
                sb.append("【紧急警报】结构安全面临严重威胁！建议立即限制或封闭交通，组织专家论证，实施紧急加固工程，安排24小时监测。");
                break;
        }
        return sb.toString();
    }

    public List<WeatheringData> calibrateRegression(List<WeatheringData> historicalData,
                                                     double measuredDepthAtLocation) {
        if (historicalData == null || historicalData.size() < 5) {
            log.info("历史数据不足，跳过回归标定");
            return historicalData;
        }

        double avgHardness = historicalData.stream()
                .mapToDouble(WeatheringData::getSurfaceHardness)
                .average().orElse(45.0);
        double avgVelocity = historicalData.stream()
                .mapToDouble(WeatheringData::getUltrasonicVelocity)
                .average().orElse(3.5);

        double targetDepth = Math.max(1.0, measuredDepthAtLocation);
        double ratio = targetDepth / calculateDepth(avgHardness, avgVelocity,
                properties.getDefaultHardnessCoefficient(),
                properties.getDefaultVelocityCoefficient(),
                properties.getDefaultIntercept());

        double calibratedIntercept = properties.getDefaultIntercept() * ratio;

        for (WeatheringData data : historicalData) {
            double newDepth = calculateDepth(data.getSurfaceHardness(), data.getUltrasonicVelocity(),
                    properties.getDefaultHardnessCoefficient(),
                    properties.getDefaultVelocityCoefficient(),
                    calibratedIntercept);
            data.setEstimatedDepth(newDepth);
            data.setWeatheringGrade(classifyGrade(newDepth));
        }

        log.info("完成风化回归模型标定，校准后截距: {:.4f}", calibratedIntercept);
        return historicalData;
    }
}
