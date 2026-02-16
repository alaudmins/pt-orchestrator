package com.cvs.orchestrator.engine;

import com.cvs.orchestrator.executors.ExecutorRegistry;
import com.cvs.orchestrator.executors.StepExecutionContext;
import com.cvs.orchestrator.executors.StepExecutionResult;
import com.cvs.orchestrator.executors.StepExecutor;
import com.cvs.orchestrator.model.runtime.Status;
import com.cvs.orchestrator.model.runtime.StepRunEntity;
import com.cvs.orchestrator.repository.StepRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPoller {

    private final StepRunRepository stepRunRepository;
    private final ExecutorRegistry executorRegistry;

    @Scheduled(fixedDelayString = "${orchestrator.poller.interval:10000}", initialDelayString = "${orchestrator.poller.initial-delay:5000}")
    @Transactional
    public void pollRunningSteps() {
        List<StepRunEntity> runningSteps = stepRunRepository.findByStatus(Status.RUNNING);

        log.debug("Polling {} running steps", runningSteps.size());

        for (StepRunEntity stepRun : runningSteps) {
            try {
                checkStepStatus(stepRun);
            } catch (Exception e) {
                log.error("Error checking step status: {}", stepRun.getId(), e);
            }
        }
    }

    private void checkStepStatus(StepRunEntity stepRun) {
        String executorType = stepRun.getStepDefinition().getExecutorType();
        StepExecutor executor = executorRegistry.getExecutor(executorType);

        StepExecutionContext context = new StepExecutionContext();
        context.setStepId(stepRun.getStepDefinition().getStepId());
        context.setConfig(stepRun.getStepDefinition().getConfig());
        context.setMetadata(stepRun.getMetadata());

        StepExecutionResult result = executor.execute(context);

        stepRun.setStatus(result.getStatus());
        if (result.getMetadata() != null) {
            stepRun.setMetadata(result.getMetadata());
        }

        if (result.getStatus() != Status.RUNNING) {
            stepRun.setEndTime(Instant.now());
            if (result.getMessage() != null) {
                stepRun.setLogs(result.getMessage());
            }
        }

        stepRunRepository.save(stepRun);

        log.info("Step {} status updated to: {}", stepRun.getId(), result.getStatus());
    }
}
