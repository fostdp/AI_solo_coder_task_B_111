package com.heritage.bridge.event;

import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.entity.SensorData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataIngestedEvent implements Serializable {

    private Long bridgeId;
    private Long sensorId;
    private String sensorCode;
    private String sensorType;
    private SensorData latestData;
    private List<SensorData> batchData;
    private LocalDateTime ingestTime;
    private Trigger trigger;

    public enum Trigger {
        DTU_UPLOAD,
        BATCH_UPLOAD,
        HISTORICAL_REPLAY,
        DAILY_CHECK
    }
}
