package com.cvs.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool for workflow background execution.
 *
 * Each workflow run gets its own thread from this pool.
 * Sequential steps block that thread; Parallel steps spawn CompletableFutures
 * on the common fork-join pool but the parent remains on this pool.
 *
 * Size guidance:
 * corePoolSize = max concurrent workflows you ever expect simultaneously
 * maxPoolSize = burst ceiling (spare threads for parallel step polling)
 * queueCapacity = pending-trigger backlog before rejecting
 */
@Configuration
public class AsyncConfig {

    @Value("${orchestrator.executor.core-pool-size:5}")
    private int corePoolSize;

    @Value("${orchestrator.executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${orchestrator.executor.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "workflowExecutor")
    public Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("wf-engine-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
