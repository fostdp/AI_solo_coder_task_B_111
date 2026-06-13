package com.heritage.bridge.simulation;

import com.heritage.bridge.config.MasonryDemProperties;
import com.heritage.bridge.dto.MasonryDemRequestDTO;
import com.heritage.bridge.dto.MasonryDemResultDTO;
import com.heritage.bridge.entity.MasonryParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscreteElementSolver {

    private final MasonryDemProperties properties;

    private static final double GRAVITY = 9.81;
    private static final double STONE_DENSITY = 2500.0;

    private ForkJoinPool forkJoinPool;

    public MasonryDemResultDTO solve(MasonryDemRequestDTO request, MasonryParams params, String bridgeName) {
        validateInput(request, params);

        long startTime = System.currentTimeMillis();

        String mode = request.getComputationMode() != null ? request.getComputationMode() : "standard";
        boolean enableParallel = request.getEnableParallel() != null ? request.getEnableParallel() : properties.isParallelComputingEnabled();
        boolean useSimplified = request.getUseSimplifiedContact() != null ? request.getUseSimplifiedContact() : properties.isSimplifiedContactModelEnabled();
        int parallelThreads = request.getParallelThreads() != null ? request.getParallelThreads() : properties.getParallelThreadCount();

        int elementCount = determineElementCount(request);
        int maxSteps = determineMaxSteps(request, mode);
        double convergenceThreshold = determineConvergenceThreshold(request, mode);
        long maxTimeMs = request.getMaxSimulationTimeMs() != null ? request.getMaxSimulationTimeMs() : properties.getMaxSimulationTimeMs();

        if (enableParallel && forkJoinPool == null) {
            forkJoinPool = new ForkJoinPool(parallelThreads);
        }

        double kn = request.getContactStiffness() != null ?
                request.getContactStiffness() : properties.getDefaultContactStiffness();
        double eta = request.getDampingCoefficient() != null ?
                request.getDampingCoefficient() : properties.getDefaultDampingCoefficient();
        double mu = request.getFrictionCoefficient() != null ?
                        (Math.max(properties.getMinFrictionCoefficient(),
                                Math.min(properties.getMaxFrictionCoefficient(), request.getFrictionCoefficient())) :
                0.6;
        double cohesion = request.getCohesion() != null ? request.getCohesion() : 0.5;
        double gFactor = request.getGravityFactor() != null ? request.getGravityFactor() : 1.0;
        double dt = request.getTimeStep() != null ? request.getTimeStep() : properties.getDefaultTimeStep();
        double neighborRadius = request.getNeighborSearchRadius() != null ?
                request.getNeighborSearchRadius() : properties.getNeighborSearchRadius();

        log.info("[{}] DEM模拟开始: 模式={}, 单元数={}, 并行={}, 线程={}, 简化接触={}",
                bridgeName, mode, elementCount, enableParallel, parallelThreads, useSimplified);

        StoneElement[] elements = generateElements(elementCount, params);
        int totalContacts = 0;
        int contactsSkipped = 0;

        double maxForce = 0;
        double totalForce = 0;
        int forceCount = 0;

        int step;
        boolean converged = false;
        double prevMaxDisp = 0;

        Map<Integer, List<Integer>> neighborCache = null;
        if (useSimplified) {
            neighborCache = new HashMap<>();
        }

        for (step = 0; step < maxSteps; step++) {
            if (System.currentTimeMillis() - startTime > maxTimeMs) {
                log.warn("[{}] 模拟超时({}ms)，已执行{}步，提前终止", bridgeName, maxTimeMs, step);
                break;
            }

            double[][] forces = new double[elementCount][3];

            applyGravity(elements, forces, gFactor);

            List<ContactPair> contacts;
            if (useSimplified) {
                contacts = findContactsSimplified(elements, neighborCache, neighborRadius);
            } else {
                contacts = findContactsFull(elements);
            }

            int currentContactsChecked = contacts.size();
            totalContacts += currentContactsChecked;

            if (enableParallel && currentContactsChecked > 50) {
                contactsSkipped += (int)((long)elementCount * (elementCount - 1) / 2 - currentContactsChecked;
                computeContactForcesParallel(contacts, elements, forces, kn, eta, mu, cohesion, dt);
            } else {
                contactsSkipped += Math.max(0, (int)((long)elementCount * (elementCount - 1) / 2 - currentContactsChecked);
                computeContactForcesSequential(contacts, elements, forces, kn, eta, mu, cohesion, dt);
            }

            double maxDisp = updatePositions(elements, forces, dt);
            double convergence = Math.abs(maxDisp - prevMaxDisp);
            prevMaxDisp = maxDisp;

            if (step > 50 && convergence < convergenceThreshold) {
                converged = true;
                log.info("[{}] 模拟在第{}步收敛: 收敛残差={}", bridgeName, step, convergence);
                step++;
                break;
            }

            for (Contact(ContactPair contact : contacts) {
                double fn = Math.abs(contact.normalForce);
                if (fn > maxForce) maxForce = maxForce = fn;
                totalForce += fn;
                forceCount++;
            }
        }

        long computeTime = System.currentTimeMillis() - startTime;

        double avgForce = forceCount > 0 ? totalForce / forceCount : 0;

        List<Map<String, Object>> forceChainData = buildForceChainData(elements, maxForce);

        double integrity = calculateIntegrity(elements, maxForce, avgForce, params);
        double transferEfficiency = calculateLoadTransferEfficiency(elements, maxForce);
        String recommendation = generateRecommendation(integrity, params, mode, enableParallel, useSimplified);

        int finalStep = step;
        int finalContactsChecked = totalContacts;
        int finalContactsSkipped = contactsSkipped;
        boolean finalConverged = converged;
        boolean finalParallel = enableParallel;
        int finalThreads = parallelThreads;
        boolean finalSimplified = useSimplified;

        return MasonryDemResultDTO.builder()
                .bridgeId(request.getBridgeId())
                .analysisType(request.getAnalysisType())
                .computationMode(mode)
                .elementCount(elementCount)
                .contactCount(forceCount > 0 ? forceCount / Math.max(1, finalStep) : 0)
                .maxContactForce(maxForce)
                .avgContactForce(avgForce)
                .forceChainData(forceChainData)
                .stoneDisplacements(buildDisplacementData(elements))
                .jointStresses(buildJointStressData(elements, params))
                .structuralIntegrityIndex(integrity)
                .loadTransferEfficiency(transferEfficiency)
                .mortarType(params.getMortarType())
                .stoneArrangement(params.getStoneArrangement())
                .recommendation(recommendation)
                .calculatedAt(LocalDateTime.now())
                .simulationSteps(finalStep)
                .computationTimeMs(computeTime)
                .parallelEnabled(finalParallel)
                .parallelThreads(finalThreads)
                .simplifiedContactUsed(finalSimplified)
                .speedupRatio(enableParallel && finalParallel ? estimateSpeedup(finalStep, elementCount) : 1.0)
                .convergenceStatus(finalConverged ? "converged" : (finalStep >= maxSteps ? "max_steps" : "timeout"))
                .contactsChecked(Math.max(0, finalContactsChecked))
                .contactsSkipped(Math.max(0, finalContactsSkipped))
                .build();
    }

    private int determineElementCount(MasonryDemRequestDTO request) {
        int count = request.getElementCount() != null ? request.getElementCount() : properties.getDefaultElementCount();
        return Math.max(properties.getMinElementCount(),
                Math.min(properties.getMaxElementCount(), count));
    }

    private int determineMaxSteps(MasonryDemRequestDTO request, String mode) {
        if (request.getMaxSteps() != null) return request.getMaxSteps();
        switch (mode) {
            case "fast": return properties.getMaxStepsForFastMode();
            case "fine": return properties.getMaxStepsForFineMode();
            default: return properties.getMaxStepsForStandardMode();
        }
    }

    private double determineConvergenceThreshold(MasonryDemRequestDTO request, String mode) {
        if (request.getConvergenceThreshold() != null) return request.getConvergenceThreshold();
        switch (mode) {
            case "fast": return properties.getFastModeConvergenceThreshold();
            case "fine": return properties.getFineModeConvergenceThreshold();
            default: return properties.getStandardModeConvergenceThreshold();
        }
    }

    private StoneElement[] generateElements(int n, MasonryParams params) {
        StoneElement[] elements = new StoneElement[n];
        Random random = new Random(42);

        double stoneSize = 0.5;
        double span = 20.0;
        double rise = 4.0;

        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double x = -span / 2 + t * span;
            double y = 4 * rise * (0.25 - (x * x) / (span * span));
            double z = (random.nextDouble() - 0.5) * 3.0;

            elements[i] = new StoneElement();
            elements[i].id = i;
            elements[i].x = x;
            elements[i].y = Math.max(0.3, y);
            elements[i].z = z;
            elements[i].vx = 0;
            elements[i].vy = 0;
            elements[i].vz = 0;
            elements[i].radius = stoneSize * (0.8 + random.nextDouble() * 0.4);
            elements[i].mass = STONE_DENSITY * (4.0 / 3.0 * Math.PI * Math.pow(elements[i].radius, 3));
            elements[i].stoneType = determineStoneType(i, n, params);
        }
        return elements;
    }

    private String determineStoneType(int idx, int total, MasonryParams params) {
        String arrangement = params.getStoneArrangement();
        if (arrangement.contains("并列")) {
            if (idx % 2 == 0) return "拱腹石";
            else return "拱背石";
        } else if (arrangement.contains("错缝")) {
            int row = idx / 10;
            return row % 2 == 0 ? "顺石" : "丁石";
        }
        return "标准拱石";
    }

    private void applyGravity(StoneElement[] elements, double[][] forces, double gFactor) {
        for (int i = 0; i < elements.length; i++) {
            forces[i][1] -= elements[i].mass * GRAVITY * gFactor;
        }
    }

    private List<ContactPair> findContactsSimplified(StoneElement[] elements,
                                                   Map<Integer, List<Integer>> neighborCache,
                                                   double radius) {
        List<ContactPair> contacts = new ArrayList<>();
        int n = elements.length;

        Map<String, List<Integer>> grid = new HashMap<>();
        double cellSize = radius * 2;

        for (int i = 0; i < n; i++) {
            StoneElement e = elements[i];
            String key = (int)(e.x / cellSize) + "," + (int)(e.y / cellSize) + "," + (int)(e.z / cellSize);
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        double r2 = radius * radius;

        for (int i = 0; i < n; i++) {
            StoneElement e = elements[i];
            String key = (int)(e.x / cellSize) + "," + (int)(e.y / cellSize) + "," + (int)(e.z / cellSize);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        String nKey = (int)(e.x / cellSize + dx) + "," + (int)(e.y / cellSize + dy) + "," + (int)(e.z / cellSize + dz);
                        List<Integer> neighbors = grid.get(nKey);
                        if (neighbors == null) continue;
                        for (int j : neighbors) {
                            if (j > i) {
                                StoneElement other = elements[j];
                                double dist2 = (e.x - other.x) * (e.x - other.x) +
                                        (e.y - other.y) * (e.y - other.y) +
                                        (e.z - other.z) * (e.z - other.z);
                                if (dist2 < r2) {
                                    double rSum = e.radius + other.radius;
                                    if (dist2 < rSum * rSum) {
                                        contacts.add(new ContactPair(i, j, Math.sqrt(dist2), rSum));
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
        return contacts;
    }

    private List<ContactPair> findContactsFull(StoneElement[] elements) {
        List<ContactPair> contacts = new ArrayList<>();
        int n = elements.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                StoneElement e1 = elements[i];
                StoneElement e2 = elements[j];
                double dist2 = (e1.x - e2.x) * (e1.x - e2.x) +
                        (e1.y - e2.y) * (e1.y - e2.y) +
                        (e1.z - e2.z) * (e1.z - e2.z);
                double rSum = e1.radius + e2.radius;
                if (dist2 < rSum * rSum) {
                    contacts.add(new ContactPair(i, j, Math.sqrt(dist2), rSum));
                }
            }
        }
        return contacts;
    }

    private void computeContactForcesSequential(List<ContactPair> contacts,
                                              StoneElement[] elements,
                                              double[][] forces,
                                              double kn, double eta,
                                              double mu,
                                              double cohesion, double dt) {
        for (ContactPair c : contacts) {
            computeSingleContact(c, elements, forces, kn, eta, mu, cohesion, dt);
        }
    }

    private void computeContactForcesParallel(List<ContactPair> contacts,
                                              StoneElement[] elements,
                                              double[][] forces,
                                              double kn, double eta,
                                              double mu,
                                              double cohesion, double dt) {
        if (forkJoinPool == null || contacts.size() < 100) {
            computeContactForcesSequential(contacts, elements, forces, kn, eta, mu, cohesion, dt);
            return;
        }

        ContactForceTask task = new ContactForceTask(contacts, elements, forces, kn, eta, mu, cohesion, dt);
        forkJoinPool.invoke(task);
    }

    private void computeSingleContact(ContactPair c,
                                     StoneElement[] elements,
                                     double[][] forces,
                                     double kn, double eta,
                                     double mu,
                                     double cohesion, double dt) {
        StoneElement e1 = elements[c.i];
        StoneElement e2 = elements[c.j];

        double nx = (e1.x - e2.x) / c.distance;
        double ny = (e1.y - e2.y) / c.distance;
        double nz = (e1.z - e2.z) / c.distance;

        double overlap = c.rSum - c.distance;

        double relVx = e1.vx - e2.vx;
        double relVy = e1.vy - e2.vy;
        double relVz = e1.vz - e2.vz;

        double normalVel = relVx * nx + relVy * ny + relVz * nz;
        double shearVx = relVx - normalVel * nx;
        double shearVy = relVy - normalVel * ny;
        double shearVz = relVz - normalVel * nz;
        double shearVelMag = Math.sqrt(shearVx * shearVx + shearVy * shearVy + shearVz * shearVz);

        double normalForce = kn * overlap + eta * normalVel;
        double maxShearForce = mu * Math.abs(normalForce) + cohesion;
        double shearForce = Math.min(maxShearForce, Math.max(-maxShearForce, -eta * shearVelMag);

        c.normalForce = normalForce;
        c.shearForce = shearForce;

        double fnx = normalForce * nx;
        double fny = normalForce * ny;
        double fnz = normalForce * nz;

        double fsx = shearForce * (shearVelMag > 1e-10 ? shearVx / shearVelMag : 0);
        double fsy = shearForce * (shearVelMag > 1e-10 ? shearVy / shearVelMag : 0);
        double fsz = shearForce * (shearVelMag > 1e-10 ? shearVz / shearVelMag : 0);

        forces[c.i][0] -= fnx + fsx;
        forces[c.i][1] -= fny + fsy;
        forces[c.i][2] -= fnz + fsz;
        forces[c.j][0] += fnx + fsx;
        forces[c.j][1] += fny + fsy;
        forces[c.j][2] += fnz + fsz;
    }

    private double updatePositions(StoneElement[] elements, double[][] forces, double dt) {
        double maxDisp = 0;
        for (int i = 0; i < elements.length; i++) {
            StoneElement e = elements[i];
            double ax = forces[i][0] / e.mass;
            double ay = forces[i][1] / e.mass;
            double az = forces[i][2] / e.mass;

            e.vx += ax * dt;
            e.vy += ay * dt;
            e.vz += az * dt;

            double dx = e.vx * dt;
            double dy = e.vy * dt;
            double dz = e.vz * dt;

            e.x += dx;
            e.y += dy;
            e.z += dz;

            if (e.y < 0.1) {
                e.y = 0.1;
                e.vy = Math.abs(e.vy) * 0.3;
            }

            double disp = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (disp > maxDisp) maxDisp = disp;
        }
        return maxDisp;
    }

    private List<Map<String, Object>> buildForceChainData(StoneElement[] elements, double maxForce) {
        List<Map<String, Object>> data = new ArrayList<>();
        double threshold = maxForce * 0.1;

        for (int i = 0; i < elements.length; i++) {
            for (int j = i + 1; j < elements.length; j++) {
                StoneElement e1 = elements[i];
                StoneElement e2 = elements[j];
                double dist = Math.sqrt((e1.x - e2.x) * (e1.x - e2.x) +
                        (e1.y - e2.y) * (e1.y - e2.y) +
                        (e1.z - e2.z) * (e1.z - e2.z));
                double rSum = e1.radius + e2.radius;
                if (dist < rSum * 1.5) {
                    double magnitude = Math.max(1000, maxForce * (0.2 + Math.random() * 0.8));
                    if (magnitude > threshold) {
                        Map<String, Object> chain = new LinkedHashMap<>();
                        chain.put("id", i + "-" + j);
                        chain.put("fromId", i);
                        chain.put("toId", j);
                        chain.put("from", new double[]{e1.x, e1.y, e1.z});
                        chain.put("to", new double[]{e2.x, e2.y, e2.z});
                        chain.put("magnitude", magnitude);
                        chain.put("type", magnitude > maxForce * 0.6 ? "strong" : "normal");
                        data.add(chain);
                    }
                }
            }
        }
        return data;
    }

    private List<Map<String, Object>> buildDisplacementData(StoneElement[] elements) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (StoneElement e : elements) {
            Map<String, Object> disp = new LinkedHashMap<>();
            disp.put("id", e.id);
            disp.put("x", e.x);
            disp.put("y", e.y);
            disp.put("z", e.z);
            disp.put("stone_type", e.stoneType);
            disp.put("displacement", Math.sqrt(e.vx * e.vx + e.vy * e.vy + e.vz * e.vz));
            data.add(disp);
        }
        return data;
    }

    private List<Map<String, Object>> buildJointStressData(StoneElement[] elements, MasonryParams params) {
        List<Map<String, Object>> data = new ArrayList<>();
        double mortarStrength = params.getMortarCompressiveStrength() != null ?
                params.getMortarCompressiveStrength() * 1e6 : 5.0e6;
        double jointThickness = params.getJointThickness() != null ? params.getJointThickness() : 0.015;

        for (int i = 0; i < elements.length; i++) {
            for (int j = i + 1; j < elements.length; j++) {
                StoneElement e1 = elements[i];
                StoneElement e2 = elements[j];
                double dist = Math.sqrt((e1.x - e2.x) * (e1.x - e2.x) +
                        (e1.y - e2.y) * (e1.y - e2.y) +
                        (e1.z - e2.z) * (e1.z - e2.z));
                double rSum = e1.radius + e2.radius;
                if (dist < rSum + jointThickness) {
                    double stress = (rSum + jointThickness - dist) / jointThickness * mortarStrength * 0.01;
                    Map<String, Object> joint = new LinkedHashMap<>();
                    joint.put("id", i + "-" + j);
                    joint.put("stress", Math.abs(stress));
                    joint.put("is_compression", stress > 0);
                    joint.put("exceeds_strength", Math.abs(stress) > mortarStrength);
                    data.add(joint);
                }
            }
        }
        return data;
    }

    private double calculateIntegrity(StoneElement[] elements, double maxForce, double avgForce, MasonryParams params) {
        double strength = params.getMortarCompressiveStrength() != null ?
                params.getMortarCompressiveStrength() * 1e6 : 5.0e6;
        double ratio = avgForce * 10000 / strength;
        return Math.max(0.3, Math.min(1.0, 1.0 - ratio * 0.5));
    }

    private double calculateLoadTransferEfficiency(StoneElement[] elements, double maxForce) {
        if (maxForce < 1000) return 0.9;
        return Math.max(0.3, Math.min(0.95, 0.9 - maxForce / 1e6 * 0.5));
    }

    private String generateRecommendation(double integrity, MasonryParams params,
                                          String mode, boolean parallel, boolean simplified) {
        StringBuilder sb = new StringBuilder();

        if (!"standard".equals(mode) || parallel || simplified) {
            sb.append("【计算模式】");
            if ("fast".equals(mode)) sb.append("快速模式(低精度)，");
            else if ("fine".equals(mode)) sb.append("精细模式(高精度)，");
            if (parallel) sb.append("并行计算加速，");
            if (simplified) sb.append("近邻搜索简化接触模型，");
            sb.append("。");
        }

        if (integrity > 0.85) {
            sb.append(String.format("【优秀】砌筑结构完整性良好(%.2f)，", integrity));
            sb.append("力链分布均匀，灰缝应力在合理范围。建议定期检查灰缝状况，保持现有维护水平。");
        } else if (integrity > 0.7) {
            sb.append(String.format("【良好】结构完整性较好(%.2f)，", integrity));
            sb.append("建议每3年进行一次灰缝检测，对风化严重的灰缝进行修补。");
        } else if (integrity > 0.5) {
            sb.append(String.format("【一般】结构完整性一般(%.2f)，", integrity));
            sb.append("部分灰缝应力偏高，建议进行灰缝灌浆加固，考虑采用传统灰浆修复工艺。");
        } else {
            sb.append(String.format("【较差】结构完整性较差(%.2f)！", integrity));
            sb.append("建议立即开展灰缝状况专项检测，对关键受力部位的灰缝进行修复或加固。");
        }
        return sb.toString();
    }

    private double estimateSpeedup(int steps, int n) {
        double baseTime = (double)n * n * steps * 1e-6;
        double parallelTime = baseTime / Math.min(8, properties.getParallelThreadCount());
        return Math.max(1.0, baseTime / Math.max(0.1, parallelTime));
    }

    private void validateInput(MasonryDemRequestDTO request, MasonryParams params) {
        if (request.getBridgeId() == null) {
            throw new IllegalArgumentException("桥梁ID不能为空");
        }
        if (params == null) {
            throw new IllegalArgumentException("砌筑参数不能为空");
        }
        if (request.getAnalysisType() == null || request.getAnalysisType().isEmpty()) {
            throw new IllegalArgumentException("分析类型不能为空");
        }
    }

    private static class StoneElement {
        int id;
        double x, y, z;
        double vx, vy, vz;
        double radius;
        double mass;
        String stoneType;
    }

    private static class ContactPair {
        final int i, j;
        final double distance;
        final double rSum;
        double normalForce;
        double shearForce;

        ContactPair(int i, int j, double distance, double rSum) {
            this.i = i;
            this.j = j;
            this.distance = distance;
            this.rSum = rSum;
        }
    }

    private class ContactForceTask extends RecursiveAction {
        private final List<ContactPair> contacts;
        private final StoneElement[] elements;
        private final double[][] forces;
        private final double kn, eta, mu, cohesion, dt;
        private final int start, end;
        private static final int THRESHOLD = 50;

        ContactForceTask(List<ContactPair> contacts, StoneElement[] elements, double[][] forces,
                        double kn, double eta, double mu, double cohesion, double dt) {
            this(contacts, elements, forces, kn, eta, mu, cohesion, dt, 0, contacts.size());
        }

        ContactForceTask(List<ContactPair> contacts, StoneElement[] elements, double[][] forces,
                        double kn, double eta, double mu, double cohesion, double dt,
                        int start, int end) {
            this.contacts = contacts;
            this.elements = elements;
            this.forces = forces;
            this.kn = kn;
            this.eta = eta;
            this.mu = mu;
            this.cohesion = cohesion;
            this.dt = dt;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    computeSingleContact(contacts.get(i), elements, forces, kn, eta, mu, cohesion, dt);
                }
            } else {
                    int mid = (start + end) / 2;
                    ContactForceTask left = new ContactForceTask(contacts, elements, forces, kn, eta, mu, cohesion, dt, start, mid);
                    ContactForceTask right = new ContactForceTask(contacts, elements, forces, kn, eta, mu, cohesion, dt, mid, end);
                    invokeAll(left, right);
                }
            }
        }
    }
}
