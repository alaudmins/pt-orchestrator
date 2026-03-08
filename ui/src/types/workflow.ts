export interface WorkflowDefinition {
    workflowId: string;
    name?: string;
    version?: string;
    yamlContent?: string;
}

export interface WorkflowRunDetail {
    runId: string;
    workflowId: string;
    workflowName?: string;
    status: string;
    startTime: string;
    endTime?: string;
    stages: StageRun[];
}

export interface StageRun {
    stageId: string;
    stageDefId: string;
    status: string;
    startTime: string;
    endTime?: string;
    steps: StepRun[];
}

export interface StepRun {
    stepId: string;
    stepDefId: string;
    executorType: string;
    config?: any;
    status: string;
    startTime: string;
    endTime?: string;
    attemptCount: number;
    logs?: string;
}
