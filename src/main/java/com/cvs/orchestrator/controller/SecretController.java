package com.cvs.orchestrator.controller;

import com.cvs.orchestrator.service.SecretService;
import com.cvs.orchestrator.service.SecretService.SecretSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the built-in secrets store.
 *
 * Secret values are WRITE-only — they can never be read back via the API.
 * Only names and metadata are exposed in list/get operations.
 *
 * Typical flow:
 * POST /api/secrets { "name": "my-github-token", "value": "ghp_xxx" }
 * GET /api/secrets → [{ "name": "my-github-token", "description": "...", ... }]
 * DELETE /api/secrets/{name}
 *
 * In workflow YAMLs reference secrets as:
 * token: "secret:my-github-token"
 */
@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
public class SecretController {

    private final SecretService secretService;

    /**
     * Create or update a secret. The raw value is encrypted immediately and never
     * stored.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> putSecret(@RequestBody SecretRequest request) {
        secretService.putSecret(request.name(), request.value(), request.description());
        return ResponseEntity.ok(Map.of(
                "name", request.name(),
                "message", "Secret stored successfully",
                "hint", "Reference in YAML with: secret:" + request.name()));
    }

    /** List all secret names and metadata. Values are NEVER returned. */
    @GetMapping
    public ResponseEntity<List<SecretSummary>> listSecrets() {
        return ResponseEntity.ok(secretService.listSecrets());
    }

    /** Delete a secret by name. */
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> deleteSecret(@PathVariable String name) {
        secretService.deleteSecret(name);
        return ResponseEntity.ok(Map.of("message", "Secret '" + name + "' deleted"));
    }

    /** Request body for create/update. */
    public record SecretRequest(String name, String value, String description) {
    }
}
