package com.heritage.bridge.simulation;

import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.entity.FemResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
public class FemSolver {

    public static final int DEFAULT_ELEMENTS = 20;
    public static final int DEFAULT_MC_SAMPLES = 1000;
    public static final double DEFAULT_MODULUS_COV = 0.15;
    public static final double DEFAULT_STRENGTH_COV = 0.20;
    public static final double GRAVITY = 9.81;
    public static final double STONE_DENSITY = 2500.0;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolverParams {
        int elementCount;
        double Emean;
        double nu;
        double fcMean;
        double riseSpanRatio;
        double spanLength;
        double pierThickness;
        double trafficLoad;
        double temperatureDelta;
        double stoneStrength;
        double modulusCov;
        double strengthCov;
        int mcSamples;
        boolean stochastic;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        List<FemResult.FemNode> nodes;
        double maxStress;
        double maxStrain;
        double safetyFactor;
        double stressP95;
        double stressP99;
        double pfFailure;
        boolean stochastic;
        int mcSamples;
        double modulusCov;

        public boolean isStochasticCheck() { return stochastic; }
    }

    public Result solve(Bridge bridge, SolverParams p) {
        int n = Math.max(10, p.getElementCount());
        int nodeCount = n + 1;
        double L = p.getSpanLength();
        double f = p.getSpanLength() * p.getRiseSpanRatio();
        double t = p.getPierThickness();
        double A = Math.max(0.8, t) * 1.0;
        double I = Math.pow(t, 3) / 12.0;

        if (!p.isStochastic()) {
            Result r = solveOnce(bridge, p, n, nodeCount, L, f, t, A, I, p.getEmean(), p.getStoneStrength());
            r.setStochastic(false);
            r.setMcSamples(0);
            r.setModulusCov(0);
            return r;
        }

        NormalDistribution modDist = new NormalDistribution(p.getEmean(), p.getEmean() * p.getModulusCov());
        NormalDistribution strDist = new NormalDistribution(p.getStoneStrength(), p.getStoneStrength() * p.getStrengthCov());
        DescriptiveStatistics maxStressStats = new DescriptiveStatistics();
        List<FemResult.FemNode> meanNodes = null;
        int failCount = 0;
        int samples = Math.max(100, p.getMcSamples());

        for (int i = 0; i < samples; i++) {
            double Ei = Math.max(1e9, modDist.sample());
            double fci = Math.max(1e6, strDist.sample());
            Result r = solveOnce(bridge, p, n, nodeCount, L, f, t, A, I, Ei, fci);
            maxStressStats.addValue(r.getMaxStress());
            if (r.getMaxStress() > p.getStoneStrength() * 1e6) failCount++;
            if (i == 0) meanNodes = r.getNodes();
        }

        double[] sorted = maxStressStats.getSortedValues();
        double p95 = sorted[(int) (sorted.length * 0.95)];
        double p99 = sorted[(int) (sorted.length * 0.99)];
        double pf = (double) failCount / samples;
        double maxStress = maxStressStats.getPercentile(50);
        double maxStrain = maxStress / p.getEmean();
        double safetyFactor = p.getStoneStrength() * 1e6 / Math.max(maxStress, 1);

        Result r = new Result();
        r.setNodes(meanNodes);
        r.setMaxStress(maxStress);
        r.setMaxStrain(maxStrain);
        r.setSafetyFactor(safetyFactor);
        r.setStressP95(p95);
        r.setStressP99(p99);
        r.setPfFailure(pf);
        r.setStochastic(true);
        r.setMcSamples(samples);
        r.setModulusCov(p.getModulusCov());
        return r;
    }

    private Result solveOnce(Bridge bridge, SolverParams p, int n, int nodeCount,
                             double L, double f, double t, double A, double I,
                             double E, double fc) {
        int dof = nodeCount * 2;
        double[][] K = new double[dof][dof];
        double[] F = new double[dof];

        double[] xCoords = IntStream.range(0, nodeCount)
                .mapToDouble(i -> -L / 2 + i * L / n).toArray();
        double[] yCoords = Arrays.stream(xCoords)
                .map(x -> 4 * f * (0.25 - x * x / (L * L))).toArray();

        double wSelf = STONE_DENSITY * A * GRAVITY;
        double qLive = p.getTrafficLoad() / Math.max(1.0, L);

        for (int i = 0; i < n; i++) {
            double x1 = xCoords[i], x2 = xCoords[i + 1];
            double y1 = yCoords[i], y2 = yCoords[i + 1];
            double dx = x2 - x1, dy = y2 - y1;
            double Le = Math.sqrt(dx * dx + dy * dy);
            double c = dx / Le, s = dy / Le;
            double kA = E * A / Le;
            double kI = 12 * E * I / Math.pow(Le, 3);
            double[] keA = localAxial(kA, c, s);
            double[] keI = localBending(kI, c, s, E, I, Le);
            addLocalStiffness(K, keA, i);
            addLocalStiffness(K, keI, i);

            double wMid = wSelf + qLive * 0.4;
            F[i * 2 + 1] += -wMid * Le / 2;
            F[(i + 1) * 2 + 1] += -wMid * Le / 2;
        }

        double alphaTemp = 8e-6;
        double deltaT = p.getTemperatureDelta();
        double aFree = alphaTemp * deltaT;
        for (int i = 0; i < n; i++) {
            double dx = xCoords[i + 1] - xCoords[i];
            double dy = yCoords[i + 1] - yCoords[i];
            double Le = Math.sqrt(dx * dx + dy * dy);
            double c = dx / Le, s = dy / Le;
            double np = E * A * aFree;
            F[i * 2] += -c * np;
            F[i * 2 + 1] += -s * np;
            F[(i + 1) * 2] += c * np;
            F[(i + 1) * 2 + 1] += s * np;
        }

        int[] constrained = {0, 1, (nodeCount - 1) * 2, (nodeCount - 1) * 2 + 1};
        Arrays.sort(constrained);
        List<Integer> freeDofs = new ArrayList<>();
        for (int d = 0; d < dof; d++) {
            if (Arrays.binarySearch(constrained, d) < 0) freeDofs.add(d);
        }
        int fN = freeDofs.size();
        double[][] Kf = new double[fN][fN];
        double[] Ff = new double[fN];
        for (int a = 0; a < fN; a++) {
            int ia = freeDofs.get(a);
            Ff[a] = F[ia];
            for (int b = 0; b < fN; b++) {
                Kf[a][b] = K[ia][freeDofs.get(b)];
            }
        }
        RealMatrix Km = new Array2DRowRealMatrix(Kf);
        DecompositionSolver solver = new LUDecomposition(Km).getSolver();
        if (!solver.isNonSingular()) {
            throw new IllegalStateException("刚度矩阵奇异，检查边界条件");
        }
        double[] Uf = solver.solve(Ff);
        double[] U = new double[dof];
        for (int a = 0; a < fN; a++) U[freeDofs.get(a)] = Uf[a];

        List<FemResult.FemNode> nodes = new ArrayList<>();
        double maxStress = 0, maxStrain = 0;
        for (int i = 0; i < nodeCount; i++) {
            double stress = computeStress(i, n, xCoords, yCoords, U, E, I, A);
            double strain = stress / E;
            if (Math.abs(stress) > maxStress) maxStress = Math.abs(stress);
            if (Math.abs(strain) > maxStrain) maxStrain = Math.abs(strain);
            FemResult.FemNode fn = new FemResult.FemNode();
            fn.setX(bd(xCoords[i]));
            fn.setY(bd(yCoords[i]));
            fn.setZ(bd(0.0));
            fn.setStress(bd(stress));
            fn.setStrain(bd(strain * 1e6));
            nodes.add(fn);
        }
        double sf = fc * 1e6 / Math.max(maxStress, 1);
        Result r = new Result();
        r.setNodes(nodes);
        r.setMaxStress(maxStress);
        r.setMaxStrain(maxStrain * 1e6);
        r.setSafetyFactor(sf);
        r.setStressP95(maxStress * 1.15);
        r.setStressP99(maxStress * 1.3);
        r.setPfFailure(0.0);
        return r;
    }

    private double[] localAxial(double kA, double c, double s) {
        double cc = c * c, ss = s * s, cs = c * s;
        return new double[]{
                cc * kA, cs * kA, -cc * kA, -cs * kA,
                cs * kA, ss * kA, -cs * kA, -ss * kA,
                -cc * kA, -cs * kA, cc * kA, cs * kA,
                -cs * kA, -ss * kA, cs * kA, ss * kA
        };
    }

    private double[] localBending(double kI, double c, double s, double E, double I, double L) {
        double k6 = 6 * E * I / (L * L);
        double k12 = 12 * E * I / Math.pow(L, 3);
        double[][] K = {
                {k12, k6, -k12, k6},
                {k6, 4 * E * I / L, -k6, 2 * E * I / L},
                {-k12, -k6, k12, -k6},
                {k6, 2 * E * I / L, -k6, 4 * E * I / L}
        };
        double[][] T = new double[4][4];
        T[0][0] = c; T[0][1] = s;
        T[1][0] = -s; T[1][1] = c;
        T[2][2] = c; T[2][3] = s;
        T[3][2] = -s; T[3][3] = c;
        double[][] R = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s0 = 0;
                for (int k = 0; k < 4; k++) s0 += K[i][k] * T[k][j];
                R[i][j] = s0;
            }
        }
        double[] flat = new double[16];
        for (int i = 0; i < 4; i++) System.arraycopy(R[i], 0, flat, i * 4, 4);
        return flat;
    }

    private void addLocalStiffness(double[][] K, double[] ke, int i) {
        int[] dofs = {i * 2, i * 2 + 1, (i + 1) * 2, (i + 1) * 2 + 1};
        if (ke.length == 16) {
            for (int a = 0; a < 4; a++)
                for (int b = 0; b < 4; b++)
                    K[dofs[a]][dofs[b]] += ke[a * 4 + b];
        }
    }

    private double computeStress(int node, int n, double[] x, double[] y, double[] U,
                                 double E, double I, double A) {
        if (node >= n) node = n - 1;
        double dx = x[node + 1] - x[node];
        double dy = y[node + 1] - y[node];
        double Le = Math.sqrt(dx * dx + dy * dy);
        double u1 = U[node * 2], v1 = U[node * 2 + 1];
        double u2 = U[(node + 1) * 2], v2 = U[(node + 1) * 2 + 1];
        double du = (u2 - u1) * dx / Le + (v2 - v1) * dy / Le;
        double epsAxial = du / Le;
        double d2y = (y[Math.min(node + 1, n)] - 2 * y[node] + y[Math.max(node - 1, 0)]) / Math.pow(Le, 2);
        double M = -E * I * d2y;
        double sigmaBending = M * (0.5) / I;
        double sigmaAxial = E * epsAxial;
        return sigmaAxial + sigmaBending;
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
    }
}
