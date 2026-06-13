package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.TrafficVibrationRequestDTO;
import com.heritage.bridge.dto.TrafficVibrationResultDTO;
import com.heritage.bridge.entity.TrafficFlowData;
import com.heritage.bridge.entity.TrafficVibrationAnalysis;
import com.heritage.bridge.traffic.TrafficVibrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/traffic")
@RequiredArgsConstructor
public class TrafficVibrationController {

    private final TrafficVibrationService vibrationService;

    @PostMapping("/analyze")
    public ApiResponse<TrafficVibrationResultDTO> analyze(@Valid @RequestBody TrafficVibrationRequestDTO dto) {
        TrafficVibrationResultDTO result = vibrationService.analyzeVibration(dto);
        return ApiResponse.success(result);
    }

    @GetMapping("/latest/{bridgeId}")
    public ApiResponse<TrafficVibrationAnalysis> getLatest(@PathVariable Long bridgeId) {
        return vibrationService.getLatestAnalysis(bridgeId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/history/{bridgeId}")
    public ApiResponse<List<TrafficVibrationAnalysis>> getHistory(@PathVariable Long bridgeId) {
        return ApiResponse.success(vibrationService.getAnalysisHistory(bridgeId));
    }

    @GetMapping("/flow/{bridgeId}")
    public ApiResponse<List<TrafficFlowData>> getTrafficFlow(@PathVariable Long bridgeId) {
        return ApiResponse.success(vibrationService.getTrafficFlowData(bridgeId));
    }

    @PostMapping("/flow/ingest")
    public ApiResponse<TrafficFlowData> ingestFlowData(@Valid @RequestBody TrafficFlowData data) {
        TrafficFlowData saved = vibrationService.ingestTrafficFlow(data);
        return ApiResponse.success(saved);
    }

    @GetMapping("/weight-limit/{bridgeId}")
    public ApiResponse<Double> getWeightLimit(@PathVariable Long bridgeId) {
        return vibrationService.getRecommendedWeightLimit(bridgeId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }
}
