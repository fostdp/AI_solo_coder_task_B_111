package com.heritage.bridge.priority;

import com.heritage.bridge.config.PriorityTopsisProperties;
import com.heritage.bridge.dto.PriorityTopsisRequestDTO;
import com.heritage.bridge.dto.PriorityTopsisResultDTO;
import com.heritage.bridge.simulation.TopsisDecisionMaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopsisCalculationService {

    private final TopsisDecisionMaker decisionMaker;
    private final PriorityTopsisProperties properties;

    @Async("topsisTaskExecutor")
    public CompletableFuture<PriorityTopsisResultDTO> calculateAsync(PriorityTopsisRequestDTO request) {
        log.info("TOPSIS异步计算开始，桥数={}", request.getExpertRatings() != null ?
                request.getExpertRatings().size() : "默认全部");
        try {
            PriorityTopsisResultDTO result = decisionMaker.calculate(request);
            log.info("TOPSIS异步计算完成，排序桥梁数={}", result.getRankings().size());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("TOPSIS异步计算失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public PriorityTopsisResultDTO calculateSync(PriorityTopsisRequestDTO request) {
        return decisionMaker.calculate(request);
    }

    @Async("topsisTaskExecutor")
    public void calculateAsyncWithCallback(PriorityTopsisRequestDTO request,
                                           Consumer<PriorityTopsisResultDTO> onSuccess,
                                           Consumer<Throwable> onError) {
        try {
            PriorityTopsisResultDTO result = decisionMaker.calculate(request);
            if (onSuccess != null) {
                onSuccess.accept(result);
            }
        } catch (Exception e) {
            log.error("TOPSIS回调计算失败", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }
}
