package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "weathering.regression")
public class WeatheringProperties {

    private double defaultHardnessCoefficient = 0.15;
    private double defaultVelocityCoefficient = 8.0;
    private double defaultIntercept = 25.0;
    private double minDepth = 0.0;
    private double maxDepth = 50.0;
    private double gradeNoneMax = 2.0;
    private double gradeSlightMax = 5.0;
    private double gradeModerateMax = 10.0;
    private double gradeSevereMax = 20.0;

    private boolean scheduledEnabled = true;
    private String scheduledCron = "0 0 4 * * ?";
    private boolean dataTriggeredAutoEvaluate = true;
}
