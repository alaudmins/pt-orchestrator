package com.cvs.orchestrator.model.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class WorkflowRunDTO {
    private UUID runId;
    private UUID workflowId;
    private String status;
    private Instant startTime;
    private Instant endTime;
}
