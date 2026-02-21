package com.cvs.orchestrator.model.definition;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "stage_definition")
public class StageDefinitionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_def_id", nullable = false)
    @JsonBackReference("workflow-stages")
    private WorkflowDefinitionEntity workflowDefinition;

    @Column(nullable = false)
    private String stageId;

    @Column(name = "stage_order", nullable = false)
    private int stageOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode")
    private ExecutionMode executionMode = ExecutionMode.SEQUENTIAL;

    @OneToMany(mappedBy = "stageDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    @JsonManagedReference("stage-steps")
    private List<StepDefinitionEntity> steps = new ArrayList<>();
}
