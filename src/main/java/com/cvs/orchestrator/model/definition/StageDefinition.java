package com.cvs.orchestrator.model.definition;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class StageDefinition {
    private String id;
    private ExecutionMode executionMode = ExecutionMode.SEQUENTIAL;
    private List<StepDefinition> steps;
}
