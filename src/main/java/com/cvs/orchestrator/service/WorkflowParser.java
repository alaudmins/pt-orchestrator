package com.cvs.orchestrator.service;

import com.cvs.orchestrator.model.definition.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkflowParser {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public WorkflowDefinition parse(String yamlContent) {
        try {
            return yamlMapper.readValue(yamlContent, WorkflowDefinition.class);
        } catch (Exception e) {
            log.error("Failed to parse workflow YAML", e);
            throw new IllegalArgumentException("Invalid workflow YAML", e);
        }
    }
}
