package com.cvs.orchestrator.service;

import com.cvs.orchestrator.model.ConfigProfileEntity;
import com.cvs.orchestrator.repository.ConfigProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigProfileService {

    private final ConfigProfileRepository configProfileRepository;

    public record ProfileSummary(UUID id, String name, String profileType, String url, String username,
            String secretReference, Instant updatedAt) {
    }

    @Transactional
    public ConfigProfileEntity saveProfile(String id, String name, String profileType, String url, String username,
            String secretReference) {
        log.info("Saving ConfigProfile '{}' of type {}", name, profileType);
        ConfigProfileEntity entity;

        if (id != null && !id.isBlank()) {
            entity = configProfileRepository.findById(UUID.fromString(id))
                    .orElse(new ConfigProfileEntity());
            if (entity.getId() == null) {
                // If ID was provided but not found, it's a new entity that shouldn't happen via
                // PUT, but safe to handle
            }
        } else {
            // Check by name just in case
            entity = configProfileRepository.findByName(name).orElse(new ConfigProfileEntity());
        }

        entity.setName(name);
        entity.setProfileType(profileType.toUpperCase());
        entity.setUrl(url);
        entity.setUsername(username);
        entity.setSecretReference(secretReference);

        return configProfileRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ProfileSummary> listProfiles(String typeFilter) {
        List<ConfigProfileEntity> entities;
        if (typeFilter != null && !typeFilter.isBlank()) {
            entities = configProfileRepository.findByProfileType(typeFilter.toUpperCase());
        } else {
            entities = configProfileRepository.findAll();
        }

        return entities.stream().map(e -> new ProfileSummary(
                e.getId(),
                e.getName(),
                e.getProfileType(),
                e.getUrl(),
                e.getUsername(),
                e.getSecretReference(),
                e.getUpdatedAt())).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ConfigProfileEntity> getProfile(UUID id) {
        return configProfileRepository.findById(id);
    }

    @Transactional
    public void deleteProfile(UUID id) {
        log.info("Deleting ConfigProfile with ID {}", id);
        configProfileRepository.deleteById(id);
    }
}
