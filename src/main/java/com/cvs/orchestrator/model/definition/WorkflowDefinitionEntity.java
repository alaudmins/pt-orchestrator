package com.cvs.orchestrator.model.definition;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinitionEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(nullable = false)
    private String workflowId; // Logical ID from YAML

    @Column(nullable = false)
    private String version;

    private String name;

    @Column(name = "yaml_content", columnDefinition = "TEXT")
    private String yamlContent;

    @OneToMany(mappedBy = "workflowDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    @JsonManagedReference("workflow-stages")
    private List<StageDefinitionEntity> stages = new ArrayList<>();

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
