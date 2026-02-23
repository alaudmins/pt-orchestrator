package com.cvs.orchestrator.service;

import com.cvs.orchestrator.model.SecretEntity;
import com.cvs.orchestrator.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;

    /**
     * Create or update a named secret. Returns the stored entity (value not
     * exposed).
     */
    @Transactional
    public SecretEntity putSecret(String name, String rawValue, String description) {
        validateName(name);

        SecretEntity entity = secretRepository.findByName(name)
                .orElseGet(SecretEntity::new);

        entity.setName(name);
        entity.setEncryptedValue(encryptionService.encrypt(rawValue));
        entity.setDescription(description);

        SecretEntity saved = secretRepository.save(entity);
        log.info("Secret '{}' {}", name, entity.getId() == null ? "created" : "updated");
        return saved;
    }

    /** Resolve a secret name to its decrypted raw value. Used by EnvVarResolver. */
    @Transactional(readOnly = true)
    public String getSecretValue(String name) {
        SecretEntity entity = secretRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Secret '" + name + "' not found. " +
                                "Register it with: POST /api/secrets {\"name\":\"" + name + "\",\"value\":\"...\"}"));
        try {
            return encryptionService.decrypt(entity.getEncryptedValue());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt secret '" + name + "'. " +
                            "This usually means SECRETS_ENCRYPTION_KEY changed between restarts " +
                            "(e.g. no .env file on Windows → new ephemeral key each start). " +
                            "Fix: (1) set a stable SECRETS_ENCRYPTION_KEY in your .env file, " +
                            "(2) delete and re-store this secret via DELETE /api/secrets/" + name +
                            " then POST /api/secrets. " +
                            "Root cause: " + e.getMessage(),
                    e);
        }
    }

    /** List all secret names + descriptions (values never returned). */
    @Transactional(readOnly = true)
    public List<SecretSummary> listSecrets() {
        return secretRepository.findAll().stream()
                .map(e -> new SecretSummary(e.getName(), e.getDescription(),
                        e.getCreatedAt(), e.getUpdatedAt()))
                .toList();
    }

    /** Delete a secret by name. */
    @Transactional
    public void deleteSecret(String name) {
        if (!secretRepository.existsByName(name)) {
            throw new IllegalArgumentException("Secret '" + name + "' not found");
        }
        secretRepository.deleteByName(name);
        log.info("Secret '{}' deleted", name);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Secret name must not be blank");
        }
        if (!name.matches("[a-zA-Z0-9_\\-\\.]+")) {
            throw new IllegalArgumentException(
                    "Secret name '" + name + "' contains invalid characters. " +
                            "Use only letters, digits, hyphens, underscores, or dots.");
        }
    }

    /** Safe view of a secret (name + metadata, never the value). */
    public record SecretSummary(String name, String description,
            java.time.Instant createdAt, java.time.Instant updatedAt) {
    }
}
