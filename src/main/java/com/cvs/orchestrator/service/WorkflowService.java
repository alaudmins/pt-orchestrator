package com.cvs.orchestrator.service;

import com.cvs.orchestrator.engine.WorkflowEngine;
import com.cvs.orchestrator.model.definition.*;
import com.cvs.orchestrator.model.runtime.Status;
import com.cvs.orchestrator.model.runtime.WorkflowRunEntity;
import com.cvs.orchestrator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowParser parser;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowEngine workflowEngine;
    private final WorkflowRunRepository workflowRunRepository;

    @Transactional
    public WorkflowDefinitionEntity registerWorkflow(String yamlContent) {
        WorkflowDefinition def = parser.parse(yamlContent);

        // Convert to Entity (Manual mapping or Mapper)
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setWorkflowId(def.getId());
        entity.setVersion(def.getVersion());
        entity.setName(def.getName());
        entity.setYamlContent(yamlContent);

        // Remove existing stages if updating? For now, we assume immutable versions or
        // new inserts
        // But if ID/Version exists, we should update or fail.
        // Let's implement upsert logic: find existing, delete stages, re-add.

        // Check if workflow with same ID and version already exists
        Optional<WorkflowDefinitionEntity> existingOpt = workflowDefinitionRepository
                .findByWorkflowIdAndVersion(def.getId(), def.getVersion());

        if (existingOpt.isPresent()) {
            WorkflowDefinitionEntity existing = existingOpt.get();
            boolean hasRuns = workflowRunRepository.existsByWorkflowDefinition(existing);

            if (hasRuns) {
                // Workflow has existing runs - update YAML content, name, AND step configs
                // Step entities are preserved (referential integrity with runs),
                // but their configs are updated to reflect the new YAML.
                log.info("Updating existing workflow {}:{} (has existing runs, updating configs in-place)",
                        existing.getWorkflowId(), existing.getVersion());
                existing.setYamlContent(yamlContent);
                existing.setName(def.getName());

                // Build stepId -> new config map from the freshly-parsed definition
                java.util.Map<String, java.util.Map<String, Object>> newConfigs = new java.util.HashMap<>();
                for (com.cvs.orchestrator.model.definition.StageDefinition stage : def.getStages()) {
                    for (com.cvs.orchestrator.model.definition.StepDefinition step : stage.getSteps()) {
                        newConfigs.put(step.getId(), step.getConfig());
                    }
                }

                // Apply updated configs to existing step entities
                for (StageDefinitionEntity stageEntity : existing.getStages()) {
                    for (StepDefinitionEntity stepEntity : stageEntity.getSteps()) {
                        java.util.Map<String, Object> newConfig = newConfigs.get(stepEntity.getStepId());
                        if (newConfig != null) {
                            stepEntity.setConfig(newConfig);
                        }
                    }
                }

                return workflowDefinitionRepository.save(existing);
            } else {
                // No runs exist, safe to delete and re-create with new structure
                log.info("Deleting existing workflow {}:{} for re-registration (no runs exist)",
                        existing.getWorkflowId(), existing.getVersion());
                workflowDefinitionRepository.delete(existing);
                workflowDefinitionRepository.flush();
            }
        }

        // Map Stages and Steps
        List<StageDefinitionEntity> stageEntities = def.getStages().stream().map(s -> {
            StageDefinitionEntity stageEntity = new StageDefinitionEntity();
            stageEntity.setStageId(s.getId());
            stageEntity.setExecutionMode(s.getExecutionMode());
            stageEntity.setStageOrder(def.getStages().indexOf(s));
            stageEntity.setWorkflowDefinition(entity);

            List<StepDefinitionEntity> stepEntities = s.getSteps().stream().map(step -> {
                StepDefinitionEntity stepEntity = new StepDefinitionEntity();
                stepEntity.setStepId(step.getId());
                stepEntity.setExecutorType(step.getType());
                stepEntity.setConfig(step.getConfig());
                stepEntity.setRetryPolicy(step.getRetry());
                stepEntity.setStepOrder(s.getSteps().indexOf(step));
                stepEntity.setStageDefinition(stageEntity);
                return stepEntity;
            }).toList();

            stageEntity.setSteps(stepEntities);
            return stageEntity;
        }).toList();

        entity.setStages(stageEntities);
        return workflowDefinitionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionEntity> listWorkflows() {
        return workflowDefinitionRepository.findAll();
    }

    public WorkflowRunEntity triggerRun(String workflowId) {
        // Find latest version? Or specific? For now, find *any* (or the last
        // registered?)
        // Let's assume passed workflowId matches the DB 'workflow_id', and we pick the
        // latest version?
        // Actually, let's just pick strictly by workflowId if unique, or fail if
        // multiple versions.
        // Better: Find by ID.
        // Assuming workflowId in URL is the logical ID.

        // Simple Logic: Find *some* definition.
        // Real logic: `findByWorkflowId` returning list, pick latest version.
        // For Phase 1: We'll assume user passes UUID or we lookup by logical ID.
        // Let's support Logical ID lookup.

        WorkflowDefinitionEntity def = workflowDefinitionRepository.findAll().stream()
                .filter(w -> w.getWorkflowId().equals(workflowId))
                .findFirst() // TODO: Sort by version desc
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setWorkflowDefinition(def);
        run.setStatus(Status.PENDING);
        run = workflowRunRepository.save(run); // Save to generate ID

        // Force-load all lazy collections while still in this @Transactional context.
        // startWorkflow() is async and runs without a transaction — the detached entity
        // graph must be fully initialized here so lazy proxies don't throw later.
        run.getWorkflowDefinition().getStages().forEach(stage -> stage.getSteps().forEach(step -> step.getStepId())); // init
                                                                                                                      // steps

        // Async execution
        workflowEngine.startWorkflow(run);

        return run;
    }

    @Transactional(readOnly = true)
    public WorkflowRunEntity getRun(UUID runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("Run not found: " + runId));
        // Eagerly initialize lazy collections while the session is open,
        // so the controller can map stages/steps without a session.
        run.getStageRuns().forEach(stage -> stage.getStepRuns().forEach(step -> step.getStatus()));
        return run;
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunEntity> listRuns() {
        return workflowRunRepository.findAllByOrderByStartTimeDesc();
    }

    @Transactional
    public void deleteWorkflow(String workflowId) {
        WorkflowDefinitionEntity workflow = workflowDefinitionRepository.findAll().stream()
                .filter(w -> w.getWorkflowId().equals(workflowId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        // First, delete all runs associated with this workflow
        List<WorkflowRunEntity> runs = workflowRunRepository.findAll().stream()
                .filter(run -> run.getWorkflowDefinition().getId().equals(workflow.getId()))
                .toList();

        if (!runs.isEmpty()) {
            log.info("Deleting {} workflow runs for workflow: {}", runs.size(), workflowId);
            workflowRunRepository.deleteAll(runs);
        }

        // Then delete the workflow definition
        workflowDefinitionRepository.delete(workflow);
        log.info("Deleted workflow: {}", workflowId);
    }
}
