package com.heritage.bridge.simulation;

import com.heritage.bridge.entity.SensorData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Component
public class BayesianParisCalibrator {

    public static final int DEFAULT_MCMC_SAMPLES = 10000;
    public static final int DEFAULT_BURNIN = 2000;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalibrationInput {
        double initialC;
        double initialM;
        double priorC_mean;
        double priorC_std;
        double priorM_mean;
        double priorM_std;
        int mcmcSamples;
        int burnin;
        double stressAmplitude;
        int annualCycles;
        List<double[]> history;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalibrationResult {
        double cPostMean;
        double cPostStd;
        double mPostMean;
        double mPostStd;
        int samples;
    }

    public CalibrationResult calibrate(CalibrationInput in) {
        int N = Math.max(1000, in.getMcmcSamples());
        int burnin = Math.max(100, in.getBurnin());
        List<double[]> history = in.getHistory() == null ? new ArrayList<>() : in.getHistory();

        NormalDistribution pC = new NormalDistribution(
                in.getPriorC_mean() > 0 ? in.getPriorC_mean() : 1e-12,
                in.getPriorC_std() > 0 ? in.getPriorC_std() : 5e-13
        );
        NormalDistribution pM = new NormalDistribution(
                in.getPriorM_mean() > 0 ? in.getPriorM_mean() : 3.0,
                in.getPriorM_std() > 0 ? in.getPriorM_std() : 0.5
        );

        NormalDistribution qC = new NormalDistribution(0, pC.getStandardDeviation() * 0.1);
        NormalDistribution qM = new NormalDistribution(0, pM.getStandardDeviation() * 0.1);

        double cCur = Math.max(1e-20, in.getInitialC() > 0 ? in.getInitialC() : pC.sample());
        double mCur = Math.max(0.1, in.getInitialM() > 0 ? in.getInitialM() : pM.sample());
        double logLcur = logLikelihood(cCur, mCur, history, in);

        List<Double> cTrace = new ArrayList<>(N - burnin);
        List<Double> mTrace = new ArrayList<>(N - burnin);

        Random rnd = new Random(42);
        int accepted = 0;
        for (int i = 0; i < N; i++) {
            double cProp = Math.max(1e-25, cCur + qC.sample());
            double mProp = Math.max(0.1, mCur + qM.sample());
            double logLprop = logLikelihood(cProp, mProp, history, in);

            double logPriorCur = Math.log(Math.max(1e-300, pC.density(cCur)))
                               + Math.log(Math.max(1e-300, pM.density(mCur)));
            double logPriorProp = Math.log(Math.max(1e-300, pC.density(cProp)))
                                + Math.log(Math.max(1e-300, pM.density(mProp)));
            double logAlpha = (logLprop + logPriorProp) - (logLcur + logPriorCur);

            if (Math.log(rnd.nextDouble()) < logAlpha) {
                cCur = cProp; mCur = mProp; logLcur = logLprop; accepted++;
            }
            if (i >= burnin) {
                cTrace.add(cCur);
                mTrace.add(mCur);
            }
        }
        log.info("MCMC accept ratio: {}", (double) accepted / N);
        double cMean = cTrace.stream().mapToDouble(Double::doubleValue).average().orElse(pC.getMean());
        double cStd = stdev(cTrace);
        double mMean = mTrace.stream().mapToDouble(Double::doubleValue).average().orElse(pM.getMean());
        double mStd = stdev(mTrace);
        return new CalibrationResult(cMean, cStd, mMean, mStd, cTrace.size());
    }

    private double logLikelihood(double C, double m, List<double[]> history, CalibrationInput in) {
        double dS = in.getStressAmplitude() > 0 ? in.getStressAmplitude() : 5e6;
        int cycles = in.getAnnualCycles() > 0 ? in.getAnnualCycles() : 365;
        if (history.isEmpty()) return 0;
        double sum = 0;
        for (double[] h : history) {
            double a0 = h[0];
            double tYears = h[1];
            double aMeasured = h[2];
            double aPredicted = integrateParis(a0, C, m, dS, tYears, cycles);
            double err = aMeasured - aPredicted;
            double sigma = Math.max(0.5e-3, 0.1 * Math.abs(aMeasured));
            sum += -0.5 * Math.log(2 * Math.PI * sigma * sigma) - 0.5 * (err * err) / (sigma * sigma);
        }
        return sum;
    }

    public static double integrateParis(double a0, double C, double m, double dS, double years, int annualCycles) {
        double totalCycles = years * annualCycles;
        double a = a0;
        int steps = 1000;
        double dN = totalCycles / steps;
        for (int i = 0; i < steps; i++) {
            double dK = dS * Math.sqrt(Math.PI * Math.max(1e-6, a));
            double dAdN = C * Math.pow(dK, m);
            a += dAdN * dN;
            if (a > 1.0) break;
        }
        return a;
    }

    public List<double[]> buildHistoryFromData(List<SensorData> data) {
        List<double[]> hist = new ArrayList<>();
        if (data == null || data.size() < 2) return hist;
        data.sort(Comparator.comparing(SensorData::getTimestamp));
        LocalDateTime t0 = data.get(0).getTimestamp();
        double a0 = Math.max(0.5e-3, data.get(0).getValue().doubleValue() * 1e-3);
        hist.add(new double[]{a0, 0, a0});
        for (int i = 1; i < data.size(); i++) {
            SensorData sd = data.get(i);
            double years = ChronoUnit.MINUTES.between(t0, sd.getTimestamp()) / 525600.0;
            double ai = Math.max(0.5e-3, sd.getValue().doubleValue() * 1e-3);
            hist.add(new double[]{a0, Math.max(0.001, years), ai});
        }
        return hist;
    }

    private double stdev(List<Double> x) {
        if (x.size() < 2) return 0;
        double m = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sum = x.stream().mapToDouble(v -> (v - m) * (v - m)).sum();
        return Math.sqrt(sum / (x.size() - 1));
    }

    public BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
