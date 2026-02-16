package com.cvs.orchestrator.repository;

import com.cvs.orchestrator.model.runtime.StageRunEntity;
import com.cvs.orchestrator.model.runtime.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageRunRepository extends JpaRepository<StageRunEntity, UUID> {
    List<StageRunEntity> findByStatus(Status status);
}
