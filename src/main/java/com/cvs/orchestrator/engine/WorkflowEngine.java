package com.cvs.orchestrator.engine;

import com.cvs.orchestrator.executors.ExecutorRegistry;
import com.cvs.orchestrator.executors.StepExecutionContext;
import com.cvs.orchestrator.executors.StepExecutionResult;
import com.cvs.orchestrator.executors.StepExecutor;
import com.cvs.orchestrator.model.definition.StageDefinitionEntity;
import com.cvs.orchestrator.model.definition.StepDefinitionEntity;
import com.cvs.orchestrator.model.runtime.StageRunEntity;
import com.cvs.orchestrator.model.runtime.Status;
import com.cvs.orchestrator.model.runtime.StepRunEntity;
import com.cvs.orchestrator.model.runtime.WorkflowRunEntity;
import com.cvs.orchestrator.repository.StageRunRepository;
import com.cvs.orchestrator.repository.StepRunRepository;
import com.cvs.orchestrator.repository.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ExecutorRegistry executorRegistry;
    private final WorkflowRunRepository workflowRunRepository;
    private final StageRunRepository stageRunRepository;
    private final StepRunRepository stepRunRepository;

    @Async
    @Transactional
    public void startWorkflow(WorkflowRunEntity run) {
        log.info("Starting workflow run: {}", run.getId());

        run.setStatus(Status.RUNNING);
        run.setStartTime(Instant.now());
        workflowRunRepository.save(run);

        try {
            for (StageDefinitionEntity stageDef : run.getWorkflowDefinition().getStages()) {
                executeStage(run, stageDef);
            }

            run.setStatus(Status.SUCCESS);
            run.setEndTime(Instant.now());
        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            run.setStatus(Status.FAILED);
            run.setEndTime(Instant.now());
        }

        workflowRunRepository.save(run);
    }

    private void executeStage(WorkflowRunEntity workflowRun, StageDefinitionEntity stageDef) {
        log.info("Starting stage: {} for run: {}", stageDef.getStageId(), workflowRun.getId());

        StageRunEntity stageRun = new StageRunEntity();
        stageRun.setWorkflowRun(workflowRun);
        stageRun.setStageDefinition(stageDef);
        stageRun.setStatus(Status.RUNNING);
        stageRun.setStartTime(Instant.now());

        // CRITICAL: Save stageRun BEFORE creating stepRuns that reference it
        stageRun = stageRunRepository.save(stageRun);

        try {
            for (StepDefinitionEntity stepDef : stageDef.getSteps()) {
                executeStep(stageRun, stepDef);
            }
            stageRun.setStatus(Status.SUCCESS);
        } catch (Exception e) {
            log.error("Stage execution failed", e);
            stageRun.setStatus(Status.FAILED);
            throw e;
        } finally {
            stageRun.setEndTime(Instant.now());
            stageRunRepository.save(stageRun);
        }

        log.info("Stage completed: {}", stageDef.getStageId());
    }

    private void executeStep(StageRunEntity stageRun, StepDefinitionEntity stepDef) {
        log.info("Executing step: {} in stage: {}", stepDef.getStepId(), stageRun.getStageDefinition().getStageId());

        StepRunEntity stepRun = new StepRunEntity();
        stepRun.setStageRun(stageRun);
        stepRun.setStepDefinition(stepDef);
        stepRun.setStatus(Status.PENDING);
        stepRun.setAttemptCount(1);
        stepRun.setStartTime(Instant.now());
        stepRun.setMetadata(new HashMap<>());

        stepRun = stepRunRepository.save(stepRun);

        StepExecutor executor = executorRegistry.getExecutor(stepDef.getExecutorType());

        StepExecutionContext context = new StepExecutionContext();
        context.setStepId(stepDef.getStepId());
        context.setConfig(stepDef.getConfig());
        context.setMetadata(stepRun.getMetadata());

        StepExecutionResult result = executor.execute(context);

        stepRun.setStatus(result.getStatus());
        if (result.getMetadata() != null) {
            stepRun.setMetadata(result.getMetadata());
        }

        if (result.getStatus() == Status.RUNNING) {
            // Leave as RUNNING, TaskPoller will continue checking
            stepRunRepository.save(stepRun);
        } else if (result.getStatus() == Status.SUCCESS) {
            stepRun.setEndTime(Instant.now());
            stepRunRepository.save(stepRun);
        } else {
            stepRun.setEndTime(Instant.now());
            stepRun.setLogs(result.getMessage());
            stepRunRepository.save(stepRun);
            throw new RuntimeException("Step failed: " + result.getMessage());
        }
    }
}
