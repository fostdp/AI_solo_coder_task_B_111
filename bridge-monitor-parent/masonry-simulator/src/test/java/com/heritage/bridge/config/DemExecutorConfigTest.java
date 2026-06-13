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

@SpringBootTest(classes = {DemExecutorConfig.class, MasonryDemProperties.class})
@EnableConfigurationProperties
@TestPropertySource(properties = {
    "masonry.dem.thread-pool.core-size=2",
    "masonry.dem.thread-pool.max-size=4",
    "masonry.dem.thread-pool.queue-capacity=10",
    "masonry.dem.thread-pool.thread-name-prefix=test-dem-"
})
class DemExecutorConfigTest {

    @Autowired
    @Qualifier("demTaskExecutor")
    private Executor demTaskExecutor;

    @Autowired
    private DemExecutorConfig executorConfig;

    @Test
    @DisplayName("DEM线程池Bean应正确创建")
    void demTaskExecutor_ShouldBeCreated() {
        assertNotNull(demTaskExecutor);
        assertTrue(demTaskExecutor instanceof ThreadPoolTaskExecutor);
    }

    @Test
    @DisplayName("DEM线程池应使用正确的配置参数")
    void demTaskExecutor_ShouldHaveCorrectConfiguration() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) demTaskExecutor;
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        assertEquals("test-dem-1", executor.getThreadFactory().newThread(() -> {}).getName().substring(0, 9));
    }

    @Test
    @DisplayName("DEM线程池应能并发执行任务")
    void demTaskExecutor_ShouldExecuteTasksConcurrently() throws InterruptedException {
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            demTaskExecutor.execute(() -> {
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
    @DisplayName("DEM线程池应使用dem-前缀命名")
    void demTaskExecutor_ShouldUseDemThreadNamePrefix() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) demTaskExecutor;
        Thread thread = executor.getThreadFactory().newThread(() -> {});
        assertTrue(thread.getName().startsWith("test-dem-"));
    }

    @Test
    @DisplayName("DEM线程池Bean名称应为demTaskExecutor")
    void demTaskExecutor_ShouldHaveCorrectBeanName() {
        assertNotNull(executorConfig.demTaskExecutor());
    }
}
