package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.entity.Sensor;
import com.heritage.bridge.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @GetMapping("/bridges/{bridgeId}/sensors")
    public ResponseEntity<ApiResponse<List<Sensor>>> listByBridge(@PathVariable Long bridgeId) {
        return ResponseEntity.ok(ApiResponse.success(sensorService.findByBridgeId(bridgeId)));
    }

    @GetMapping("/sensors/{id}")
    public ResponseEntity<ApiResponse<Sensor>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(sensorService.findById(id)));
    }
}
