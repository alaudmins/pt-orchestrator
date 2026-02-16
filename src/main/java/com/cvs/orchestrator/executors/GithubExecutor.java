package com.cvs.orchestrator.executors;

import com.cvs.orchestrator.model.runtime.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GithubExecutor implements StepExecutor {

    @Override
    public String getType() {
        return "GITHUB_WORKFLOW";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        Map<String, Object> metadata = context.getMetadata() != null ? context.getMetadata() : new HashMap<>();

        String repo = (String) config.get("repo");
        String workflow = (String) config.get("workflow");
        String branch = (String) config.get("branch");
        String token = (String) config.get("token");

        // Check if already triggered and we have run ID
        if (metadata.containsKey("runId")) {
            return checkRunStatus(repo, workflow, token, metadata);
        } else if (metadata.containsKey("triggeredAt")) {
            // Find the run ID
            return findRunId(repo, workflow, branch, token, metadata);
        } else {
            // Trigger new workflow
            return triggerWorkflow(repo, workflow, branch, token);
        }
    }

    private StepExecutionResult triggerWorkflow(String repo, String workflow, String branch, String token) {
        log.info("Triggering GitHub workflow: {}/{} on branch {}", repo, workflow, branch);

        // TODO: Implement actual GitHub API call
        // POST /repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches
        // Body: { "ref": branch }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("triggeredAt", System.currentTimeMillis());
        metadata.put("repo", repo);
        metadata.put("workflow", workflow);
        metadata.put("branch", branch);

        return StepExecutionResult.running(metadata);
    }

    private StepExecutionResult findRunId(String repo, String workflow, String branch, String token,
            Map<String, Object> metadata) {
        log.info("Finding GitHub workflow run ID for: {}/{}", repo, workflow);

        // TODO: Implement actual GitHub API call
        // GET /repos/{owner}/{repo}/actions/runs
        // Filter by workflow, branch, and created_at > triggeredAt

        // Mock: Assume we found the run
        List runs = List.of(Map.of("id", 123456L, "status", "in_progress"));

        if (!runs.isEmpty()) {
            Map run = (Map) runs.get(0);
            metadata.put("runId", run.get("id"));
            metadata.remove("triggeredAt");
            return StepExecutionResult.running(metadata);
        }

        // Still waiting for run to appear
        return StepExecutionResult.running(metadata);
    }

    private StepExecutionResult checkRunStatus(String repo, String workflow, String token,
            Map<String, Object> metadata) {
        Long runId = ((Number) metadata.get("runId")).longValue();
        log.info("Checking GitHub workflow run status: {}", runId);

        // TODO: Implement actual GitHub API call
        // GET /repos/{owner}/{repo}/actions/runs/{run_id}
        // Check "status": "completed", "conclusion": "success"

        // Mock response
        Map run = Map.of(
                "status", "completed",
                "conclusion", "success");

        String status = (String) run.get("status");
        if (!"completed".equals(status)) {
            return StepExecutionResult.running(metadata);
        }

        String conclusion = (String) run.get("conclusion");
        if ("success".equals(conclusion)) {
            return StepExecutionResult.success();
        } else {
            return StepExecutionResult.failed("GitHub workflow failed: " + conclusion);
        }
    }
}
