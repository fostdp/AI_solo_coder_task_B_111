package com.heritage.bridge.event;

import com.heritage.bridge.entity.Alert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertFiredEvent implements Serializable {

    private Long alertId;
    private Long bridgeId;
    private Long sensorId;
    private Alert alert;
    private String alertType;
    private String level;
    private String message;
    private Double value;
    private Double threshold;
    private LocalDateTime timestamp;
    private Source source;
    private DeliveryStatus deliveryStatus;

    public enum Source {
        SENSOR_DATA,
        FEM_RESULT,
        DAMAGE_PREDICTION,
        DAILY_HEALTH_CHECK,
        MANUAL
    }

    public enum DeliveryStatus {
        QUEUED,
        PUBLISHED,
        FAILED_RETRYING,
        FAILED_PERMANENT
    }
}
