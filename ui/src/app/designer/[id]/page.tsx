"use client";

import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react';
import {
    ReactFlow,
    Background,
    ReactFlowProvider,
    Node,
    Edge
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useRouter, useParams } from 'next/navigation';
import axios from 'axios';
import { Save, ArrowLeft, Github, Clock, Settings, X, Plus, AlertCircle } from 'lucide-react';
import { SiJenkins } from 'react-icons/si';
import CustomNode from '@/components/CustomNode';
import { WorkflowYaml, buildLayout, parseYaml, stringifyYaml } from '@/utils/workflowParser';
import Link from 'next/link';

const nodeTypes = {
    customStep: CustomNode,
};

function DesignerFlow() {
    const router = useRouter();
    const params = useParams();
    const workflowId = params.id as string;
    const isNew = workflowId === 'new';

    const reactFlowWrapper = useRef<HTMLDivElement>(null);
    const [reactFlowInstance, setReactFlowInstance] = useState<any>(null);
    const [isSaving, setIsSaving] = useState(false);

    // Single source of truth state
    const [workflow, setWorkflow] = useState<WorkflowYaml>({
        id: isNew ? 'new-workflow' : workflowId,
        name: 'New Workflow',
        version: '1.0',
        stages: [{
            id: 'build',
            executionMode: 'SEQUENTIAL',
            steps: []
        }]
    });

    const [selectedStepId, setSelectedStepId] = useState<string | null>(null);

    // Initial load
    useEffect(() => {
        if (!isNew) {
            axios.get('/api/workflows').then(res => {
                const wf = res.data.find((w: any) => w.workflowId === workflowId);
                if (wf && wf.yamlContent) {
                    const parsed = parseYaml(wf.yamlContent);
                    if (parsed) {
                        setWorkflow(parsed);
                    }
                }
            }).catch(err => alert("Failed to load workflow: " + err.message));
        }
    }, [workflowId, isNew]);

    // Derive layout automatically
    const { nodes, edges } = useMemo(() => buildLayout(workflow), [workflow]);

    const onDragOver = useCallback((event: any) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    const onDrop = useCallback(
        (event: any) => {
            event.preventDefault();
            if (!reactFlowInstance) return;

            const type = event.dataTransfer.getData('application/reactflow');
            if (typeof type === 'undefined' || !type) return;

            const position = reactFlowInstance.screenToFlowPosition({
                x: event.clientX,
                y: event.clientY,
            });

            // Find if we dropped on a group (Stage)
            const targetGroup = nodes.find(n => n.type === 'group' &&
                position.x >= n.position.x && position.x <= n.position.x + (n.style?.width as number || 0) &&
                position.y >= n.position.y && position.y <= n.position.y + (n.style?.height as number || 0)
            );

            if (!targetGroup) {
                // Ignore drops outside of a Stage bounding box to enforce strict Layout
                alert("Please drop executors inside a defined Stage box.");
                return;
            }

            const stageId = targetGroup.id.replace('stage-', '');
            const newStepId = `step-${Date.now()}`;

            setWorkflow(prev => {
                const newWorkflow = { ...prev, stages: [...prev.stages] };
                const stageIndex = newWorkflow.stages.findIndex(s => s.id === stageId);
                if (stageIndex >= 0) {
                    newWorkflow.stages[stageIndex] = {
                        ...newWorkflow.stages[stageIndex],
                        steps: [
                            ...newWorkflow.stages[stageIndex].steps,
                            { id: newStepId, type, config: {} }
                        ]
                    };
                }
                return newWorkflow;
            });

            setSelectedStepId(newStepId);
        },
        [reactFlowInstance, nodes]
    );

    const onNodeClick = (_: any, node: Node) => {
        if (node.type !== 'group') {
            setSelectedStepId(node.data.id as string);
        } else {
            // Stage Clicked: Toggle Execution Mode
            const stageIdMatch = (node.data.label as string).match(/Stage: (.*?) \(/);
            const stageId = stageIdMatch ? stageIdMatch[1] : node.id.replace("stage-", "");

            setWorkflow(prev => {
                const newWf = { ...prev, stages: [...prev.stages] };
                const stIdx = newWf.stages.findIndex(s => s.id === stageId);
                if (stIdx >= 0) {
                    const currentMode = newWf.stages[stIdx].executionMode;
                    newWf.stages[stIdx] = {
                        ...newWf.stages[stIdx],
                        executionMode: currentMode === 'SEQUENTIAL' ? 'PARALLEL' : 'SEQUENTIAL'
                    };
                }
                return newWf;
            });
        }
    };

    const updateSelectedStepConfig = (field: string, value: any) => {
        if (selectedStepId === null) return;

        setWorkflow(prev => {
            const newWf = { ...prev, stages: [...prev.stages] };
            for (let i = 0; i < newWf.stages.length; i++) {
                const st = newWf.stages[i];
                const stpIdx = st.steps.findIndex(step => step.id === selectedStepId);
                if (stpIdx >= 0) {
                    const updatedSteps = [...st.steps];

                    if (field === 'id') {
                        updatedSteps[stpIdx] = { ...updatedSteps[stpIdx], id: value };
                    } else if (field.startsWith('config.')) {
                        const configKey = field.split('.')[1];
                        updatedSteps[stpIdx] = {
                            ...updatedSteps[stpIdx],
                            config: { ...updatedSteps[stpIdx].config, [configKey]: value }
                        };
                    }
                    newWf.stages[i] = { ...st, steps: updatedSteps };
                    break;
                }
            }
            return newWf;
        });

        if (field === 'id') {
            setSelectedStepId(value);
        }
    };

    const addStage = () => {
        setWorkflow(prev => ({
            ...prev,
            stages: [
                ...prev.stages,
                { id: `stage-${Date.now()}`, executionMode: 'SEQUENTIAL', steps: [] }
            ]
        }));
    };

    const removeSelectedStep = () => {
        if (selectedStepId === null) return;
        setWorkflow(prev => {
            const newWf = { ...prev, stages: [...prev.stages] };
            for (let i = 0; i < newWf.stages.length; i++) {
                const st = newWf.stages[i];
                const stpIdx = st.steps.findIndex(step => step.id === selectedStepId);
                if (stpIdx >= 0) {
                    const updatedSteps = [...st.steps];
                    updatedSteps.splice(stpIdx, 1);
                    newWf.stages[i] = { ...st, steps: updatedSteps };
                    break;
                }
            }
            return newWf;
        });
        setSelectedStepId(null);
    };

    const saveWorkflow = async () => {
        try {
            setIsSaving(true);
            const yamlContent = stringifyYaml(workflow);
            await axios.post('/api/workflows', yamlContent, { headers: { 'Content-Type': 'text/plain' } });
            alert('Workflow saved successfully!');
            router.push('/');
        } catch (err: any) {
            alert("Failed to save: " + err.message);
        } finally {
            setIsSaving(false);
        }
    };

    // Find active selected step data for property inspector
    let activeStepData = null;
    if (selectedStepId !== null) {
        for (const st of workflow.stages) {
            const step = st.steps.find(s => s.id === selectedStepId);
            if (step) {
                activeStepData = step;
                break;
            }
        }
        if (!activeStepData) {
            // Might have been deleted or id changed externally
            setSelectedStepId(null);
        }
    }

    return (
        <div className="flex h-screen w-full -m-8">
            {/* Sidebar Tools */}
            <div className="w-64 bg-white border-r border-slate-200 p-4 flex flex-col z-10 shadow-sm overflow-y-auto">
                <Link href="/" className="flex items-center gap-2 text-slate-500 hover:text-slate-900 mb-6 font-medium transition-colors">
                    <ArrowLeft size={18} /> Back to List
                </Link>

                <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Metadata</h3>
                <input
                    type="text"
                    value={workflow.id}
                    disabled={!isNew}
                    onChange={e => setWorkflow({ ...workflow, id: e.target.value })}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 mb-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="Workflow ID"
                />
                <input
                    type="text"
                    value={workflow.name}
                    onChange={e => setWorkflow({ ...workflow, name: e.target.value })}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 mb-6 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="Friendly Name"
                />

                <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Executors</h3>

                <div className="p-3 mb-3 bg-blue-50 text-blue-800 text-xs rounded-lg flex items-start gap-2 border border-blue-100">
                    <AlertCircle size={14} className="mt-0.5 flex-shrink-0" />
                    <span>Drag executors directly into a Stage box bounding area on the right to append them automatically!</span>
                </div>

                <div className="flex flex-col gap-3">
                    <div
                        className="border border-slate-200 bg-white p-3 rounded-lg flex items-center gap-3 cursor-grab hover:border-blue-500 hover:shadow-sm transition-all"
                        onDragStart={(e: any) => e.dataTransfer.setData('application/reactflow', 'JENKINS_JOB')}
                        draggable
                    >
                        <div className="bg-blue-100 text-blue-600 p-1.5 rounded-md"><SiJenkins size={18} /></div>
                        <span className="font-medium text-sm text-slate-700">Jenkins Job</span>
                    </div>
                    <div
                        className="border border-slate-200 bg-white p-3 rounded-lg flex items-center gap-3 cursor-grab hover:border-slate-800 hover:shadow-sm transition-all"
                        onDragStart={(e: any) => e.dataTransfer.setData('application/reactflow', 'GITHUB_WORKFLOW')}
                        draggable
                    >
                        <div className="bg-slate-100 text-slate-800 p-1.5 rounded-md"><Github size={18} /></div>
                        <span className="font-medium text-sm text-slate-700">GitHub Action</span>
                    </div>
                    <div
                        className="border border-slate-200 bg-white p-3 rounded-lg flex items-center gap-3 cursor-grab hover:border-amber-500 hover:shadow-sm transition-all"
                        onDragStart={(e: any) => e.dataTransfer.setData('application/reactflow', 'WAIT')}
                        draggable
                    >
                        <div className="bg-amber-100 text-amber-600 p-1.5 rounded-md"><Clock size={18} /></div>
                        <span className="font-medium text-sm text-slate-700">Wait</span>
                    </div>
                </div>

                <div className="p-3 mt-6 mb-3 bg-slate-50 text-slate-600 text-xs rounded-lg flex items-start gap-2 border border-slate-200">
                    <AlertCircle size={14} className="mt-0.5 flex-shrink-0" />
                    <span>Click on a Stage boundary box to toggle it instantly between SEQUENTIAL and PARALLEL layouts.</span>
                </div>

                <button
                    onClick={addStage}
                    className="w-full flex items-center justify-center gap-2 bg-slate-100 hover:bg-slate-200 text-slate-700 py-2 rounded-lg text-sm font-medium transition-colors"
                >
                    <Plus size={16} /> Add Empty Stage
                </button>

                <div className="mt-auto pt-4 border-t border-slate-200 h-[60px] shrink-0">
                    <button
                        onClick={saveWorkflow}
                        disabled={isSaving}
                        className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white p-3 rounded-xl font-bold transition-all disabled:opacity-50"
                    >
                        <Save size={18} />
                        {isSaving ? 'Saving...' : 'Save Workflow'}
                    </button>
                </div>
            </div>

            {/* Main Canvas */}
            <div className="flex-1 h-full relative" ref={reactFlowWrapper}>
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onInit={setReactFlowInstance}
                    onDrop={onDrop}
                    onDragOver={onDragOver}
                    onNodeClick={onNodeClick}
                    nodeTypes={nodeTypes}
                    // Disable standard interactions since it's an auto-layout strict engine now
                    nodesDraggable={false}
                    nodesConnectable={false}
                    elementsSelectable={true}
                    deleteKeyCode={null}
                    fitView
                    className="bg-slate-50"
                >
                    <Background color="#cbd5e1" gap={16} size={2} />
                </ReactFlow>

                {/* Node Property Editor Modal Triggered by Selection */}
                {activeStepData && (
                    <div className="absolute top-4 right-4 w-80 bg-white/95 backdrop-blur-md border border-slate-200 rounded-2xl shadow-xl p-5 animate-in slide-in-from-right-8 z-50">
                        <div className="flex justify-between items-center mb-4">
                            <div className="flex items-center gap-2 text-slate-800">
                                <Settings size={18} />
                                <h3 className="font-bold">Step Properties</h3>
                            </div>
                            <button
                                onClick={() => setSelectedStepId(null)}
                                className="text-slate-400 hover:text-slate-800 transition-colors"
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <label className="text-xs font-semibold text-slate-500 uppercase">Step ID</label>
                                <input
                                    type="text"
                                    value={activeStepData.id || ''}
                                    onChange={e => updateSelectedStepConfig('id', e.target.value)}
                                    className="mt-1 w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
                                />
                            </div>

                            {activeStepData.type === 'JENKINS_JOB' && (
                                <>
                                    <div>
                                        <label className="text-xs font-semibold text-slate-500 uppercase">Jenkins URL</label>
                                        <input type="text" value={(activeStepData.config as any)?.jenkinsUrl || ''} onChange={e => updateSelectedStepConfig('config.jenkinsUrl', e.target.value)} className="mt-1 w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500" placeholder="${JENKINS_URL}" />
                                    </div>
                                    <div>
                                        <label className="text-xs font-semibold text-slate-500 uppercase">Job Name</label>
                                        <input type="text" value={(activeStepData.config as any)?.jobName || ''} onChange={e => updateSelectedStepConfig('config.jobName', e.target.value)} className="mt-1 w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500" />
                                    </div>
                                </>
                            )}

                            {activeStepData.type === 'GITHUB_WORKFLOW' && (
                                <>
                                    <div>
                                        <label className="text-xs font-semibold text-slate-500 uppercase">Repository</label>
                                        <input type="text" value={(activeStepData.config as any)?.repo || ''} onChange={e => updateSelectedStepConfig('config.repo', e.target.value)} className="mt-1 w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500" />
                                    </div>
                                    <div>
                                        <label className="text-xs font-semibold text-slate-500 uppercase">Workflow File</label>
                                        <input type="text" value={(activeStepData.config as any)?.workflow || ''} onChange={e => updateSelectedStepConfig('config.workflow', e.target.value)} className="mt-1 w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500" />
                                    </div>
                                </>
                            )}

                            {activeStepData.type === 'WAIT' && (
                                <div>
                                    <label className="text-xs font-semibold text-slate-500 uppercase">Wait Time (Seconds)</label>
                                    <input type="number" value={(activeStepData.config as any)?.durationSeconds || (activeStepData.config as any)?.waitTimeSeconds || 10} onChange={e => updateSelectedStepConfig('config.durationSeconds', parseInt(e.target.value))} className="mt-1 w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500" />
                                </div>
                            )}

                            <button onClick={removeSelectedStep} className="w-full mt-4 bg-red-50 text-red-600 hover:bg-red-100 font-medium py-2 rounded-lg text-sm transition-colors">
                                Remove Step
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

export default function DesignerPage() {
    return (
        <ReactFlowProvider>
            <DesignerFlow />
        </ReactFlowProvider>
    );
}
