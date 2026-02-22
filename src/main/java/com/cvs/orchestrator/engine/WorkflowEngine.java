package com.cvs.orchestrator.engine;

import com.cvs.orchestrator.executors.ExecutorRegistry;
import com.cvs.orchestrator.executors.StepExecutionContext;
import com.cvs.orchestrator.executors.StepExecutionResult;
import com.cvs.orchestrator.executors.StepExecutor;
import com.cvs.orchestrator.model.definition.ExecutionMode;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates workflow execution.
 *
 * Design:
 * SEQUENTIAL — triggerStep() then inline poll loop, one step at a time.
 * PARALLEL — triggerStep() for all steps, then one CompletableFuture per
 * step for concurrent inline polling. allOf().join() waits for
 * all to finish before moving to the next stage.
 *
 * Both modes poll Jenkins directly (no DB reads for flow-control). The
 * TaskPoller updates DB in the background purely for observability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ExecutorRegistry executorRegistry;
    private final WorkflowRunRepository workflowRunRepository;
    private final StageRunRepository stageRunRepository;
    private final StepRunRepository stepRunRepository;

    @Value("${orchestrator.poller.interval:10000}")
    private long pollerIntervalMs;

    // -------------------------------------------------------------------------
    // Entry point (no @Transactional — each repo.save() commits on its own)
    // -------------------------------------------------------------------------

    @Async("workflowExecutor")
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

    // -------------------------------------------------------------------------
    // Stage dispatch
    // -------------------------------------------------------------------------

    private void executeStage(WorkflowRunEntity workflowRun, StageDefinitionEntity stageDef) {
        log.info("Starting stage: {} [{}]", stageDef.getStageId(), stageDef.getExecutionMode());

        StageRunEntity stageRun = new StageRunEntity();
        stageRun.setWorkflowRun(workflowRun);
        stageRun.setStageDefinition(stageDef);
        stageRun.setStatus(Status.RUNNING);
        stageRun.setStartTime(Instant.now());
        stageRun = stageRunRepository.save(stageRun);

        try {
            ExecutionMode mode = stageDef.getExecutionMode() != null
                    ? stageDef.getExecutionMode()
                    : ExecutionMode.SEQUENTIAL;

            if (mode == ExecutionMode.PARALLEL) {
                runParallel(stageRun, stageDef);
            } else {
                runSequential(stageRun, stageDef);
            }
            stageRun.setStatus(Status.SUCCESS);
        } catch (Exception e) {
            log.error("Stage failed: {}", stageDef.getStageId(), e);
            stageRun.setStatus(Status.FAILED);
            throw e;
        } finally {
            stageRun.setEndTime(Instant.now());
            stageRunRepository.save(stageRun);
        }
    }

    // -------------------------------------------------------------------------
    // SEQUENTIAL: trigger → poll → trigger next
    // -------------------------------------------------------------------------

    private void runSequential(StageRunEntity stageRun, StageDefinitionEntity stageDef) {
        for (StepDefinitionEntity stepDef : stageDef.getSteps()) {
            StepRunEntity stepRun = triggerStep(stageRun, stepDef);
            if (stepRun.getStatus() == Status.RUNNING) {
                pollUntilDone(stepRun, stepDef); // blocks until SUCCESS or throws on FAILED
            }
        }
    }

    // -------------------------------------------------------------------------
    // PARALLEL: trigger all → CompletableFuture poll per step → allOf.join()
    // -------------------------------------------------------------------------

    private void runParallel(StageRunEntity stageRun, StageDefinitionEntity stageDef) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> failures = Collections.synchronizedList(new ArrayList<>());

        for (StepDefinitionEntity stepDef : stageDef.getSteps()) {
            StepRunEntity stepRun = triggerStep(stageRun, stepDef);

            if (stepRun.getStatus() == Status.RUNNING) {
                // Capture immutable state for the lambda
                final UUID stepRunId = stepRun.getId();
                final StepExecutor executor = executorRegistry.getExecutor(stepDef.getExecutorType());
                final Map<String, Object> meta = new HashMap<>(stepRun.getMetadata());
                final String stepId = stepDef.getStepId();
                final Map<String, Object> cfg = stepDef.getConfig();

                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    try {
                        pollParallelStep(stepRunId, stepId, executor, cfg, meta);
                    } catch (Exception e) {
                        log.error("Parallel step {} failed: {}", stepId, e.getMessage());
                        failures.add(stepId + ": " + e.getMessage());
                    }
                });
                futures.add(f);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!failures.isEmpty()) {
            throw new RuntimeException("Parallel stage failures — " + String.join("; ", failures));
        }
        log.info("All {} parallel steps completed successfully", futures.size());
    }

    // -------------------------------------------------------------------------
    // Trigger: call executor once, persist initial state
    // -------------------------------------------------------------------------

    private StepRunEntity triggerStep(StageRunEntity stageRun, StepDefinitionEntity stepDef) {
        log.info("Triggering step: {}", stepDef.getStepId());

        StepRunEntity stepRun = new StepRunEntity();
        stepRun.setStageRun(stageRun);
        stepRun.setStepDefinition(stepDef);
        stepRun.setStatus(Status.PENDING);
        stepRun.setAttemptCount(1);
        stepRun.setStartTime(Instant.now());
        stepRun.setMetadata(new HashMap<>());
        stepRun = stepRunRepository.save(stepRun);

        StepExecutor executor = executorRegistry.getExecutor(stepDef.getExecutorType());
        StepExecutionContext ctx = buildContext(stepDef, stepRun.getMetadata());
        StepExecutionResult result = executor.execute(ctx);

        stepRun.setStatus(result.getStatus());
        if (result.getMetadata() != null)
            stepRun.setMetadata(result.getMetadata());

        if (result.getStatus() == Status.FAILED) {
            stepRun.setEndTime(Instant.now());
            stepRun.setLogs(result.getMessage());
            stepRunRepository.save(stepRun);
            throw new RuntimeException("Step failed on trigger: " + result.getMessage());
        }
        if (result.getStatus() == Status.SUCCESS)
            stepRun.setEndTime(Instant.now());

        stepRunRepository.save(stepRun);
        log.info("Step {} triggered — status: {}", stepDef.getStepId(), result.getStatus());
        return stepRun;
    }

    // -------------------------------------------------------------------------
    // Inline poll loop — SEQUENTIAL (called on engine thread, blocks)
    // -------------------------------------------------------------------------

    private void pollUntilDone(StepRunEntity stepRun, StepDefinitionEntity stepDef) {
        StepExecutor executor = executorRegistry.getExecutor(stepDef.getExecutorType());
        StepExecutionContext ctx = buildContext(stepDef, new HashMap<>(stepRun.getMetadata()));

        while (true) {
            sleep(pollerIntervalMs);
            StepExecutionResult result = executor.execute(ctx);
            if (result.getMetadata() != null)
                ctx.setMetadata(result.getMetadata());

            log.info("Step {} polling — status: {}", stepDef.getStepId(), result.getStatus());

            if (result.getStatus() == Status.SUCCESS) {
                stepRun.setStatus(Status.SUCCESS);
                stepRun.setEndTime(Instant.now());
                if (result.getMetadata() != null)
                    stepRun.setMetadata(result.getMetadata());
                stepRunRepository.save(stepRun);
                return;
            }
            if (result.getStatus() == Status.FAILED) {
                stepRun.setStatus(Status.FAILED);
                stepRun.setEndTime(Instant.now());
                stepRun.setLogs(result.getMessage());
                stepRunRepository.save(stepRun);
                throw new RuntimeException("Step failed: " + result.getMessage());
            }
            // RUNNING — update metadata in DB for TaskPoller observability
            if (result.getMetadata() != null) {
                stepRun.setMetadata(result.getMetadata());
                stepRunRepository.save(stepRun);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inline poll loop — PARALLEL (called on CompletableFuture thread)
    // stepRunRepository.save() auto-commits since there is no outer transaction
    // -------------------------------------------------------------------------

    private void pollParallelStep(UUID stepRunId, String stepId,
            StepExecutor executor, Map<String, Object> config,
            Map<String, Object> initialMeta) {
        log.info("Parallel polling started for step {}", stepId);
        StepExecutionContext ctx = new StepExecutionContext();
        ctx.setStepId(stepId);
        ctx.setConfig(config);
        ctx.setMetadata(new HashMap<>(initialMeta));

        while (true) {
            sleep(pollerIntervalMs);
            StepExecutionResult result = executor.execute(ctx);
            if (result.getMetadata() != null)
                ctx.setMetadata(result.getMetadata());

            log.info("Parallel step {} — status: {}", stepId, result.getStatus());

            // Persist status for observability — auto-committed (no outer transaction)
            stepRunRepository.findById(stepRunId).ifPresent(sr -> {
                sr.setStatus(result.getStatus());
                if (result.getMetadata() != null)
                    sr.setMetadata(result.getMetadata());
                if (result.getStatus() != Status.RUNNING)
                    sr.setEndTime(Instant.now());
                if (result.getMessage() != null)
                    sr.setLogs(result.getMessage());
                stepRunRepository.save(sr);
            });

            if (result.getStatus() == Status.SUCCESS) {
                log.info("Parallel step {} completed successfully", stepId);
                return;
            }
            if (result.getStatus() == Status.FAILED) {
                throw new RuntimeException("Step failed: " + result.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StepExecutionContext buildContext(StepDefinitionEntity stepDef, Map<String, Object> meta) {
        StepExecutionContext ctx = new StepExecutionContext();
        ctx.setStepId(stepDef.getStepId());
        ctx.setConfig(stepDef.getConfig());
        ctx.setMetadata(meta);
        return ctx;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Execution interrupted", ie);
        }
    }
}
