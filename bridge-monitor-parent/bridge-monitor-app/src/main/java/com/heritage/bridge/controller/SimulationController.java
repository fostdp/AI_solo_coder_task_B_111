package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.FemRequestDTO;
import com.heritage.bridge.entity.FemResult;
import com.heritage.bridge.fem.FemSimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final FemSimulatorService femSimulatorService;

    @PostMapping("/fem")
    public ApiResponse<FemResult.ResultWrapper> simulate(@Valid @RequestBody FemRequestDTO dto) {
        FemResult.ResultWrapper result = femSimulatorService.simulateOnDemand(dto);
        return ApiResponse.success(result);
    }

    @GetMapping("/fem/{bridgeId}")
    public ApiResponse<FemResult> getLatest(@PathVariable Long bridgeId) {
        FemResult latest = femSimulatorService.getLatest(bridgeId);
        return ApiResponse.success(latest);
    }

    @GetMapping("/fem/{bridgeId}/list")
    public ApiResponse<List<FemResult>> listResults(
            @PathVariable Long bridgeId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(femSimulatorService.listByBridge(bridgeId, limit));
    }
}
