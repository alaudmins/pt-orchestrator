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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/jenkins/parameters")
@RequiredArgsConstructor
public class JenkinsIntrospectionController {

    private final ConfigProfileService profileService;
    private final SecretService secretService;

    @GetMapping
    public ResponseEntity<?> getJobParameters(
            @RequestParam UUID profileId,
            @RequestParam String jobName) {

        log.info("Introspecting Jenkins job parameters for profileId {} and jobName {}", profileId, jobName);

        Optional<ConfigProfileEntity> profileOpt = profileService.getProfile(profileId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        ConfigProfileEntity profile = profileOpt.get();
        if (!"JENKINS".equalsIgnoreCase(profile.getProfileType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile is not a Jenkins profile"));
        }

        String jenkinsUrl = profile.getUrl();
        String username = profile.getUsername();
        String token = null;

        if (profile.getSecretReference() != null && !profile.getSecretReference().isBlank()) {
            token = secretService.getSecretValue(profile.getSecretReference());
        }

        try {
            WebClient webClient = createWebClient(jenkinsUrl, username, token);
            String jobPath = toJobPath(jobName);

            // Fetch job info focusing on parameter definitions
            String apiUrl = jobPath
                    + "/api/json?tree=property[parameterDefinitions[name,type,description,defaultParameterValue[value]]]";

            log.info("Executing Jenkins Parameter Sync API Request to URL: {}{}", jenkinsUrl, apiUrl);

            Map<String, Object> jobDetails = webClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (jobDetails == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Map<String, Object>> properties = (List<Map<String, Object>>) jobDetails.get("property");
            if (properties == null || properties.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Find ParametersDefinitionProperty
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (Map<String, Object> prop : properties) {
                if (prop.containsKey("parameterDefinitions")) {
                    List<Map<String, Object>> defs = (List<Map<String, Object>>) prop.get("parameterDefinitions");
                    if (defs != null) {
                        for (Map<String, Object> def : defs) {
                            Map<String, Object> normalizedDef = new HashMap<>();
                            normalizedDef.put("name", def.get("name"));
                            normalizedDef.put("type", def.get("type"));
                            normalizedDef.put("description", def.get("description"));

                            Map<String, Object> defaultVal = (Map<String, Object>) def.get("defaultParameterValue");
                            if (defaultVal != null) {
                                normalizedDef.put("defaultValue", defaultVal.get("value"));
                            } else {
                                normalizedDef.put("defaultValue", null);
                            }

                            parameters.add(normalizedDef);
                        }
                    }
                }
            }

            return ResponseEntity.ok(parameters);

        } catch (WebClientResponseException.NotFound e) {
            log.error("Jenkins job not found: {}", jobName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Jenkins job not found. Check the path and profile permissions."));
        } catch (WebClientResponseException e) {
            log.error("Jenkins API error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Jenkins API error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to introspect Jenkins job", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to connect to Jenkins: " + e.getMessage()));
        }
    }

    private WebClient createWebClient(String jenkinsUrl, String username, String token) {
        WebClient.Builder builder = WebClient.builder().baseUrl(jenkinsUrl);

        if (username != null && !username.isBlank() && token != null && !token.isBlank()) {
            String auth = username + ":" + token;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader("Authorization", "Basic " + encodedAuth);
        }

        return builder.build();
    }

    private String toJobPath(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("Jenkins jobName must not be blank");
        }
        String[] parts = jobName.trim().split("/");
        return "/job/" + String.join("/job/", parts);
    }
}
