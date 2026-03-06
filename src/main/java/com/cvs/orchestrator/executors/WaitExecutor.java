package com.cvs.orchestrator.executors;

import com.cvs.orchestrator.util.EnvVarResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WaitExecutor implements StepExecutor {

    private final EnvVarResolver envVarResolver;

    public WaitExecutor(EnvVarResolver envVarResolver) {
        this.envVarResolver = envVarResolver;
    }

    @Override
    public String getType() {
        return "WAIT";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = envVarResolver.resolveMap(context.getConfig());
        if (config == null) {
            config = new HashMap<>();
        }
        Map<String, Object> metadata = context.getMetadata() != null ? context.getMetadata() : new HashMap<>();

        // Get wait time in seconds from config, default to 10 seconds if not provided
        long waitTimeSeconds = 10;
        if (config.containsKey("durationSeconds")) {
            Object durationObj = config.get("durationSeconds");
            if (durationObj instanceof Number) {
                waitTimeSeconds = ((Number) durationObj).longValue();
            } else if (durationObj instanceof String) {
                try {
                    waitTimeSeconds = Long.parseLong((String) durationObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid durationSeconds: {}, using default 10s", durationObj);
                }
            }
        } else if (config.containsKey("waitTimeSeconds")) {
            Object waitTimeObj = config.get("waitTimeSeconds");
            if (waitTimeObj instanceof Number) {
                waitTimeSeconds = ((Number) waitTimeObj).longValue();
            } else if (waitTimeObj instanceof String) {
                try {
                    waitTimeSeconds = Long.parseLong((String) waitTimeObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid waitTimeSeconds: {}, using default 10s", waitTimeObj);
                }
            }
        }

        long currentTime = System.currentTimeMillis();

        if (metadata.containsKey("startTime")) {
            long startTime = ((Number) metadata.get("startTime")).longValue();
            long elapsedTime = currentTime - startTime;

            if (elapsedTime >= waitTimeSeconds * 1000) {
                log.info("Wait step completed after {} ms", elapsedTime);
                return StepExecutionResult.success();
            } else {
                log.debug("Still waiting... elapsed: {} ms, total wait: {} ms", elapsedTime, waitTimeSeconds * 1000);
                return StepExecutionResult.running(metadata);
            }
        } else {
            log.info("Starting wait step for {} seconds", waitTimeSeconds);
            metadata.put("startTime", currentTime);
            return StepExecutionResult.running(metadata);
        }
    }
}
