package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.DamagePredictionRequestDTO;
import com.heritage.bridge.damage.DamagePredictorService;
import com.heritage.bridge.entity.DamagePrediction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/damage")
@RequiredArgsConstructor
public class DamageController {

    private final DamagePredictorService damagePredictorService;

    @PostMapping("/calculate")
    public ApiResponse<DamagePrediction> calculate(@Valid @RequestBody DamagePredictionRequestDTO dto) {
        DamagePrediction result = damagePredictorService.calculateOnDemand(dto);
        return ApiResponse.success(result);
    }

    @GetMapping("/list")
    public ApiResponse<List<DamagePrediction>> listPredictions(
            @RequestParam Long bridgeId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(damagePredictorService.listByBridge(bridgeId, limit));
    }

    @GetMapping("/latest/{bridgeId}")
    public ApiResponse<DamagePrediction> getLatest(@PathVariable Long bridgeId) {
        return ApiResponse.success(damagePredictorService.getLatest(bridgeId).orElse(null));
    }
}
