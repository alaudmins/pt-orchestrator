package com.cvs.orchestrator.repository;

import com.cvs.orchestrator.model.runtime.StepRunEntity;
import com.cvs.orchestrator.model.runtime.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepRunRepository extends JpaRepository<StepRunEntity, UUID> {
    List<StepRunEntity> findByStatus(Status status);
}
