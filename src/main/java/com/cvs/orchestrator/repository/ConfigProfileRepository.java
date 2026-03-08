package com.cvs.orchestrator.repository;

import com.cvs.orchestrator.model.ConfigProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfigProfileRepository extends JpaRepository<ConfigProfileEntity, UUID> {

    Optional<ConfigProfileEntity> findByName(String name);

    List<ConfigProfileEntity> findByProfileType(String profileType);

    void deleteByName(String name);
}
