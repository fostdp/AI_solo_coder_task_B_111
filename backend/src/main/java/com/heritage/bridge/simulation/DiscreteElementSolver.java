package com.heritage.bridge.simulation;

import com.heritage.bridge.dto.MasonryDemRequestDTO;
import com.heritage.bridge.dto.MasonryDemResultDTO;
import com.heritage.bridge.config.MasonryDemProperties;
import com.heritage.bridge.entity.MasonryParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DiscreteElementSolver {

    private final MasonryDemProperties properties;
    private static final double GRAVITY = 9.81;

    public DiscreteElementSolver(MasonryDemProperties properties) {
        this.properties = properties;
    }

    public MasonryDemResultDTO solve(MasonryDemRequestDTO request, MasonryParams params, String bridgeName) {
        validateInput(request);

        int nElements = request.getElementCount() != null ?
                Math.max(properties.getMinElementCount(),
                        Math.min(properties.getMaxElementCount(), request.getElementCount())) :
                properties.getDefaultElementCount();

        double dt = request.getTimeStep() != null ? request.getTimeStep() : properties.getDefaultTimeStep();
        double gravityFactor = request.getGravityFactor() != null ?
                request.getGravityFactor() : properties.getDefaultGravityFactor();
        double kn = request.getContactStiffness() != null ?
                request.getContactStiffness() : properties.getDefaultContactStiffness();
        double eta = request.getDampingCoefficient() != null ?
                request.getDampingCoefficient() : properties.getDefaultDampingCoefficient();
        double mu = request.getFrictionCoefficient() != null ?
                Math.max(properties.getMinFrictionCoefficient(),
                        Math.min(properties.getMaxFrictionCoefficient(), request.getFrictionCoefficient())) :
                params.getStoneFrictionCoefficient();
        double cohesion = request.getCohesion() != null ? request.getCohesion() :
                (params.getCohesion() != null ? params.getCohesion() : 0.5);

        double fcMortar = request.getMortarCompressiveStrength() != null ?
                request.getMortarCompressiveStrength() : params.getMortarCompressiveStrength();
        double ftMortar = request.getMortarTensileStrength() != null ?
                request.getMortarTensileStrength() : params.getMortarTensileStrength();
        double jointThickness = request.getJointThickness() != null ?
                request.getJointThickness() : params.getJointThickness();

        String analysisType = request.getAnalysisType() != null ? request.getAnalysisType() : "static";

        List<DEMElement> elements = generateElements(nElements, analysisType);
        List<DEMContact> contacts = generateContacts(elements, kn, eta, mu, cohesion, ftMortar, jointThickness);

        int nSteps = determineTimeSteps(analysisType);
        double maxContactForce = 0;
        double totalContactForce = 0;
        int activeContacts = 0;

        RandomDataGenerator random = new RandomDataGenerator();
        random.reSeed(42);

        for (int step = 0; step < nSteps; step++) {
            double t = step * dt;
            applyLoad(elements, analysisType, t, gravityFactor, random);
            calculateContactForces(elements, contacts);
            updatePositions(elements, dt);
            applyBoundaryConditions(elements);

            if (step > nSteps * 0.5) {
                for (DEMContact contact : contacts) {
                    if (contact.isActive()) {
                        activeContacts++;
                        double force = contact.getNormalForce();
                        maxContactForce = Math.max(maxContactForce, force);
                        totalContactForce += force;
                    }
                }
            }
        }

        double avgContactForce = activeContacts > 0 ? totalContactForce / activeContacts : 0;

        List<Map<String, Object>> forceChainData = buildForceChainData(elements, contacts);
        List<Map<String, Object>> stoneDisplacements = buildDisplacementData(elements);
        List<Map<String, Object>> jointStresses = buildJointStressData(contacts, fcMortar, ftMortar);

        double structuralIntegrity = calculateIntegrityIndex(contacts, ftMortar);
        double loadTransferEfficiency = calculateLoadTransferEfficiency(forceChainData);
        String recommendation = generateRecommendation(analysisType, structuralIntegrity,
                loadTransferEfficiency, params);

        return MasonryDemResultDTO.builder()
                .bridgeId(request.getBridgeId())
                .analysisType(analysisType)
                .elementCount(nElements)
                .contactCount(contacts.size())
                .maxContactForce(Math.round(maxContactForce * 100.0) / 100.0)
                .avgContactForce(Math.round(avgContactForce * 100.0) / 100.0)
                .forceChainData(forceChainData)
                .stoneDisplacements(stoneDisplacements)
                .jointStresses(jointStresses)
                .structuralIntegrityIndex(Math.round(structuralIntegrity * 1000.0) / 1000.0)
                .loadTransferEfficiency(Math.round(loadTransferEfficiency * 1000.0) / 1000.0)
                .mortarType(params.getMortarType())
                .stoneArrangement(params.getStoneArrangement())
                .recommendation(recommendation)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private void validateInput(MasonryDemRequestDTO request) {
        if (request.getBridgeId() == null) {
            throw new IllegalArgumentException("桥梁ID不能为空");
        }
    }

    private int determineTimeSteps(String analysisType) {
        switch (analysisType) {
            case "seismic": return 2000;
            case "traffic": return 1500;
            case "gravity": return 500;
            default: return 1000;
        }
    }

    private List<DEMElement> generateElements(int nElements, String analysisType) {
        List<DEMElement> elements = new ArrayList<>();
        RandomDataGenerator random = new RandomDataGenerator();
        random.reSeed(42);

        int nRows = (int) Math.ceil(Math.sqrt(nElements * 0.4));
        int nCols = nElements / nRows;

        int id = 0;
        for (int row = 0; row < nRows; row++) {
            for (int col = 0; col < nCols && id < nElements; col++) {
                double x = col * 0.6 + random.nextUniform(-0.02, 0.02);
                double y = row * 0.3 + random.nextUniform(-0.01, 0.01);

                double size = 0.5 + random.nextUniform(-0.05, 0.05);
                double density = 2500.0;
                double mass = density * size * size * 0.3;
                double inertia = mass * (size * size + size * size) / 12.0;

                DEMElement element = new DEMElement();
                element.id = id;
                element.position = new Vector2D(x, y);
                element.velocity = new Vector2D(0, 0);
                element.acceleration = new Vector2D(0, 0);
                element.size = size;
                element.mass = mass;
                element.inertia = inertia;
                element.rotation = 0;
                element.angularVelocity = 0;
                element.isBoundary = (row == 0);
                element.stoneType = determineStoneType(row, col, analysisType);

                elements.add(element);
                id++;
            }
        }
        return elements;
    }

    private String determineStoneType(int row, int col, String analysisType) {
        if (row == 0) return "voussoir_arch";
        if (col % 5 == 0) return "key_stone";
        return "regular_stone";
    }

    private List<DEMContact> generateContacts(List<DEMElement> elements, double kn, double eta,
                                               double mu, double cohesion, double ft, double jointThickness) {
        List<DEMContact> contacts = new ArrayList<>();
        int contactId = 0;

        for (int i = 0; i < elements.size(); i++) {
            for (int j = i + 1; j < elements.size(); j++) {
                DEMElement ei = elements.get(i);
                DEMElement ej = elements.get(j);

                double dist = ei.position.distance(ej.position);
                double nominalGap = jointThickness;
                double contactDist = (ei.size + ej.size) / 2 + nominalGap;

                if (dist < contactDist * 1.5) {
                    DEMContact contact = new DEMContact();
                    contact.id = contactId++;
                    contact.element1 = ei.id;
                    contact.element2 = ej.id;
                    contact.normalStiffness = kn;
                    contact.dampingCoefficient = eta;
                    contact.frictionCoefficient = mu;
                    contact.cohesion = cohesion;
                    contact.tensileStrength = ft;
                    contact.compressiveStrength = 40.0e6;
                    contact.gap0 = dist - (ei.size + ej.size) / 2;
                    contact.isJoint = true;

                    contacts.add(contact);
                }
            }
        }
        return contacts;
    }

    private void applyLoad(List<DEMElement> elements, String analysisType,
                           double t, double gravityFactor, RandomDataGenerator random) {
        double g = GRAVITY * gravityFactor;

        for (DEMElement element : elements) {
            if (element.isBoundary) continue;

            Vector2D gravity = new Vector2D(0, -element.mass * g);
            element.externalForce = gravity;

            switch (analysisType) {
                case "seismic":
                    double seismicAccel = 0.15 * g * Math.sin(2 * Math.PI * 2 * t)
                            + 0.1 * g * Math.sin(2 * Math.PI * 5 * t);
                    element.externalForce = element.externalForce.add(
                            new Vector2D(element.mass * seismicAccel * 0.3, 0));
                    break;
                case "traffic":
                    double trafficLoad = 5000 * Math.sin(Math.PI * t / 0.5) * Math.exp(-t / 5.0);
                    if (t > 1.0 && t < 3.0 && element.position.getX() > 2.0 && element.position.getX() < 4.0) {
                        element.externalForce = element.externalForce.add(new Vector2D(0, -trafficLoad));
                    }
                    break;
            }
        }
    }

    private void calculateContactForces(List<DEMElement> elements, List<DEMContact> contacts) {
        Map<Integer, DEMElement> elementMap = elements.stream()
                .collect(Collectors.toMap(e -> e.id, e -> e));

        for (DEMContact contact : contacts) {
            DEMElement e1 = elementMap.get(contact.element1);
            DEMElement e2 = elementMap.get(contact.element2);

            Vector2D n = e2.position.subtract(e1.position).normalize();
            double dist = e1.position.distance(e2.position);
            double overlap = contact.gap0 + (e1.size + e2.size) / 2 - dist;

            if (overlap > 0 || !contact.isJoint) {
                contact.active = true;
                Vector2D relVel = e2.velocity.subtract(e1.velocity);
                double normalVel = relVel.dotProduct(n);

                double normalForce = contact.normalStiffness * Math.max(0, overlap)
                        + contact.dampingCoefficient * Math.max(0, normalVel);

                if (normalForce > contact.tensileStrength * contact.gap0 * 1.0) {
                    normalForce = 0;
                    contact.active = false;
                    contact.broken = true;
                }

                contact.normalForce = normalForce;

                Vector2D shearDir = new Vector2D(-n.getY(), n.getX());
                double shearVel = relVel.dotProduct(shearDir);
                double maxShearForce = contact.frictionCoefficient * normalForce + contact.cohesion;
                double shearForce = Math.min(Math.max(-maxShearForce, -contact.dampingCoefficient * shearVel), maxShearForce);
                contact.shearForce = shearForce;

                Vector2D totalForce = n.scalarMultiply(normalForce).add(shearDir.scalarMultiply(shearForce));
                e1.externalForce = e1.externalForce.add(totalForce);
                e2.externalForce = e2.externalForce.subtract(totalForce);
            } else {
                contact.active = false;
                contact.normalForce = 0;
                contact.shearForce = 0;
            }
        }
    }

    private void updatePositions(List<DEMElement> elements, double dt) {
        for (DEMElement element : elements) {
            if (element.isBoundary) continue;

            Vector2D accel = element.externalForce.scalarMultiply(1.0 / element.mass);
            element.velocity = element.velocity.add(accel.scalarMultiply(dt));
            element.position = element.position.add(element.velocity.scalarMultiply(dt));

            double maxVel = 10.0;
            if (element.velocity.getNorm() > maxVel) {
                element.velocity = element.velocity.normalize().scalarMultiply(maxVel);
            }
        }
    }

    private void applyBoundaryConditions(List<DEMElement> elements) {
        for (DEMElement element : elements) {
            if (element.position.getY() < -0.5) {
                element.position = new Vector2D(element.position.getX(), -0.5);
                element.velocity = new Vector2D(element.velocity.getX() * 0.5, -element.velocity.getY() * 0.3);
            }
            if (element.position.getX() < -1.0 || element.position.getX() > 30.0) {
                element.velocity = new Vector2D(-element.velocity.getX() * 0.5, element.velocity.getY());
            }
        }
    }

    private List<Map<String, Object>> buildForceChainData(List<DEMElement> elements, List<DEMContact> contacts) {
        List<Map<String, Object>> data = new ArrayList<>();
        Map<Integer, DEMElement> elementMap = elements.stream()
                .collect(Collectors.toMap(e -> e.id, e -> e));

        double maxForce = contacts.stream()
                .mapToDouble(c -> c.normalForce)
                .max().orElse(1.0);

        for (DEMContact contact : contacts) {
            if (!contact.active) continue;

            DEMElement e1 = elementMap.get(contact.element1);
            DEMElement e2 = elementMap.get(contact.element2);

            Map<String, Object> chain = new LinkedHashMap<>();
            chain.put("id", contact.id);
            chain.put("from", Arrays.asList(e1.position.getX(), e1.position.getY()));
            chain.put("to", Arrays.asList(e2.position.getX(), e2.position.getY()));
            chain.put("normal_force", contact.normalForce);
            chain.put("shear_force", contact.shearForce);
            chain.put("magnitude", Math.sqrt(contact.normalForce * contact.normalForce
                    + contact.shearForce * contact.shearForce));
            chain.put("thickness", 0.02 + 0.08 * (contact.normalForce / maxForce));
            chain.put("broken", contact.broken);

            data.add(chain);
        }
        return data;
    }

    private List<Map<String, Object>> buildDisplacementData(List<DEMElement> elements) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (DEMElement element : elements) {
            Map<String, Object> disp = new LinkedHashMap<>();
            disp.put("id", element.id);
            disp.put("x", element.position.getX());
            disp.put("y", element.position.getY());
            disp.put("dx", element.velocity.getX() * 0.01);
            disp.put("dy", element.velocity.getY() * 0.01);
            disp.put("stone_type", element.stoneType);
            disp.put("size", element.size);
            data.add(disp);
        }
        return data;
    }

    private List<Map<String, Object>> buildJointStressData(List<DEMContact> contacts,
                                                           double fc, double ft) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (DEMContact contact : contacts) {
            if (!contact.isJoint) continue;

            double stress = contact.normalForce / (contact.gap0 * 1.0);
            Map<String, Object> js = new LinkedHashMap<>();
            js.put("id", contact.id);
            js.put("elements", Arrays.asList(contact.element1, contact.element2));
            js.put("stress", stress);
            js.put("stress_ratio", stress > 0 ? stress / fc : stress / ft);
            js.put("is_compression", stress > 0);
            data.add(js);
        }
        return data;
    }

    private double calculateIntegrityIndex(List<DEMContact> contacts, double ftMortar) {
        long totalJoints = contacts.stream().filter(c -> c.isJoint).count();
        long brokenJoints = contacts.stream().filter(c -> c.broken).count();
        double strengthRatio = contacts.stream()
                .filter(c -> c.active)
                .mapToDouble(c -> Math.max(0, 1 - c.normalForce / (c.tensileStrength * 1000)))
                .average().orElse(1.0);

        return (1.0 - (double) brokenJoints / totalJoints) * 0.5 + strengthRatio * 0.5;
    }

    private double calculateLoadTransferEfficiency(List<Map<String, Object>> forceChains) {
        if (forceChains.isEmpty()) return 0.5;

        double totalForce = forceChains.stream()
                .mapToDouble(c -> (Double) c.get("magnitude"))
                .sum();

        double diagonalPreference = forceChains.stream()
                .filter(c -> {
                    List<Double> from = (List<Double>) c.get("from");
                    List<Double> to = (List<Double>) c.get("to");
                    double dy = to.get(1) - from.get(1);
                    return dy < 0;
                })
                .mapToDouble(c -> (Double) c.get("magnitude"))
                .sum();

        return diagonalPreference / totalForce;
    }

    private String generateRecommendation(String analysisType, double integrity,
                                           double loadEfficiency, MasonryParams params) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("【%s分析】", getAnalysisTypeName(analysisType)));

        if (integrity >= 0.9) {
            sb.append(String.format("结构完整性优异(%.2f%%)，荷载传递效率%.1f%%。",
                    integrity * 100, loadEfficiency * 100));
            sb.append(String.format("采用%s砌筑，%s的力学性能表现良好。",
                    params.getStoneArrangement(), params.getMortarType()));
            sb.append("建议维持常规监测，每年进行一次结构检查。");
        } else if (integrity >= 0.75) {
            sb.append(String.format("结构完整性良好(%.2f%%)，荷载传递效率%.1f%%。",
                    integrity * 100, loadEfficiency * 100));
            sb.append("建议重点监测灰缝开裂情况，每季度进行一次应力检测。");
        } else if (integrity >= 0.6) {
            sb.append(String.format("结构完整性一般(%.2f%%)，荷载传递效率%.1f%%。",
                    integrity * 100, loadEfficiency * 100));
            sb.append("建议对灰缝进行加固处理，考虑采用环氧树脂注入工艺修复开裂部位。");
        } else {
            sb.append(String.format("【警告】结构完整性不足(%.2f%%)，荷载传递效率%.1f%%！",
                    integrity * 100, loadEfficiency * 100));
            sb.append("【紧急】建议立即限制荷载，组织专项评估，考虑对砌筑结构进行系统性加固。");
        }

        return sb.toString();
    }

    private String getAnalysisTypeName(String type) {
        switch (type) {
            case "gravity": return "重力场";
            case "seismic": return "地震作用";
            case "traffic": return "交通荷载";
            default: return "静力";
        }
    }

    private static class DEMElement {
        int id;
        Vector2D position;
        Vector2D velocity;
        Vector2D acceleration;
        Vector2D externalForce;
        double size;
        double mass;
        double inertia;
        double rotation;
        double angularVelocity;
        boolean isBoundary;
        String stoneType;
    }

    private static class DEMContact {
        int id;
        int element1;
        int element2;
        double normalStiffness;
        double dampingCoefficient;
        double frictionCoefficient;
        double cohesion;
        double tensileStrength;
        double compressiveStrength;
        double gap0;
        double normalForce;
        double shearForce;
        boolean active;
        boolean broken;
        boolean isJoint;
    }
}
