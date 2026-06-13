package com.heritage.bridge.priority;

import com.heritage.bridge.config.PriorityTopsisProperties;
import com.heritage.bridge.config.TopsisExecutorConfig;
import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.entity.Bridge;
import com.heritage.bridge.repository.BridgeRepository;
import com.heritage.bridge.simulation.TopsisDecisionMaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopsisCalculationServiceTest {

    @Mock
    private TopsisDecisionMaker decisionMaker;

    @Mock
    private PriorityTopsisProperties properties;

    @InjectMocks
    private TopsisCalculationService topsisCalculationService;

    private PriorityTopsisRequestDTO testRequest;
    private PriorityTopsisResultDTO testResult;

    @BeforeEach
    void setUp() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("structuralSafety", 0.35);
        weights.put("damageTrend", 0.30);
        weights.put("historicalValue", 0.20);
        weights.put("maintainability", 0.15);

        testRequest = PriorityTopsisRequestDTO.builder()
                .bridgeIds(Arrays.asList(1L, 2L, 3L, 4L, 5L))
                .weights(weights)
                .year(2026)
                .delphiMethodEnabled(true)
                .build();

        PriorityTopsisResultDTO.Ranking ranking1 = PriorityTopsisResultDTO.Ranking.builder()
                .bridgeId(1L).bridgeName("卢沟桥").closeness(0.85).rank(1).build();
        PriorityTopsisResultDTO.Ranking ranking2 = PriorityTopsisResultDTO.Ranking.builder()
                .bridgeId(2L).bridgeName("赵州桥").closeness(0.78).rank(2).build();

        testResult = PriorityTopsisResultDTO.builder()
                .rankings(Arrays.asList(ranking1, ranking2))
                .delphiMethodUsed(true)
                .expertCount(5)
                .expertConsensusCoefficient(0.72)
                .build();
    }

    @Test
    @DisplayName("同步TOPSIS计算应返回正确结果")
    void calculateSync_ShouldReturnCorrectResult() {
        when(decisionMaker.calculate(any(PriorityTopsisRequestDTO.class))).thenReturn(testResult);

        PriorityTopsisResultDTO result = topsisCalculationService.calculateSync(testRequest);

        assertNotNull(result);
        assertEquals(2, result.getRankings().size());
        assertEquals("卢沟桥", result.getRankings().get(0).getBridgeName());
        assertEquals(0.85, result.getRankings().get(0).getCloseness());
        assertTrue(result.getDelphiMethodUsed());
        verify(decisionMaker, times(1)).calculate(any(PriorityTopsisRequestDTO.class));
    }

    @Test
    @DisplayName("异步TOPSIS计算应返回CompletableFuture")
    void calculateAsync_ShouldReturnCompletableFuture() throws Exception {
        when(decisionMaker.calculate(any(PriorityTopsisRequestDTO.class))).thenReturn(testResult);

        CompletableFuture<PriorityTopsisResultDTO> future = topsisCalculationService.calculateAsync(testRequest);

        assertNotNull(future);
        PriorityTopsisResultDTO result = future.get();
        assertNotNull(result);
        assertEquals(2, result.getRankings().size());
        verify(decisionMaker, times(1)).calculate(any(PriorityTopsisRequestDTO.class));
    }

    @Test
    @DisplayName("异步计算失败时应返回异常Future")
    void calculateAsync_OnErrorShouldReturnFailedFuture() throws Exception {
        when(decisionMaker.calculate(any(PriorityTopsisRequestDTO.class)))
                .thenThrow(new RuntimeException("Test exception"));

        CompletableFuture<PriorityTopsisResultDTO> future = topsisCalculationService.calculateAsync(testRequest);

        assertNotNull(future);
        assertThrows(java.util.concurrent.ExecutionException.class, future::get);
    }

    @Test
    @DisplayName("带回调的异步计算成功时应调用onSuccess")
    void calculateAsyncWithCallback_OnSuccessShouldCallOnSuccess() throws InterruptedException {
        when(decisionMaker.calculate(any(PriorityTopsisRequestDTO.class))).thenReturn(testResult);

        boolean[] successCalled = {false};
        boolean[] errorCalled = {false};

        topsisCalculationService.calculateAsyncWithCallback(
                testRequest,
                result -> { successCalled[0] = true; },
                error -> { errorCalled[0] = true; }
        );

        Thread.sleep(100);
        assertTrue(successCalled[0]);
        assertFalse(errorCalled[0]);
    }

    @Test
    @DisplayName("带回调的异步计算失败时应调用onError")
    void calculateAsyncWithCallback_OnErrorShouldCallOnError() throws InterruptedException {
        when(decisionMaker.calculate(any(PriorityTopsisRequestDTO.class)))
                .thenThrow(new RuntimeException("Test exception"));

        boolean[] successCalled = {false};
        boolean[] errorCalled = {false};

        topsisCalculationService.calculateAsyncWithCallback(
                testRequest,
                result -> { successCalled[0] = true; },
                error -> { errorCalled[0] = true; }
        );

        Thread.sleep(100);
        assertFalse(successCalled[0]);
        assertTrue(errorCalled[0]);
    }

    @Test
    @DisplayName("计算服务应使用决策器进行计算")
    void calculate_ShouldDelegateToDecisionMaker() {
        when(decisionMaker.calculate(any(PriorityTopsisRequestDTO.class))).thenReturn(testResult);

        topsisCalculationService.calculateSync(testRequest);

        verify(decisionMaker).calculate(argThat(request ->
                request.getBridgeIds().contains(1L) &&
                request.getYear() == 2026
        ));
    }
}
