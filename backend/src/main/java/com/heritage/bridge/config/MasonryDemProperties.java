package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "masonry.dem")
public class MasonryDemProperties {

    private int defaultElementCount = 200;
    private int minElementCount = 50;
    private int maxElementCount = 1000;
    private double defaultTimeStep = 1.0e-5;
    private double defaultGravityFactor = 1.0;
    private double defaultContactStiffness = 1.0e9;
    private double defaultDampingCoefficient = 0.3;
    private double minFrictionCoefficient = 0.1;
    private double maxFrictionCoefficient = 0.9;
    private String analysisTypes = "static,gravity,seismic,traffic";

    private boolean scheduledEnabled = true;
    private String scheduledCron = "0 0 5 * * ?";

    private boolean parallelComputingEnabled = true;
    private int parallelThreadCount = 4;
    private boolean simplifiedContactModelEnabled = true;
    private double neighborSearchRadius = 0.15;
    private double verletListSkin = 0.02;
    private int maxStepsForFastMode = 200;
    private int maxStepsForStandardMode = 500;
    private int maxStepsForFineMode = 1000;
    private double fastModeConvergenceThreshold = 0.01;
    private double standardModeConvergenceThreshold = 0.001;
    private double fineModeConvergenceThreshold = 0.0001;
    private long maxSimulationTimeMs = 300000;
}
