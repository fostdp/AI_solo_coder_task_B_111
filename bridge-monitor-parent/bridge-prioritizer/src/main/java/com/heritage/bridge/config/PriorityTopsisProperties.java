package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "priority.topsis")
public class PriorityTopsisProperties {

    private Map<String, Double> defaultWeights = new LinkedHashMap<>();
    private int priorityCriticalMax = 3;
    private int priorityHighMax = 6;
    private double urgencyImmediateMax = 1.0;
    private double urgencyUrgentMax = 0.8;
    private double urgencyNormalMax = 0.6;

    private boolean scheduledEnabled = true;
    private String scheduledCron = "0 0 6 1 * ?";
    private boolean protectionPlanEnabled = true;
    private int defaultPlanYear = 2026;

    private boolean delphiMethodEnabled = true;
    private int defaultExpertCount = 5;
    private double minExpertConsensus = 0.6;
    private double expertWeightInfluence = 0.7;
    private boolean sensitivityAnalysisEnabled = true;
    private int sensitivityPerturbationCount = 10;
    private double sensitivityPerturbationRange = 0.2;
    private double rankingStabilityThreshold = 0.8;

    public PriorityTopsisProperties() {
        defaultWeights.put("structure-safety", 0.30);
        defaultWeights.put("damage-trend", 0.25);
        defaultWeights.put("weathering", 0.15);
        defaultWeights.put("traffic-impact", 0.15);
        defaultWeights.put("historical-value", 0.15);
    }
}
