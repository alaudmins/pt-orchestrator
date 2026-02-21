package com.cvs.orchestrator.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores named secrets encrypted with AES-256-GCM.
 * The raw value is NEVER stored — only the encrypted ciphertext.
 */
@Data
@Entity
@Table(name = "orchestrator_secret", uniqueConstraints = @UniqueConstraint(name = "uk_secret_name", columnNames = "name"))
public class SecretEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Logical name used in workflow YAMLs: secret:my-github-token */
    @Column(nullable = false, unique = true, length = 128)
    private String name;

    /** AES-256-GCM ciphertext (base64-encoded). Raw value is never stored. */
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    /** Optional human-readable description of what this secret is for. */
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
