package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "damage.paris")
public class ParisFormulaProperties {

    private double defaultC = 1.0e-12;
    private double defaultM = 3.0;
    private double minC = 1.0e-20;
    private double maxC = 1.0e-8;
    private double minM = 1.0;
    private double maxM = 10.0;

    private int defaultYearsToPredict = 5;
    private int defaultAnnualCycles = 365;
    private double defaultStressAmplitude = 5.0e6;

    private boolean enableBayesianDefault = true;
    private int defaultMcmcSamples = 10000;
    private int defaultBurnin = 2000;
    private int minMcmcSamples = 1000;
    private int maxMcmcSamples = 100000;

    private double priorCMean = 1.0e-12;
    private double priorCStd = 5.0e-13;
    private double priorMMean = 3.0;
    private double priorMStd = 0.5;

    private double maintenanceThresholdMm = 10.0;
    private double dangerThresholdMm = 20.0;

    private boolean dataTriggeredAutoPredict = true;
    private String scheduledCron = "0 0 3 * * MON";
    private boolean scheduledEnabled = true;

    private double proposalCScale = 0.1;
    private double proposalMScale = 0.1;
    private double observationNoiseRatio = 0.10;
    private long randomSeed = 42L;
}
