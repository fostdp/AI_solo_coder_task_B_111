package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.dto.MasonryDemRequestDTO;
import com.heritage.bridge.dto.MasonryDemResultDTO;
import com.heritage.bridge.entity.MasonryDemResult;
import com.heritage.bridge.entity.MasonryParams;
import com.heritage.bridge.masonry.MasonrySimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/masonry")
@RequiredArgsConstructor
public class MasonryController {

    private final MasonrySimulationService masonryService;

    @PostMapping("/simulate")
    public ApiResponse<MasonryDemResultDTO> simulate(@Valid @RequestBody MasonryDemRequestDTO dto) {
        MasonryDemResultDTO result = masonryService.simulateMasonry(dto);
        return ApiResponse.success(result);
    }

    @GetMapping("/latest/{bridgeId}")
    public ApiResponse<MasonryDemResult> getLatest(@PathVariable Long bridgeId) {
        Optional<MasonryDemResult> latest = masonryService.getLatestResult(bridgeId);
        return ApiResponse.success(latest.orElse(null));
    }

    @GetMapping("/history/{bridgeId}")
    public ApiResponse<List<MasonryDemResult>> getHistory(@PathVariable Long bridgeId) {
        return ApiResponse.success(masonryService.getResultHistory(bridgeId));
    }

    @GetMapping("/params/{bridgeId}")
    public ApiResponse<MasonryParams> getParams(@PathVariable Long bridgeId) {
        return masonryService.getLatestParams(bridgeId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @PostMapping("/params")
    public ApiResponse<MasonryParams> saveParams(@Valid @RequestBody MasonryParams params) {
        MasonryParams saved = masonryService.saveParams(params);
        return ApiResponse.success(saved);
    }

    @GetMapping("/force-chain/{bridgeId}")
    public ApiResponse<MasonryDemResultDTO> getForceChain(@PathVariable Long bridgeId,
                                                          @RequestParam(required = false) String analysisType) {
        Optional<MasonryDemResultDTO> result = masonryService.getForceChainVisualization(bridgeId, analysisType);
        return ApiResponse.success(result.orElse(null));
    }
}
