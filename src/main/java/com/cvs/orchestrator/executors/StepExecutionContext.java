package com.cvs.orchestrator.executors;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class StepExecutionContext {
    private String stepId;
    private Map<String, Object> config;
    private Map<String, Object> metadata;
}
