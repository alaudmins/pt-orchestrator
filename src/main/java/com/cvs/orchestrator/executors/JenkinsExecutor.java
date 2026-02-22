package com.cvs.orchestrator.executors;

import com.cvs.orchestrator.model.runtime.Status;
import com.cvs.orchestrator.util.EnvVarResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JenkinsExecutor implements StepExecutor {

    private final WebClient webClient;
    private final EnvVarResolver envVarResolver;

    public JenkinsExecutor(EnvVarResolver envVarResolver) {
        this.envVarResolver = envVarResolver;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String getType() {
        return "JENKINS_JOB";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        // Resolve environment variables in config
        Map<String, Object> config = envVarResolver.resolveMap(context.getConfig());
        Map<String, Object> metadata = context.getMetadata() != null ? context.getMetadata() : new HashMap<>();

        String jenkinsUrl = (String) config.get("jenkinsUrl");
        String jobName = (String) config.get("jobName");
        String username = (String) config.get("username");
        String token = (String) config.get("token");

        // Check if already triggered
        if (metadata.containsKey("buildNumber")) {
            // Poll for status
            return checkBuildStatus(jenkinsUrl, jobName, username, token, metadata);
        } else if (metadata.containsKey("queueUrl")) {
            // Check queue for build number
            return checkQueue(jenkinsUrl, jobName, username, token, metadata);
        } else {
            // Trigger new build
            return triggerBuild(jenkinsUrl, jobName, username, token, config);
        }
    }

    private StepExecutionResult triggerBuild(String jenkinsUrl, String jobName, String username, String token,
            Map<String, Object> config) {
        log.info("Triggering Jenkins job: {} at {}", jobName, jenkinsUrl);

        try {
            WebClient webClient = createWebClient(jenkinsUrl, username, token);
            Map<String, Object> parameters = (Map<String, Object>) config.get("parameters");

            // Convert slash-separated folder path to Jenkins URL path.
            // e.g. "TeamA/Perf/load-test" → "/job/TeamA/job/Perf/job/load-test"
            String jobPath = toJobPath(jobName);
            String endpoint;
            if (parameters != null && !parameters.isEmpty()) {
                endpoint = jobPath + "/buildWithParameters";
            } else {
                endpoint = jobPath + "/build";
            }

            // Trigger the build
            var response = webClient.post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(endpoint);
                        if (parameters != null) {
                            parameters.forEach(
                                    (key, value) -> builder.queryParam(key, value != null ? value.toString() : ""));
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));

            // Extract queue URL from Location header
            String queueUrl = response.getHeaders().getLocation() != null
                    ? response.getHeaders().getLocation().toString()
                    : null;

            if (queueUrl == null) {
                log.warn("No queue URL returned from Jenkins, using fallback");
                queueUrl = jenkinsUrl + "/queue/item/latest";
            }

            log.info("Jenkins job triggered successfully. Queue URL: {}", queueUrl);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("queueUrl", queueUrl);
            metadata.put("triggeredAt", System.currentTimeMillis());

            return StepExecutionResult.running(metadata);
        } catch (Exception e) {
            log.error("Failed to trigger Jenkins job: {}", jobName, e);
            return StepExecutionResult.failed("Failed to trigger Jenkins job: " + e.getMessage());
        }
    }

    private StepExecutionResult checkQueue(String jenkinsUrl, String jobName, String username, String token,
            Map<String, Object> metadata) {
        String queueUrl = (String) metadata.get("queueUrl");
        log.info("Checking Jenkins queue: {}", queueUrl);

        try {
            WebClient webClient = createWebClient(jenkinsUrl, username, token);

            // Extract queue item ID from URL
            String queueItemPath = queueUrl.replace(jenkinsUrl, "");

            Map<String, Object> queueItem = webClient.get()
                    .uri(queueItemPath + "/api/json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            // Check if build has started (has executable)
            Map<String, Object> executable = (Map<String, Object>) queueItem.get("executable");

            if (executable != null) {
                Integer buildNumber = (Integer) executable.get("number");
                log.info("Jenkins build started with number: {}", buildNumber);

                metadata.put("buildNumber", buildNumber);
                metadata.remove("queueUrl");

                return StepExecutionResult.running(metadata);
            } else {
                // Check if cancelled or blocked
                Boolean cancelled = (Boolean) queueItem.get("cancelled");
                if (cancelled != null && cancelled) {
                    return StepExecutionResult.failed("Jenkins build was cancelled in queue");
                }

                // Still waiting in queue
                log.debug("Jenkins build still in queue");
                return StepExecutionResult.running(metadata);
            }
        } catch (Exception e) {
            log.error("Failed to check Jenkins queue", e);
            // Keep retrying
            return StepExecutionResult.running(metadata);
        }
    }

    private StepExecutionResult checkBuildStatus(String jenkinsUrl, String jobName, String username, String token,
            Map<String, Object> metadata) {
        Integer buildNumber = (Integer) metadata.get("buildNumber");
        log.info("Checking Jenkins build status: {}/{}", jobName, buildNumber);

        try {
            WebClient webClient = createWebClient(jenkinsUrl, username, token);

            Map<String, Object> build = webClient.get()
                    .uri(toJobPath(jobName) + "/" + buildNumber + "/api/json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            Boolean building = (Boolean) build.get("building");

            if (building != null && building) {
                log.debug("Jenkins build {} is still running", buildNumber);
                return StepExecutionResult.running(metadata);
            }

            String result = (String) build.get("result");

            if ("SUCCESS".equals(result)) {
                log.info("Jenkins build {} completed successfully", buildNumber);
                return StepExecutionResult.success();
            } else if (result == null) {
                // Still building
                return StepExecutionResult.running(metadata);
            } else {
                log.warn("Jenkins build {} failed with result: {}", buildNumber, result);
                return StepExecutionResult.failed("Jenkins build failed: " + result);
            }
        } catch (Exception e) {
            log.error("Failed to check Jenkins build status", e);
            return StepExecutionResult.failed("Failed to check build status: " + e.getMessage());
        }
    }

    private WebClient createWebClient(String jenkinsUrl, String username, String token) {
        String auth = username + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(jenkinsUrl)
                .defaultHeader("Authorization", "Basic " + encodedAuth)
                .build();
    }

    /**
     * Converts a slash-separated Jenkins job path into the URL segment form.
     *
     * Examples:
     * "my-job" → "/job/my-job"
     * "TeamA/load-test" → "/job/TeamA/job/load-test"
     * "TeamA/Perf/load-test" → "/job/TeamA/job/Perf/job/load-test"
     *
     * This supports Jenkins Folders Plugin (CloudBees / Jenkins LTS) where each
     * folder and job segment is separated by /job/ in the REST API path.
     */
    private String toJobPath(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("Jenkins jobName must not be blank");
        }
        // Split on '/' and join with '/job/' separator, then prepend '/job/'
        String[] parts = jobName.trim().split("/");
        return "/job/" + String.join("/job/", parts);
    }
}
