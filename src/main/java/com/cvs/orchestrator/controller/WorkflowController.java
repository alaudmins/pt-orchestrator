package com.cvs.orchestrator.controller;

import com.cvs.orchestrator.model.definition.WorkflowDefinitionEntity;
import com.cvs.orchestrator.model.dto.WorkflowRunDTO;
import com.cvs.orchestrator.model.dto.WorkflowRunDetailDTO;
import com.cvs.orchestrator.model.runtime.WorkflowRunEntity;
import com.cvs.orchestrator.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/workflows")
    public ResponseEntity<java.util.Map<String, Object>> registerWorkflow(@RequestBody String yamlContent) {
        WorkflowDefinitionEntity workflow = workflowService.registerWorkflow(yamlContent);
        return ResponseEntity.ok(java.util.Map.of(
                "workflowId", workflow.getWorkflowId(), // ← use this for /run and /delete
                "name", workflow.getName(),
                "version", workflow.getVersion(),
                "message", "Workflow registered successfully. Use 'workflowId' to trigger a run."));
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowDefinitionEntity>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listWorkflows());
    }

    @PostMapping("/workflows/{workflowId}/run")
    public ResponseEntity<WorkflowRunDTO> triggerRun(@PathVariable String workflowId) {
        WorkflowRunEntity run = workflowService.triggerRun(workflowId);

        WorkflowRunDTO dto = new WorkflowRunDTO();
        dto.setRunId(run.getId());
        dto.setWorkflowId(run.getWorkflowDefinition().getId());
        dto.setStatus(run.getStatus().name());
        dto.setStartTime(run.getStartTime());
        dto.setEndTime(run.getEndTime());

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/runs")
    public ResponseEntity<List<WorkflowRunDTO>> listRuns() {
        List<WorkflowRunEntity> runs = workflowService.listRuns();

        List<WorkflowRunDTO> dtos = runs.stream()
                .map(run -> {
                    WorkflowRunDTO dto = new WorkflowRunDTO();
                    dto.setRunId(run.getId());
                    dto.setWorkflowId(run.getWorkflowDefinition().getId());
                    dto.setStatus(run.getStatus().name());
                    dto.setStartTime(run.getStartTime());
                    dto.setEndTime(run.getEndTime());
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<WorkflowRunDetailDTO> getRunStatus(@PathVariable UUID runId) {
        WorkflowRunEntity run = workflowService.getRun(runId);

        WorkflowRunDetailDTO dto = new WorkflowRunDetailDTO();
        dto.setRunId(run.getId());
        dto.setWorkflowId(run.getWorkflowDefinition().getId());
        dto.setWorkflowName(run.getWorkflowDefinition().getName());
        dto.setStatus(run.getStatus().name());
        dto.setStartTime(run.getStartTime());
        dto.setEndTime(run.getEndTime());

        // Note: stages and detailed execution info will be added in future phase
        dto.setStages(List.of());

        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/workflows/{workflowId}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable String workflowId) {
        workflowService.deleteWorkflow(workflowId);
        return ResponseEntity.noContent().build();
    }
}
