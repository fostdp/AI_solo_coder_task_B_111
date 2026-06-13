package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.priority.PriorityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/priority")
@RequiredArgsConstructor
public class PriorityController {

    private final PriorityService priorityService;

    @PostMapping("/calculate")
    public ApiResponse<PriorityTopsisResultDTO> calculate(@Valid @RequestBody PriorityTopsisRequestDTO dto) {
        PriorityTopsisResultDTO result = priorityService.calculatePriorities(dto);
        return ApiResponse.success(result);
    }

    @GetMapping("/rankings")
    public ApiResponse<List<PriorityTopsisResultDTO.BridgePriority>> getRankings(
            @RequestParam(required = false) Integer planYear) {
        return ApiResponse.success(priorityService.getLatestRankings(planYear));
    }

    @GetMapping("/bridge/{bridgeId}")
    public ApiResponse<PriorityTopsisResultDTO.BridgePriority> getBridgeRanking(
            @PathVariable Long bridgeId,
            @RequestParam(required = false) Integer planYear) {
        return priorityService.getBridgeRanking(bridgeId, planYear)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/plan")
    public ApiResponse<List<PriorityTopsisResultDTO.AnnualPlanItem>> getAnnualPlan(
            @RequestParam(required = false) Integer planYear) {
        return ApiResponse.success(priorityService.getAnnualProtectionPlan(planYear));
    }

    @GetMapping("/plan/bridge/{bridgeId}")
    public ApiResponse<List<PriorityTopsisResultDTO.AnnualPlanItem>> getBridgePlan(
            @PathVariable Long bridgeId) {
        return ApiResponse.success(priorityService.getBridgeProtectionPlan(bridgeId));
    }

    @GetMapping("/years")
    public ApiResponse<List<Integer>> getAvailableYears() {
        return ApiResponse.success(priorityService.getAvailablePlanYears());
    }
}
