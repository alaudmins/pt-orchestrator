package com.cvs.orchestrator.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class WorkflowRunDetailDTO {
    private UUID runId;
    private UUID workflowId;
    private String workflowName;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private String contextJson;
    private List<StageRunDTO> stages;

    @Data
    public static class StageRunDTO {
        private UUID stageId;
        private String stageDefId;
        private String status;
        private Instant startTime;
        private Instant endTime;
        private List<StepRunDTO> steps;
    }

    @Data
    public static class StepRunDTO {
        private UUID stepId;
        private String stepDefId;
        private String executorType;
        private String status;
        private Instant startTime;
        private Instant endTime;
        private Integer attemptCount;
        private String logs;
        private String metadata;
    }
}
