package com.cvs.orchestrator.model.definition;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class WorkflowDefinition {
    private String id;
    private String name;
    private String version;
    private List<StageDefinition> stages;
}
