package com.heritage.bridge.simulation;

import com.heritage.bridge.config.WeatheringProperties;
import com.heritage.bridge.dto.WeatheringRequestDTO;
import com.heritage.bridge.dto.WeatheringResultDTO;
import com.heritage.bridge.entity.WeatheringData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

        int totalReceived = request.getMeasurements().size();
        List<MeasurementWithQuality> qualityChecked = new ArrayList<>();

        if (properties.isDataQualityCheckEnabled() && totalReceived >= 5) {
            qualityChecked = performDataQualityCheck(request.getMeasurements());
        } else {
            for (WeatheringRequestDTO.MeasurementPoint mp : request.getMeasurements()) {
                MeasurementWithQuality mq = new MeasurementWithQuality();
                mq.point = mp;
                mq.couplingQuality = calculateCouplingQuality(mp);
                mq.isValid = true;
                qualityChecked.add(mq);
            }
        }

        List<MeasurementWithQuality> validPoints = qualityChecked.stream()
                .filter(mq -> mq.isValid)
                .collect(Collectors.toList());

        int validCount = validPoints.size();
        int rejectedCount = totalReceived - validCount;
        double passRate = totalReceived > 0 ? (double) validCount / totalReceived : 0;

        if (passRate < properties.getMinDataPassRate() && totalReceived >= 5) {
            log.warn("[{}] 数据合格率过低({:.1%})，低于阈值{:.1%}，放宽异常标准重新评估",
                    bridgeName, passRate, properties.getMinDataPassRate());
            qualityChecked = relaxQualityCheck(request.getMeasurements());
            validPoints = qualityChecked.stream().filter(mq -> mq.isValid).collect(Collectors.toList());
            validCount = validPoints.size();
            rejectedCount = totalReceived - validCount;
            passRate = (double) validCount / totalReceived;
        }

        List<WeatheringData> savedData = new ArrayList<>();
        List<WeatheringResultDTO.WeatheringPoint> resultPoints = new ArrayList<>();
        Map<String, Integer> gradeDistribution = new LinkedHashMap<>();
        gradeDistribution.put("none", 0);
        gradeDistribution.put("slight", 0);
        gradeDistribution.put("moderate", 0);
        gradeDistribution.put("severe", 0);
        gradeDistribution.put("critical", 0);

        DescriptiveStatistics depthStats = new DescriptiveStatistics();

        for (MeasurementWithQuality mq : qualityChecked) {
            WeatheringRequestDTO.MeasurementPoint mp = mq.point;
            double depth = 0;
            String grade = "pending";
            String rejectReason = null;

            if (mq.isValid) {
                depth = calculateDepth(mp.getSurfaceHardness(), mp.getUltrasonicVelocity(), aH, aV, b);
                depth = Math.max(properties.getMinDepth(), Math.min(properties.getMaxDepth(), depth));
                grade = classifyGrade(depth);
                gradeDistribution.merge(grade, 1, Integer::sum);
                depthStats.addValue(depth);
            } else {
                rejectReason = mq.rejectReason;
            }

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
                    .couplingQualityIndex(mq.couplingQuality)
                    .isOutlier(!mq.isValid)
                    .rejectReason(rejectReason)
                    .build());
        }

        double rSquared = validCount >= 3 ?
                calculateRSquared(validPoints, aH, aV, b) : 0.85;

        String overallGrade = depthStats.getN() > 0 ?
                determineOverallGrade(depthStats.getMean()) : "unknown";

        String recommendation = generateRecommendation(
                overallGrade,
                depthStats.getN() > 0 ? depthStats.getMean() : 0,
                depthStats.getN() > 0 ? depthStats.getMax() : 0,
                passRate);

        WeatheringResultDTO.DataQualityReport qualityReport = buildQualityReport(
                request.getMeasurements(), qualityChecked, passRate);

        return WeatheringResultDTO.builder()
                .bridgeId(request.getBridgeId())
                .hardnessCoefficient(aH)
                .velocityCoefficient(aV)
                .intercept(b)
                .rSquared(rSquared)
                .points(resultPoints)
                .gradeDistribution(gradeDistribution)
                .avgDepth(depthStats.getN() > 0 ? depthStats.getMean() : 0)
                .maxDepth(depthStats.getN() > 0 ? depthStats.getMax() : 0)
                .overallGrade(overallGrade)
                .recommendation(recommendation)
                .calculatedAt(LocalDateTime.now())
                .dataQualityReport(qualityReport)
                .totalPoints(totalReceived)
                .validPoints(validCount)
                .rejectedPoints(rejectedCount)
                .dataPassRate(passRate)
                .build();
    }

    private List<MeasurementWithQuality> performDataQualityCheck(
            List<WeatheringRequestDTO.MeasurementPoint> measurements) {

        List<MeasurementWithQuality> result = new ArrayList<>();

        double[] velocities = measurements.stream()
                .mapToDouble(WeatheringRequestDTO.MeasurementPoint::getUltrasonicVelocity)
                .toArray();
        double[] hardnesses = measurements.stream()
                .mapToDouble(WeatheringRequestDTO.MeasurementPoint::getSurfaceHardness)
                .toArray();

        double[] velocityBounds = calculateIQRBounds(velocities, properties.getIqrOutlierThreshold());
        double[] hardnessBounds = calculateIQRBounds(hardnesses, properties.getIqrOutlierThreshold());

        for (WeatheringRequestDTO.MeasurementPoint mp : measurements) {
            MeasurementWithQuality mq = new MeasurementWithQuality();
            mq.point = mp;
            mq.couplingQuality = calculateCouplingQuality(mp);
            mq.isValid = true;

            if (mp.getUltrasonicVelocity() < velocityBounds[0] ||
                    mp.getUltrasonicVelocity() > velocityBounds[1]) {
                mq.isValid = false;
                mq.rejectReason = String.format("IQR异常-波速(%.2f超出[%.2f,%.2f])",
                        mp.getUltrasonicVelocity(), velocityBounds[0], velocityBounds[1]);
                mq.iqrOutlier = true;
            } else if (mp.getSurfaceHardness() < hardnessBounds[0] ||
                    mp.getSurfaceHardness() > hardnessBounds[1]) {
                mq.isValid = false;
                mq.rejectReason = String.format("IQR异常-硬度(%.1f超出[%.1f,%.1f])",
                        mp.getSurfaceHardness(), hardnessBounds[0], hardnessBounds[1]);
                mq.iqrOutlier = true;
            } else if (mq.couplingQuality < properties.getMinCouplingQualityIndex()) {
                mq.isValid = false;
                mq.rejectReason = String.format("耦合不良(质量指数%.2f<%.2f)",
                        mq.couplingQuality, properties.getMinCouplingQualityIndex());
                mq.couplingRejected = true;
            }

            result.add(mq);
        }

        return result;
    }

    private List<MeasurementWithQuality> relaxQualityCheck(
            List<WeatheringRequestDTO.MeasurementPoint> measurements) {
        List<MeasurementWithQuality> result = new ArrayList<>();

        for (WeatheringRequestDTO.MeasurementPoint mp : measurements) {
            MeasurementWithQuality mq = new MeasurementWithQuality();
            mq.point = mp;
            mq.couplingQuality = calculateCouplingQuality(mp);
            mq.isValid = true;

            if (mp.getUltrasonicVelocity() < 1.5 || mp.getUltrasonicVelocity() > 5.5) {
                mq.isValid = false;
                mq.rejectReason = "物理范围异常-波速超出合理范围";
            } else if (mp.getSurfaceHardness() < 10 || mp.getSurfaceHardness() > 90) {
                mq.isValid = false;
                mq.rejectReason = "物理范围异常-硬度超出合理范围";
            } else if (mq.couplingQuality < 0.2) {
                mq.isValid = false;
                mq.rejectReason = String.format("耦合严重不良(%.2f)", mq.couplingQuality);
            }

            result.add(mq);
        }
        return result;
    }

    private double calculateCouplingQuality(WeatheringRequestDTO.MeasurementPoint mp) {
        double snr = mp.getSignalNoiseRatio() != null ? mp.getSignalNoiseRatio() : 20.0;
        double echo = mp.getEchoAmplitude() != null ? mp.getEchoAmplitude() : 80.0;

        double snrScore = Math.min(1.0, Math.max(0, (snr - 10) / 30.0));
        double echoScore = Math.min(1.0, Math.max(0, (echo - 30) / 70.0));

        double v = mp.getUltrasonicVelocity();
        double h = mp.getSurfaceHardness();
        double physicalConsistency = 1.0;
        if (h > 40 && v < 2.5) physicalConsistency *= 0.7;
        if (h < 25 && v > 4.0) physicalConsistency *= 0.7;

        return 0.35 * snrScore + 0.35 * echoScore + 0.3 * physicalConsistency;
    }

    private double[] calculateIQRBounds(double[] data, double threshold) {
        Arrays.sort(data);
        int n = data.length;

        double q1, q3;
        if (n >= 4) {
            q1 = data[(int) Math.ceil(n * 0.25) - 1];
            q3 = data[(int) Math.ceil(n * 0.75) - 1];
        } else if (n == 3) {
            q1 = data[0];
            q3 = data[2];
        } else {
            q1 = data[0];
            q3 = data[n - 1];
        }

        double iqr = q3 - q1;
        double lower = q1 - threshold * iqr;
        double upper = q3 + threshold * iqr;

        if (iqr < 0.1) {
            lower = q1 - 0.5;
            upper = q3 + 0.5;
        }

        return new double[]{lower, upper};
    }

    private WeatheringResultDTO.DataQualityReport buildQualityReport(
            List<WeatheringRequestDTO.MeasurementPoint> measurements,
            List<MeasurementWithQuality> qualityChecked,
            double passRate) {

        int iqrRejected = (int) qualityChecked.stream().filter(m -> m.iqrOutlier).count();
        int couplingRejected = (int) qualityChecked.stream().filter(m -> m.couplingRejected).count();
        double avgCoupling = qualityChecked.stream()
                .mapToDouble(m -> m.couplingQuality)
                .average().orElse(0);

        DescriptiveStatistics hardStats = new DescriptiveStatistics();
        DescriptiveStatistics velStats = new DescriptiveStatistics();
        for (WeatheringRequestDTO.MeasurementPoint mp : measurements) {
            hardStats.addValue(mp.getSurfaceHardness());
            velStats.addValue(mp.getUltrasonicVelocity());
        }

        double hStd = hardStats.getStandardDeviation();
        double vStd = velStats.getStandardDeviation();

        String overallQuality;
        String advice;

        if (passRate >= 0.9 && avgCoupling >= 0.7) {
            overallQuality = "excellent";
            advice = "数据质量优秀，评估结果可靠";
        } else if (passRate >= 0.75 && avgCoupling >= 0.5) {
            overallQuality = "good";
            advice = "数据质量良好，评估结果可信度较高";
        } else if (passRate >= 0.6 && avgCoupling >= 0.4) {
            overallQuality = "fair";
            advice = "数据质量一般，建议对被剔除测点重新检测";
        } else {
            overallQuality = "poor";
            advice = "数据质量较差，建议清洁测点表面、检查探头耦合后重新检测";
        }

        if (vStd > properties.getVelocityStdDevThreshold()) {
            advice += " 注意：波速数据波动较大(σ=" + String.format("%.2f", vStd) + ")，可能存在测点不一致问题";
        }
        if (hStd > properties.getHardnessStdDevThreshold()) {
            advice += " 注意：硬度数据波动较大(σ=" + String.format("%.1f", hStd) + ")，建议检查测量方法一致性";
        }

        return WeatheringResultDTO.DataQualityReport.builder()
                .totalReceived(measurements.size())
                .iqrRejected(iqrRejected)
                .couplingRejected(couplingRejected)
                .duplicateRejected(0)
                .passRate(passRate)
                .avgCouplingQuality(avgCoupling)
                .hardnessStdDev(hStd)
                .velocityStdDev(vStd)
                .overallQuality(overallQuality)
                .qualityAdvice(advice)
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
        if (hardness <= 0) {
            throw new IllegalArgumentException("硬度必须为正值");
        }
        if (velocity <= 0) {
            throw new IllegalArgumentException("超声波速必须为正值");
        }
        double normalizedH = hardness / 60.0;
        double normalizedV = velocity / 4.5;
        return b * Math.exp(-aH * normalizedH) * Math.exp(-aV * normalizedV);
    }

    public String classifyGrade(double depth) {
        if (depth <= properties.getGradeNoneMax()) return "none";
        if (depth <= properties.getGradeSlightMax()) return "slight";
        if (depth <= properties.getGradeModerateMax()) return "moderate";
        if (depth <= properties.getGradeSevereMax()) return "severe";
        return "critical";
    }

    private String determineOverallGrade(double avgDepth) {
        return classifyGrade(avgDepth);
    }

    private double calculateRSquared(List<MeasurementWithQuality> validPoints,
                                     double aH, double aV, double b) {
        if (validPoints.size() < 3) return 0.85;

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        int n = validPoints.size();
        double[] y = new double[n];
        double[][] x = new double[n][2];

        for (int i = 0; i < n; i++) {
            WeatheringRequestDTO.MeasurementPoint mp = validPoints.get(i).point;
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

    private String generateRecommendation(String grade, double avgDepth, double maxDepth, double passRate) {
        StringBuilder sb = new StringBuilder();

        if (passRate < properties.getMinDataPassRate()) {
            sb.append(String.format("【数据质量预警】本次检测数据合格率仅%.1f%%，低于阈值%.1f%%，部分测点因耦合不良或异常被剔除。",
                    passRate * 100, properties.getMinDataPassRate() * 100));
        }

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
            default:
                sb.append("数据不足，建议补充检测后重新评估。");
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

    private static class MeasurementWithQuality {
        WeatheringRequestDTO.MeasurementPoint point;
        double couplingQuality;
        boolean isValid;
        boolean iqrOutlier;
        boolean couplingRejected;
        String rejectReason;
    }
}
