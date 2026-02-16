package com.cvs.orchestrator.controller;

import com.cvs.orchestrator.model.definition.WorkflowDefinitionEntity;
import com.cvs.orchestrator.model.dto.WorkflowRunDTO;
import com.cvs.orchestrator.model.runtime.WorkflowRunEntity;
import com.cvs.orchestrator.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowDefinitionEntity> registerWorkflow(@RequestBody String yamlContent) {
        WorkflowDefinitionEntity workflow = workflowService.registerWorkflow(yamlContent);
        return ResponseEntity.ok(workflow);
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

    @GetMapping("/runs/{runId}")
    public ResponseEntity<WorkflowRunEntity> getRunStatus(@PathVariable UUID runId) {
        WorkflowRunEntity run = workflowService.getRun(runId);
        return ResponseEntity.ok(run);
    }
}
