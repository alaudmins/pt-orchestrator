package com.cvs.orchestrator.repository;

import com.cvs.orchestrator.model.runtime.WorkflowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, UUID> {
    List<WorkflowRunEntity> findAllByOrderByStartTimeDesc();
}
