package com.cvs.orchestrator.executors;

import com.cvs.orchestrator.model.runtime.Status;
import com.cvs.orchestrator.util.EnvVarResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GithubExecutor implements StepExecutor {

    private final WebClient webClient;
    private final EnvVarResolver envVarResolver;
    private final com.cvs.orchestrator.service.ConfigProfileService profileService;
    private final com.cvs.orchestrator.service.SecretService secretService;

    public GithubExecutor(EnvVarResolver envVarResolver,
            com.cvs.orchestrator.service.ConfigProfileService profileService,
            com.cvs.orchestrator.service.SecretService secretService) {
        this.envVarResolver = envVarResolver;
        this.profileService = profileService;
        this.secretService = secretService;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    @Override
    public String getType() {
        return "GITHUB_WORKFLOW";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        // Resolve environment variables in config
        Map<String, Object> config = envVarResolver.resolveMap(context.getConfig());
        Map<String, Object> metadata = context.getMetadata() != null ? context.getMetadata() : new HashMap<>();

        String repo = (String) config.get("repo");
        String workflow = (String) config.get("workflow");
        String branch = (String) config.get("branch");
        String token = (String) config.get("token");
        String profileId = (String) config.get("profileId");
        Map<String, Object> inputs = (Map<String, Object>) config.get("inputs");

        // Prefer Config Profile resolution if profileId is provided
        if (profileId != null && !profileId.isBlank()) {
            java.util.UUID pid = java.util.UUID.fromString(profileId);
            com.cvs.orchestrator.model.ConfigProfileEntity profile = profileService.getProfile(pid)
                    .orElseThrow(() -> new RuntimeException("ConfigProfile not found for id: " + profileId));

            if (profile.getSecretReference() != null && !profile.getSecretReference().isBlank()) {
                token = secretService.getSecretValue(profile.getSecretReference());
            }
        }

        // Check if already triggered and we have run ID
        if (metadata.containsKey("runId")) {
            return checkRunStatus(repo, token, metadata);
        } else if (metadata.containsKey("triggeredAt")) {
            // Find the run ID
            return findRunId(repo, workflow, branch, token, metadata);
        } else {
            // Trigger new workflow
            return triggerWorkflow(repo, workflow, branch, token, inputs);
        }
    }

    private StepExecutionResult triggerWorkflow(String repo, String workflow, String branch, String token,
            Map<String, Object> inputs) {
        log.info("Triggering GitHub workflow: {}/{} on branch {}", repo, workflow, branch);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ref", branch);
            if (inputs != null && !inputs.isEmpty()) {
                requestBody.put("inputs", inputs);
            }

            // Split repo into owner/repo to avoid URL encoding the slash
            String[] repoParts = repo.split("/");
            String owner = repoParts[0];
            String repoName = repoParts[1];

            webClient.post()
                    .uri("/repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches", owner, repoName, workflow)
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));

            log.info("Successfully triggered GitHub workflow: {}/{}", repo, workflow);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("triggeredAt", System.currentTimeMillis());
            metadata.put("repo", repo);
            metadata.put("workflow", workflow);
            metadata.put("branch", branch);

            return StepExecutionResult.running(metadata);
        } catch (Exception e) {
            log.error("Failed to trigger GitHub workflow", e);
            return StepExecutionResult.failed("Failed to trigger workflow: " + e.getMessage());
        }
    }

    private StepExecutionResult findRunId(String repo, String workflow, String branch, String token,
            Map<String, Object> metadata) {
        log.info("Finding GitHub workflow run ID for: {}/{}", repo, workflow);

        try {
            Long triggeredAt = ((Number) metadata.get("triggeredAt")).longValue();

            String[] repoParts = repo.split("/");
            String owner = repoParts[0];
            String repoName = repoParts[1];

            // Filter by workflow file name — avoids picking up runs from other workflows
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/actions/runs")
                            .queryParam("workflow_id", workflow)
                            .queryParam("per_page", "5")
                            .build(owner, repoName))
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            List<Map<String, Object>> runs = (List<Map<String, Object>>) response.get("workflow_runs");

            for (Map<String, Object> run : runs) {
                String createdAt = (String) run.get("created_at");
                // Only accept runs created AFTER we triggered (within a 5-minute window)
                if (createdAt != null) {
                    long runCreatedMs = java.time.Instant.parse(createdAt).toEpochMilli();
                    if (runCreatedMs >= triggeredAt - 5000) { // 5s tolerance for clock skew
                        long runId = ((Number) run.get("id")).longValue();
                        metadata.put("runId", runId);
                        metadata.remove("triggeredAt");
                        log.info("Found GitHub workflow run ID: {} for workflow: {}", runId, workflow);
                        return StepExecutionResult.running(metadata);
                    }
                }
            }

            // Run not yet visible — keep polling
            log.debug("No matching run found yet for {} — will retry", workflow);
            return StepExecutionResult.running(metadata);
        } catch (Exception e) {
            log.error("Failed to find GitHub workflow run ID", e);
            return StepExecutionResult.running(metadata);
        }
    }

    private StepExecutionResult checkRunStatus(String repo, String token,
            Map<String, Object> metadata) {
        Long runId = ((Number) metadata.get("runId")).longValue();
        log.info("Checking GitHub workflow run status: {}", runId);

        try {
            String[] repoParts = repo.split("/");
            String owner = repoParts[0];
            String repoName = repoParts[1];

            Map<String, Object> run = webClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{run_id}", owner, repoName, runId)
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            String status = (String) run.get("status");
            log.info("GitHub workflow status: {}", status);

            if (!"completed".equals(status)) {
                return StepExecutionResult.running(metadata);
            }

            String conclusion = (String) run.get("conclusion");
            if ("success".equals(conclusion)) {
                log.info("GitHub workflow completed successfully");
                return StepExecutionResult.success();
            } else {
                log.warn("GitHub workflow failed with conclusion: {}", conclusion);
                return StepExecutionResult.failed("GitHub workflow failed: " + conclusion);
            }
        } catch (Exception e) {
            log.error("Failed to check GitHub workflow status", e);
            return StepExecutionResult.failed("Failed to check status: " + e.getMessage());
        }
    }
}
