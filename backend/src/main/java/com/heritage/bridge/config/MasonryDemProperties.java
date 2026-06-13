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
}
