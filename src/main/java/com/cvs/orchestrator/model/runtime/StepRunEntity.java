package com.cvs.orchestrator.model.runtime;

import com.cvs.orchestrator.model.definition.StepDefinitionEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "step_run")
public class StepRunEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_run_id", nullable = false)
    @JsonBackReference("stagerun-stepruns")
    private StageRunEntity stageRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_def_id", nullable = false)
    private StepDefinitionEntity stepDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "attempt_count")
    private int attemptCount = 0;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json")
    private Map<String, Object> metadata;

    @Column(name = "logs_text", columnDefinition = "TEXT")
    private String logs;
}
