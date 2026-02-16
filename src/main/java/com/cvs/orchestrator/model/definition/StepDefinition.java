package com.cvs.orchestrator.model.definition;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
public class StepDefinition {
    private String id;
    private String type; // JENKINS_JOB, GITHUB_WORKFLOW
    private Map<String, Object> config;
    private RetryPolicy retry;
}
