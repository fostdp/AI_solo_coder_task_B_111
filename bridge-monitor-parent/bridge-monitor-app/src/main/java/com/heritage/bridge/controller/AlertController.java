package com.heritage.bridge.controller;

import com.heritage.bridge.alarm.AlarmPublisherService;
import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.entity.Alert;
import com.heritage.bridge.entity.AlertThreshold;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlarmPublisherService alarmService;

    @GetMapping
    public ApiResponse<List<Alert>> listAll() {
        return ApiResponse.success(alarmService.findAll());
    }

    @GetMapping("/unacknowledged")
    public ApiResponse<List<Alert>> listUnacknowledged() {
        return ApiResponse.success(alarmService.findUnacknowledged());
    }

    @GetMapping("/count/unacknowledged")
    public ApiResponse<Long> countUnacknowledged() {
        return ApiResponse.success(alarmService.countUnacknowledged());
    }

    @GetMapping("/count/unacknowledged/{bridgeId}")
    public ApiResponse<Long> countUnacknowledgedByBridge(@PathVariable Long bridgeId) {
        return ApiResponse.success(alarmService.countUnacknowledged(bridgeId));
    }

    @GetMapping("/bridge/{bridgeId}")
    public ApiResponse<List<Alert>> listByBridge(@PathVariable Long bridgeId) {
        return ApiResponse.success(alarmService.findByBridgeId(bridgeId));
    }

    @GetMapping("/level/{level}")
    public ApiResponse<List<Alert>> listByLevel(@PathVariable String level) {
        return ApiResponse.success(alarmService.findByLevel(level));
    }

    @PostMapping("/{id}/acknowledge")
    public ApiResponse<Alert> acknowledge(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String user = body != null ? body.get("user") : "system";
        Alert ack = alarmService.acknowledge(id, user);
        return ack != null ? ApiResponse.success(ack) : ApiResponse.error(404, "告警不存在");
    }

    @GetMapping("/thresholds")
    public ApiResponse<List<AlertThreshold>> listThresholds() {
        return ApiResponse.success(alarmService.listThresholds());
    }

    @PutMapping("/thresholds/{id}")
    public ApiResponse<AlertThreshold> updateThreshold(
            @PathVariable Long id,
            @RequestBody AlertThreshold threshold) {
        return ApiResponse.success(alarmService.updateThreshold(id, threshold));
    }
}
