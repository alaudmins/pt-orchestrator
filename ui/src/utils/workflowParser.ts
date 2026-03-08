import yaml from "js-yaml";
import { Node, Edge } from "@xyflow/react";

export interface WorkflowYaml {
    id: string;
    version: string;
    name: string;
    stages: StageYaml[];
}

export interface StageYaml {
    id: string;
    executionMode: "SEQUENTIAL" | "PARALLEL";
    steps: StepYaml[];
}

export interface StepYaml {
    id: string;
    type: string;
    config?: any;
}

const STEP_WIDTH = 250;
const STEP_HEIGHT = 80;
const X_PADDING = 40;
const Y_PADDING = 60;
const GROUP_Y_SPACING = 50;

export const parseYaml = (yamlContent: string): WorkflowYaml | null => {
    try {
        return yaml.load(yamlContent) as WorkflowYaml;
    } catch {
        return null;
    }
}

export const stringifyYaml = (workflow: WorkflowYaml): string => {
    return yaml.dump(workflow, { indent: 2, noRefs: true });
}

export const buildLayout = (workflow: WorkflowYaml): { nodes: Node[]; edges: Edge[] } => {
    const nodes: Node[] = [];
    const edges: Edge[] = [];

    if (!workflow || !workflow.stages) return { nodes, edges };

    let currentY = 50;

    workflow.stages.forEach((stage, stageIndex) => {
        const stageNodeId = `stage-${stage.id}`;

        // 1. Calculate bounding box sizes and inner layouts
        let stageWidth = STEP_WIDTH + (X_PADDING * 2);
        let stageHeight = STEP_HEIGHT + (Y_PADDING * 2);

        if (stage.executionMode === "PARALLEL") {
            const numSteps = Math.max(1, stage.steps.length);
            stageWidth = (numSteps * STEP_WIDTH) + ((numSteps + 1) * X_PADDING);
            stageHeight = STEP_HEIGHT + (Y_PADDING * 2);
        } else {
            // SEQUENTIAL
            const numSteps = Math.max(1, stage.steps.length);
            stageWidth = STEP_WIDTH + (X_PADDING * 2);
            stageHeight = (numSteps * STEP_HEIGHT) + ((numSteps + 1) * Y_PADDING);
        }

        // Add the group wrapper
        nodes.push({
            id: stageNodeId,
            type: "group",
            position: { x: (typeof window !== 'undefined' ? window.innerWidth : 1200) / 2 - stageWidth / 2 - 130, y: currentY }, // Roughly center
            draggable: false, // STYLED: no free-dragging for structured layout
            style: {
                width: stageWidth,
                height: stageHeight,
                backgroundColor: 'rgba(241, 245, 249, 0.4)',
                border: stage.executionMode === 'PARALLEL' ? '2px dashed #94a3b8' : '1px solid #cbd5e1',
                borderRadius: '12px'
            },
            data: { label: `Stage: ${stage.id} (${stage.executionMode})` },
        });

        // 2. Connect to the previous stage
        if (stageIndex > 0) {
            const prevStage = workflow.stages[stageIndex - 1];
            edges.push({
                id: `e-stage-${prevStage.id}-to-${stage.id}`,
                source: `stage-${prevStage.id}`,
                target: stageNodeId,
                type: 'smoothstep',
                animated: true,
                style: { stroke: '#94a3b8', strokeWidth: 2 }
            });
        }

        // 3. Position the inner steps
        stage.steps.forEach((step, stepIndex) => {
            const stepNodeId = `step-${step.id}`;

            let stepX = X_PADDING;
            let stepY = Y_PADDING;

            if (stage.executionMode === "PARALLEL") {
                stepX = X_PADDING + (stepIndex * (STEP_WIDTH + X_PADDING));
            } else {
                stepY = Y_PADDING + (stepIndex * (STEP_HEIGHT + Y_PADDING));
            }

            nodes.push({
                id: stepNodeId,
                type: "customStep",
                position: { x: stepX, y: stepY },
                parentId: stageNodeId,
                extent: "parent",
                draggable: false, // Prevent breaking out of the box
                data: {
                    id: step.id,
                    type: step.type,
                    config: step.config || {},
                },
            });

            // If sequential, draw a connection from the previous step
            if (stage.executionMode === "SEQUENTIAL" && stepIndex > 0) {
                const prevStep = stage.steps[stepIndex - 1];
                edges.push({
                    id: `e-step-${prevStep.id}-to-${step.id}`,
                    source: `step-${prevStep.id}`,
                    target: stepNodeId,
                    type: 'smoothstep',
                    style: { stroke: '#3b82f6', strokeWidth: 1.5 }
                });
            }
        });

        currentY += stageHeight + GROUP_Y_SPACING;
    });

    return { nodes, edges };
};
