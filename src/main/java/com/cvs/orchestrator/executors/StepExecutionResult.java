package com.cvs.orchestrator.executors;

import com.cvs.orchestrator.model.runtime.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionResult {
    private Status status;
    private Map<String, Object> metadata;
    private String message;

    public static StepExecutionResult success() {
        return new StepExecutionResult(Status.SUCCESS, null, null);
    }

    public static StepExecutionResult running(Map<String, Object> metadata) {
        return new StepExecutionResult(Status.RUNNING, metadata, null);
    }

    public static StepExecutionResult failed(String message) {
        return new StepExecutionResult(Status.FAILED, null, message);
    }
}
