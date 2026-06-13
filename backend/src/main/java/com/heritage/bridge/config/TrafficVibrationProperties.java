package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "traffic.vibration")
public class TrafficVibrationProperties {

    private double defaultNaturalFrequency = 3.0;
    private double defaultDampingRatio = 0.05;
    private double defaultBridgeMass = 500000.0;
    private double defaultSpanStiffness = 1.0e9;
    private double allowableAcceleration = 0.5;
    private double allowableDisplacement = 0.005;
    private String defaultVehicleTypes = "passenger,truck_light,truck_medium,truck_heavy,bus";

    private boolean scheduledEnabled = true;
    private String scheduledCron = "0 30 2 * * ?";
    private boolean dataTriggeredAutoAnalyze = true;

    private boolean pavementDampingEnabled = true;
    private double defaultPavementThickness = 0.08;
    private double defaultPavementDampingRatio = 0.15;
    private double defaultPavementYoungsModulus = 1.2e9;
    private double defaultPavementDensity = 2400.0;
    private double maxPavementDampingFactor = 0.6;
    private double minPavementDampingFactor = 0.85;
}
