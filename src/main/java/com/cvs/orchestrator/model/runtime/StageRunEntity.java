package com.cvs.orchestrator.model.runtime;

import com.cvs.orchestrator.model.definition.StageDefinitionEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "stage_run")
public class StageRunEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    @JsonBackReference("workflowrun-stageruns")
    private WorkflowRunEntity workflowRun;

    @OneToMany(mappedBy = "stageRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("stagerun-stepruns")
    private List<StepRunEntity> stepRuns = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_def_id", nullable = false)
    private StageDefinitionEntity stageDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;
}
