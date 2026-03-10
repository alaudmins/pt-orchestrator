package com.cvs.orchestrator.executors;

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
    private final com.cvs.orchestrator.service.ConfigProfileService profileService;
    private final com.cvs.orchestrator.service.SecretService secretService;

    public JenkinsExecutor(EnvVarResolver envVarResolver,
            com.cvs.orchestrator.service.ConfigProfileService profileService,
            com.cvs.orchestrator.service.SecretService secretService) {
        this.envVarResolver = envVarResolver;
        this.profileService = profileService;
        this.secretService = secretService;
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
        String username = (String) config.get("username");
        String token = (String) config.get("token");
        String profileId = (String) config.get("profileId");

        // Prefer Config Profile resolution if profileId is provided
        if (profileId != null && !profileId.isBlank()) {
            java.util.UUID pid = java.util.UUID.fromString(profileId);
            com.cvs.orchestrator.model.ConfigProfileEntity profile = profileService.getProfile(pid)
                    .orElseThrow(() -> new RuntimeException("ConfigProfile not found for id: " + profileId));

            jenkinsUrl = profile.getUrl();
            username = profile.getUsername();
            if (profile.getSecretReference() != null && !profile.getSecretReference().isBlank()) {
                token = secretService.getSecretValue(profile.getSecretReference());
            }
        }

        String jobName = (String) config.get("jobName");

        if (jenkinsUrl == null || jobName == null) {
            return StepExecutionResult
                    .failed("Missing required configuration: jenkinsUrl or jobName (or valid profileId)");
        }

        // Check if already triggered
        if (metadata.containsKey("buildNumber")) {
            // Poll for status
            return checkBuildStatus(jenkinsUrl, jobName, username, token, metadata, config);
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
            Map<String, Object> metadata, Map<String, Object> config) {
        Integer buildNumber = (Integer) metadata.get("buildNumber");
        log.info("Checking Jenkins build status: {}/{}", jobName, buildNumber);

        try {
            WebClient webClient = createWebClient(jenkinsUrl, username, token);
            String jobPath = toJobPath(jobName);

            Map<String, Object> build = webClient.get()
                    .uri(jobPath + "/" + buildNumber + "/api/json?depth=1")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            Boolean building = (Boolean) build.get("building");

            if (building != null && building) {
                // Auto-Approve check
                Boolean autoApprove = (Boolean) config.get("autoApprove");
                if (Boolean.TRUE.equals(autoApprove)) {
                    try {
                        log.info("Polling for pending inputs on build {} using /wfapi/pendingInputActions",
                                buildNumber);
                        // Check for pending inputs
                        java.util.List<Map<String, Object>> pendingInputs = webClient.get()
                                .uri(jobPath + "/" + buildNumber + "/wfapi/pendingInputActions")
                                .retrieve()
                                .bodyToFlux(
                                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                                        })
                                .collectList()
                                .block(Duration.ofSeconds(5));

                        log.info("Received pending inputs payload for build {}: {}", buildNumber, pendingInputs);

                        // Proceed with each pending input if any exist
                        if (pendingInputs != null && !pendingInputs.isEmpty()) {
                            for (Map<String, Object> input : pendingInputs) {
                                String inputId = (String) input.get("id");

                                log.info("Found pending input '{}' on build {}. Attempting auto-approval.", inputId,
                                        buildNumber);

                                // If proceedUrl is provided by the API, we use it (needs to strip context path
                                // if necessary, but standard Spring WebClient might need absolute or relative
                                // depending on setup)
                                // Let's construct it safely using the same jobPath prefix
                                String targetUri = jobPath + "/" + buildNumber + "/input/" + inputId + "/proceed";

                                org.springframework.util.MultiValueMap<String, String> formData = new org.springframework.util.LinkedMultiValueMap<>();
                                formData.add("json", "{}");

                                webClient.post()
                                        .uri(targetUri)
                                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                                        .body(org.springframework.web.reactive.function.BodyInserters
                                                .fromFormData(formData))
                                        .retrieve()
                                        .toBodilessEntity()
                                        .block(Duration.ofSeconds(5));
                                log.info("Successfully auto-approved input '{}'.", inputId);
                            }
                        } else {
                            log.info("No pending inputs found for build {}", buildNumber);
                        }
                    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                        log.info("API Response code {} when checking pending inputs for build {}",
                                e.getStatusCode().value(), buildNumber);
                        // 404 is normal when there are no pending inputs, don't log it
                        if (e.getStatusCode().value() != 404) {
                            log.warn("Failed to check/approve pending inputs for build {}: {}", buildNumber,
                                    e.getMessage());
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to check/approve pending inputs for build {}: {}", buildNumber,
                                ex.getMessage());
                    }
                }

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
