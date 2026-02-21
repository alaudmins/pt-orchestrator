package com.cvs.orchestrator.repository;

import com.cvs.orchestrator.model.SecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SecretRepository extends JpaRepository<SecretEntity, UUID> {
    Optional<SecretEntity> findByName(String name);

    boolean existsByName(String name);

    void deleteByName(String name);
}
