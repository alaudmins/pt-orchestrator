package com.cvs.orchestrator.executors;

public interface StepExecutor {

    /**
     * Get the executor type identifier
     */
    String getType();

    /**
     * Execute the step. May trigger external system and return RUNNING status.
     * Should check metadata to continue polling if already started.
     */
    StepExecutionResult execute(StepExecutionContext context);
}
