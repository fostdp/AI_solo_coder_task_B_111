package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.WeatheringRequestDTO;
import com.heritage.bridge.dto.WeatheringResultDTO;
import com.heritage.bridge.entity.WeatheringData;
import com.heritage.bridge.weathering.WeatheringEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/weathering")
@RequiredArgsConstructor
public class WeatheringController {

    private final WeatheringEvaluationService weatheringService;

    @PostMapping("/evaluate")
    public ApiResponse<WeatheringResultDTO> evaluate(@Valid @RequestBody WeatheringRequestDTO dto) {
        WeatheringResultDTO result = weatheringService.evaluateWeathering(dto);
        return ApiResponse.success(result);
    }

    @GetMapping("/latest/{bridgeId}")
    public ApiResponse<WeatheringData> getLatest(@PathVariable Long bridgeId) {
        return weatheringService.getLatestWeathering(bridgeId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/history/{bridgeId}")
    public ApiResponse<List<WeatheringData>> getHistory(@PathVariable Long bridgeId) {
        return ApiResponse.success(weatheringService.getWeatheringHistory(bridgeId));
    }

    @GetMapping("/distribution/{bridgeId}")
    public ApiResponse<Map<String, Integer>> getGradeDistribution(@PathVariable Long bridgeId) {
        return ApiResponse.success(weatheringService.getWeatheringGradeDistribution(bridgeId));
    }
}
