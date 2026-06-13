package com.heritage.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alert.thresholds")
public class AlertThresholdProperties {

    private Settlement settlement = new Settlement();
    private CrackRate crackRate = new CrackRate();
    private Strain strain = new Strain();
    private TemperatureRate temperatureRate = new TemperatureRate();

    @Data
    public static class Settlement {
        private double warning = 5.0;
        private double danger = 10.0;
        private String unit = "mm";
    }

    @Data
    public static class CrackRate {
        private double warning = 0.5;
        private double danger = 1.0;
        private String unit = "mm/月";
    }

    @Data
    public static class Strain {
        private double warning = 100.0;
        private double danger = 150.0;
        private String unit = "微应变";
    }

    @Data
    public static class TemperatureRate {
        private double warning = 5.0;
        private double danger = 8.0;
        private String unit = "℃/小时";
    }

    public double getWarningValue(String type) {
        return switch (type == null ? "" : type) {
            case "settlement" -> settlement.getWarning();
            case "crack_rate", "crack" -> crackRate.getWarning();
            case "strain" -> strain.getWarning();
            case "temperature", "temperature_rate" -> temperatureRate.getWarning();
            default -> 0;
        };
    }

    public double getDangerValue(String type) {
        return switch (type == null ? "" : type) {
            case "settlement" -> settlement.getDanger();
            case "crack_rate", "crack" -> crackRate.getDanger();
            case "strain" -> strain.getDanger();
            case "temperature", "temperature_rate" -> temperatureRate.getDanger();
            default -> 0;
        };
    }

    public String getUnit(String type) {
        return switch (type == null ? "" : type) {
            case "settlement" -> settlement.getUnit();
            case "crack_rate", "crack" -> crackRate.getUnit();
            case "strain" -> strain.getUnit();
            case "temperature", "temperature_rate" -> temperatureRate.getUnit();
            default -> "";
        };
    }
}
