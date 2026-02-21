package com.cvs.orchestrator.model.definition;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "step_definition")
public class StepDefinitionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_def_id", nullable = false)
    @JsonBackReference("stage-steps")
    private StageDefinitionEntity stageDefinition;

    @Column(nullable = false)
    private String stepId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "executor_type", nullable = false)
    private String executorType; // JENKINS_JOB, GITHUB_WORKFLOW

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false)
    private Map<String, Object> config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy_json")
    private RetryPolicy retryPolicy;
}
