package com.heritage.bridge.controller;

import com.heritage.bridge.dto.ApiResponse;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.service.BridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bridges")
@Validated
@RequiredArgsConstructor
public class BridgeController {

    private final BridgeService bridgeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Bridge>>> list() {
        return ResponseEntity.ok(ApiResponse.success(bridgeService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Bridge>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bridgeService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Bridge>> create(@RequestBody Bridge bridge) {
        return ResponseEntity.ok(ApiResponse.success(bridgeService.save(bridge)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Bridge>> update(@PathVariable Long id, @RequestBody Bridge bridge) {
        return ResponseEntity.ok(ApiResponse.success(bridgeService.update(id, bridge)));
    }
}
