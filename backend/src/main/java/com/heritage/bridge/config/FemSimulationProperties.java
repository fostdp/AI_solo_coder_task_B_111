package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "simulation.fem")
public class FemSimulationProperties {

    private int defaultElementCount = 20;
    private int minElementCount = 10;
    private int maxElementCount = 200;

    private double defaultEmean = 20.0;
    private double defaultNu = 0.18;
    private double defaultFcMean = 40.0;
    private double defaultStoneDensity = 2500.0;
    private double defaultGravity = 9.81;
    private double defaultThermalExpansion = 8e-6;

    private double defaultTrafficLoad = 5000.0;

    private int defaultMcSamples = 1000;
    private int minMcSamples = 100;
    private int maxMcSamples = 10000;
    private double defaultModulusCov = 0.15;
    private double defaultStrengthCov = 0.20;
    private double minCov = 0.01;
    private double maxCov = 0.50;

    private boolean dataTriggeredAutoSim = false;
    private int autoSimSensorThreshold = 50;
    private boolean defaultStochastic = false;

    private String scheduledCron = "0 0 2 * * ?";
    private boolean scheduledEnabled = true;
}
