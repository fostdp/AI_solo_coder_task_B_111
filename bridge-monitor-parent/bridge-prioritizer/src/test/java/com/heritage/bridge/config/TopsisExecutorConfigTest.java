package com.heritage.bridge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {TopsisExecutorConfig.class, PriorityTopsisProperties.class})
@EnableConfigurationProperties
@TestPropertySource(properties = {
    "priority.topsis.thread-pool.core-size=1",
    "priority.topsis.thread-pool.max-size=2",
    "priority.topsis.thread-pool.queue-capacity=5",
    "priority.topsis.thread-pool.thread-name-prefix=test-topsis-"
})
class TopsisExecutorConfigTest {

    @Autowired
    @Qualifier("topsisTaskExecutor")
    private Executor topsisTaskExecutor;

    @Autowired
    private TopsisExecutorConfig executorConfig;

    @Test
    @DisplayName("TOPSIS线程池Bean应正确创建")
    void topsisTaskExecutor_ShouldBeCreated() {
        assertNotNull(topsisTaskExecutor);
        assertTrue(topsisTaskExecutor instanceof ThreadPoolTaskExecutor);
    }

    @Test
    @DisplayName("TOPSIS线程池应使用正确的配置参数")
    void topsisTaskExecutor_ShouldHaveCorrectConfiguration() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) topsisTaskExecutor;
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(2, executor.getMaxPoolSize());
    }

    @Test
    @DisplayName("TOPSIS线程池应能并发执行任务")
    void topsisTaskExecutor_ShouldExecuteTasksConcurrently() throws InterruptedException {
        int taskCount = 5;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            topsisTaskExecutor.execute(() -> {
                try {
                    Thread.sleep(10);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed);
        assertEquals(taskCount, successCount.get());
    }

    @Test
    @DisplayName("TOPSIS线程池应使用topsis-前缀命名")
    void topsisTaskExecutor_ShouldUseTopsisThreadNamePrefix() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) topsisTaskExecutor;
        Thread thread = executor.getThreadFactory().newThread(() -> {});
        assertTrue(thread.getName().startsWith("test-topsis-"));
    }

    @Test
    @DisplayName("TOPSIS线程池Bean名称应为topsisTaskExecutor")
    void topsisTaskExecutor_ShouldHaveCorrectBeanName() {
        assertNotNull(executorConfig.topsisTaskExecutor());
    }
}
