package com.cvs.orchestrator.executors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExecutorRegistry {

    private final Map<String, StepExecutor> executors = new HashMap<>();

    public ExecutorRegistry(List<StepExecutor> executorList) {
        for (StepExecutor executor : executorList) {
            executors.put(executor.getType(), executor);
            log.info("Registered executor: {}", executor.getType());
        }
    }

    public StepExecutor getExecutor(String type) {
        StepExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for type: " + type);
        }
        return executor;
    }
}
