package com.heritage.bridge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class TopsisExecutorConfig {

    @Value("${priority.topsis.thread-pool.core-size:2}")
    private int corePoolSize;

    @Value("${priority.topsis.thread-pool.max-size:4}")
    private int maxPoolSize;

    @Value("${priority.topsis.thread-pool.queue-capacity:20}")
    private int queueCapacity;

    @Value("${priority.topsis.thread-pool.thread-name-prefix:topsis-}")
    private String threadNamePrefix;

    @Bean("topsisTaskExecutor")
    public Executor topsisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600);
        executor.initialize();

        log.info("TOPSIS独立线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }
}
