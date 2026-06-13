package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.SensorDataUploadDTO;
import com.heritage.bridge.dto.TrendDataDTO;
import com.heritage.bridge.dtu.DtuReceiverService;
import com.heritage.bridge.entity.SensorData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class DataController {

    private final DtuReceiverService dtuReceiverService;

    @PostMapping("/upload")
    public ApiResponse<SensorData> uploadSingle(@Valid @RequestBody SensorDataUploadDTO dto) {
        SensorData saved = dtuReceiverService.ingest(dto);
        return ApiResponse.success(saved);
    }

    @PostMapping("/batch")
    public ApiResponse<Integer> uploadBatch(@Valid @RequestBody List<SensorDataUploadDTO> dtos) {
        List<SensorData> saved = dtuReceiverService.batchIngest(dtos);
        return ApiResponse.success(saved.size());
    }

    @GetMapping("/bridges/{bridgeId}/latest")
    public ApiResponse<List<SensorData>> getLatestByBridge(@PathVariable Long bridgeId) {
        return ApiResponse.success(dtuReceiverService.findLatestByBridgeId(bridgeId));
    }

    @GetMapping("/sensors/{sensorId}/trend")
    public ApiResponse<List<TrendDataDTO>> getSensorTrend(
            @PathVariable Long sensorId,
            @RequestParam(defaultValue = "30") int days) {
        List<TrendDataDTO> data = dtuReceiverService.getSensorTrend(sensorId, days);
        return ApiResponse.success(data);
    }
}
