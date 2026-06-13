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
public class DemExecutorConfig {

    @Value("${masonry.dem.thread-pool.core-size:4}")
    private int corePoolSize;

    @Value("${masonry.dem.thread-pool.max-size:8}")
    private int maxPoolSize;

    @Value("${masonry.dem.thread-pool.queue-capacity:50}")
    private int queueCapacity;

    @Value("${masonry.dem.thread-pool.thread-name-prefix:dem-}")
    private String threadNamePrefix;

    @Bean("demTaskExecutor")
    public Executor demTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();

        log.info("DEM独立线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }
}
