package com.cvs.orchestrator.engine;

import com.cvs.orchestrator.model.runtime.StepRunEntity;
import com.cvs.orchestrator.repository.StepRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads step run status in a brand-new transaction every call.
 *
 * Why this exists:
 * WorkflowEngine.startWorkflow() runs in a long-lived @Transactional session.
 * JPA's L1 (first-level) cache lives for the duration of that session, so
 * repeated stepRunRepository.findById() calls within the same transaction
 * return the *cached* entity — not the row that TaskPoller just committed.
 *
 * REQUIRES_NEW suspends the caller's transaction, opens a fresh
 * EntityManager (empty L1 cache), reads the current DB row, then closes.
 * The engine's outer transaction resumes afterwards.
 */
@Component
@RequiredArgsConstructor
public class StepStatusReader {

    private final StepRunRepository stepRunRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public StepRunEntity getFreshStep(UUID stepRunId) {
        return stepRunRepository.findById(stepRunId)
                .orElseThrow(() -> new RuntimeException("Step run not found: " + stepRunId));
    }
}
