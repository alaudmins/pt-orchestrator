package com.cvs.orchestrator.controller;

import com.cvs.orchestrator.model.ConfigProfileEntity;
import com.cvs.orchestrator.service.ConfigProfileService;
import com.cvs.orchestrator.service.SecretService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.yaml.snakeyaml.Yaml;

import java.time.Duration;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/github/parameters")
@RequiredArgsConstructor
public class GithubIntrospectionController {

    private final ConfigProfileService profileService;
    private final SecretService secretService;

    @GetMapping
    public ResponseEntity<?> getWorkflowInputs(
            @RequestParam UUID profileId,
            @RequestParam String repo,
            @RequestParam String workflow) {

        log.info("Introspecting GitHub workflow inputs for profileId {}, repo {} and workflow {}", profileId, repo,
                workflow);

        Optional<ConfigProfileEntity> profileOpt = profileService.getProfile(profileId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        ConfigProfileEntity profile = profileOpt.get();
        if (!"GITHUB".equalsIgnoreCase(profile.getProfileType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile is not a GitHub profile"));
        }

        String token = null;
        if (profile.getSecretReference() != null && !profile.getSecretReference().isBlank()) {
            token = secretService.getSecretValue(profile.getSecretReference());
        }

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "GitHub profile does not have a valid token (requires PAT)."));
        }

        try {
            // Split repo into owner/repo
            String[] repoParts = repo.split("/");
            if (repoParts.length != 2) {
                return ResponseEntity.badRequest().body(Map.of("error", "Repository must be in 'owner/repo' format"));
            }
            String owner = repoParts[0];
            String repoName = repoParts[1];

            WebClient webClient = WebClient.builder()
                    .baseUrl("https://api.github.com")
                    .defaultHeader("Accept", "application/vnd.github.v3+json")
                    .defaultHeader("Authorization", "Bearer " + token)
                    .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                    .build();

            // The workflow filename usually has .yaml or .yml extension
            String workflowPath = ".github/workflows/" + workflow;

            // Get contents of workflow file
            Map<String, Object> contentsResponse = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repoName, workflowPath)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (contentsResponse == null || !contentsResponse.containsKey("content")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to retrieve workflow file contents from GitHub"));
            }

            String base64Content = (String) contentsResponse.get("content");
            // GitHub API returns base64 content with newlines
            base64Content = base64Content.replace("\n", "").replace("\r", "");
            byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
            String yamlContent = new String(decodedBytes);

            // Parse YAML
            Yaml yamlParser = new Yaml();
            Map<String, Object> parsedYaml = yamlParser.load(yamlContent);

            return ResponseEntity.ok(extractInputsFromYaml(parsedYaml));

        } catch (WebClientResponseException.NotFound e) {
            log.error("GitHub workflow not found: {}", workflow);
            return ResponseEntity.badRequest().body(Map.of("error",
                    "GitHub workflow file not found in repository. Check the file name and repository permissions."));
        } catch (WebClientResponseException e) {
            log.error("GitHub API error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "GitHub API error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to introspect GitHub workflow", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to connect to GitHub: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> extractInputsFromYaml(Map<String, Object> yaml) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (yaml == null) {
            return result;
        }

        // SnakeYAML converts "on:" to a boolean key (true) under YAML 1.1 specs
        Object onBlock = yaml.get("on");
        if (onBlock == null) {
            onBlock = yaml.get(true);
        }

        if (!(onBlock instanceof Map)) {
            return result;
        }

        Map<String, Object> onMap = (Map<String, Object>) onBlock;
        if (!onMap.containsKey("workflow_dispatch")) {
            return result;
        }

        Object dispatchBlock = onMap.get("workflow_dispatch");
        if (!(dispatchBlock instanceof Map)) {
            return result; // workflow_dispatch exists but has no custom inputs
        }

        Map<String, Object> dispatchMap = (Map<String, Object>) dispatchBlock;
        if (!dispatchMap.containsKey("inputs")) {
            return result;
        }

        Object inputsBlock = dispatchMap.get("inputs");
        if (!(inputsBlock instanceof Map)) {
            return result;
        }

        Map<String, Map<String, Object>> inputsMap = (Map<String, Map<String, Object>>) inputsBlock;

        for (Map.Entry<String, Map<String, Object>> entry : inputsMap.entrySet()) {
            String paramName = entry.getKey();
            Map<String, Object> details = entry.getValue();

            Map<String, Object> normalized = new HashMap<>();
            normalized.put("name", paramName);

            if (details != null) {
                normalized.put("type", details.getOrDefault("type", "string"));
                normalized.put("description", details.get("description"));
                normalized.put("defaultValue", details.get("default"));
                normalized.put("required", details.getOrDefault("required", false));
                if (details.containsKey("options")) {
                    normalized.put("options", details.get("options"));
                }
            } else {
                normalized.put("type", "string");
            }

            result.add(normalized);
        }

        return result;
    }
}
