package com.cvs.orchestrator.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores named integration profiles (e.g. Jenkins Prod, Github CI)
 * that point to specific endpoints and inherently reference a stored Secret.
 */
@Data
@Entity
@Table(name = "config_profile", uniqueConstraints = @UniqueConstraint(name = "uk_config_profile_name", columnNames = "name"))
public class ConfigProfileEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "profile_type", nullable = false, length = 64)
    private String profileType; // JENKINS, GITHUB, etc.

    @Column(nullable = false)
    private String url;

    @Column(length = 255)
    private String username;

    /** Reference to a SecretEntity name to retrieve tokens securely at runtime */
    @Column(name = "secret_reference", length = 128)
    private String secretReference;

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
