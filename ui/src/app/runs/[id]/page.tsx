"use client";

import { useEffect, useState, use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import axios from "axios";
import { formatDistanceToNow } from "date-fns";
import { ArrowLeft, PlayCircle, CheckCircle, XCircle, Clock, SquareSquare, Layers, AlertTriangle, StopCircle } from "lucide-react";
import clsx from "clsx";
import { WorkflowRunDetail, StageRun, StepRun } from "@/types/workflow";

const StatusBadge = ({ status }: { status: string }) => {
    let colorClass = "bg-slate-100 text-slate-600";
    let Icon = Clock;

    if (status === "SUCCESS") {
        colorClass = "bg-green-100 text-green-700 border-green-200";
        Icon = CheckCircle;
    } else if (status === "FAILED") {
        colorClass = "bg-red-100 text-red-700 border-red-200";
        Icon = XCircle;
    } else if (status === "RUNNING") {
        colorClass = "bg-blue-100 text-blue-700 border-blue-200";
        Icon = PlayCircle;
    } else if (status === "PENDING") {
        colorClass = "bg-amber-100 text-amber-700 border-amber-200";
        Icon = Clock;
    }

    return (
        <span className={clsx("inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold border", colorClass)}>
            <Icon size={14} className={status === "RUNNING" ? "animate-pulse" : ""} />
            {status}
        </span>
    );
};

export default function RunDetailsPage(props: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const params = use(props.params);
    const runId = params.id;
    const [run, setRun] = useState<WorkflowRunDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [aborting, setAborting] = useState(false);

    useEffect(() => {
        const fetchRunDetails = async () => {
            try {
                const res = await axios.get(`/api/runs/${runId}`);
                setRun(res.data);
            } catch (err) {
                console.error(err);
            } finally {
                setLoading(false);
            }
        };

        fetchRunDetails();

        // Poll every 2.5s if still running or pending
        const interval = setInterval(() => {
            if (run?.status === "RUNNING" || run?.status === "PENDING" || !run) {
                fetchRunDetails();
            }
        }, 2500);

        return () => clearInterval(interval);
    }, [runId, run?.status]);

    const handleAbort = async () => {
        if (!confirm("Are you sure you want to abort this run?")) return;
        try {
            setAborting(true);
            await axios.post(`/api/runs/${runId}/abort`);
            // It will naturally update on next poll
        } catch (err: any) {
            alert("Failed to abort run: " + err.message);
        } finally {
            setAborting(false);
        }
    };

    if (loading && !run) {
        return (
            <div className="py-8 animate-pulse">
                <div className="h-8 bg-slate-200 rounded w-1/3 mb-4"></div>
                <div className="h-4 bg-slate-100 rounded w-1/4 mb-12"></div>
                <div className="h-64 bg-slate-100 rounded-xl"></div>
            </div>
        );
    }

    if (!run) {
        return (
            <div className="py-8 text-center text-red-500 font-medium">
                Failed to load run details.
            </div>
        );
    }

    const isComplete = run.status === "SUCCESS" || run.status === "FAILED";

    return (
        <div className="py-8 pb-20">
            <Link href="/runs" className="inline-flex items-center gap-2 text-slate-500 hover:text-blue-600 mb-6 font-medium transition-colors">
                <ArrowLeft size={18} /> Back to Runs
            </Link>

            <div className="glass-card rounded-2xl p-6 md:p-8 mb-8 flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
                <div>
                    <div className="flex items-center gap-3 mb-2">
                        <h1 className="text-3xl font-extrabold text-slate-900 tracking-tight">Run {runId.substring(0, 8)}</h1>
                        <StatusBadge status={run.status} />
                    </div>
                    <p className="text-slate-500 font-medium">
                        Workflow: <Link href={`/designer/${run.workflowId}`} className="text-blue-600 hover:underline">{run.workflowName || run.workflowId}</Link>
                    </p>
                    <div className="flex gap-6 mt-4 text-sm text-slate-500">
                        <div><strong className="text-slate-700">Started:</strong> {new Date(run.startTime).toLocaleString()}</div>
                        {run.endTime && <div><strong className="text-slate-700">Ended:</strong> {new Date(run.endTime).toLocaleString()}</div>}
                    </div>
                </div>

                {!isComplete && (
                    <button
                        onClick={handleAbort}
                        disabled={aborting}
                        className="bg-red-50 text-red-600 border border-red-200 hover:bg-red-600 hover:text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-bold shadow-sm transition-all disabled:opacity-50"
                    >
                        <StopCircle size={20} />
                        {aborting ? "Aborting..." : "Abort Run"}
                    </button>
                )}
            </div>

            <div className="space-y-8 relative before:absolute before:inset-0 before:ml-[23px] before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-slate-200 before:to-transparent">
                {run.stages?.sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()).map((stage, sIdx) => (
                    <div key={stage.stageId} className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active">

                        <div className="flex items-center justify-center w-12 h-12 rounded-full border-4 border-white bg-white shadow shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 z-10">
                            {stage.status === 'SUCCESS' ? <CheckCircle className="text-green-500" /> :
                                stage.status === 'FAILED' ? <XCircle className="text-red-500" /> :
                                    stage.status === 'RUNNING' ? <PlayCircle className="text-blue-500 animate-spin-slow" /> :
                                        <Clock className="text-slate-300" />}
                        </div>

                        <div className="w-[calc(100%-4rem)] md:w-[calc(50%-3rem)] glass-card p-6 rounded-2xl shadow-sm border border-slate-200 ml-4 md:ml-0 overflow-hidden relative">
                            <div className="absolute top-0 left-0 w-1 h-full" style={{ backgroundColor: stage.status === 'SUCCESS' ? '#22c55e' : stage.status === 'FAILED' ? '#ef4444' : stage.status === 'RUNNING' ? '#3b82f6' : '#cbd5e1' }}></div>

                            <div className="flex justify-between items-start mb-4">
                                <div>
                                    <h3 className="font-bold text-slate-800 text-lg flex items-center gap-2">
                                        <Layers size={18} className="text-slate-400" />
                                        Stage: {stage.stageDefId}
                                    </h3>
                                    <span className="text-xs text-slate-400 font-mono mt-1 block">ID: {stage.stageId}</span>
                                </div>
                                <StatusBadge status={stage.status} />
                            </div>

                            <div className="space-y-4 mt-6">
                                {stage.steps?.sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()).map((step, stepIdx) => (
                                    <div key={step.stepId} className="bg-slate-50 border border-slate-100 rounded-xl p-4 transition-all hover:bg-white hover:shadow-md hover:border-slate-200">
                                        <div className="flex justify-between items-center mb-2">
                                            <h4 className="font-bold text-slate-800 text-sm flex items-center gap-2">
                                                <SquareSquare size={14} className="text-blue-500" />
                                                Step: {step.stepDefId}
                                            </h4>
                                            <div className="flex items-center gap-2">
                                                <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-200 text-slate-600">
                                                    {step.executorType}
                                                </span>
                                                <StatusBadge status={step.status} />
                                            </div>
                                        </div>

                                        <div className="flex justify-between text-xs text-slate-500 font-medium">
                                            <span>Attempts: {step.attemptCount}</span>
                                            <span>
                                                {step.endTime ? `${Math.round((new Date(step.endTime).getTime() - new Date(step.startTime).getTime()) / 1000)}s` : step.status === 'RUNNING' ? 'Running...' : 'Pending...'}
                                            </span>
                                        </div>

                                        {step.logs && (
                                            <div className="mt-3 bg-slate-900 border border-slate-800 rounded-lg p-3 text-xs font-mono text-green-400 overflow-x-auto whitespace-pre-wrap max-h-40 overflow-y-auto">
                                                {step.logs}
                                            </div>
                                        )}
                                    </div>
                                ))}

                                {(!stage.steps || stage.steps.length === 0) && (
                                    <div className="text-sm text-slate-400 italic text-center py-4">No steps executed yet.</div>
                                )}
                            </div>

                        </div>
                    </div>
                ))}
                {(!run.stages || run.stages.length === 0) && (
                    <div className="text-center py-12 text-slate-500 font-medium">
                        No stages have started execution yet.
                    </div>
                )}
            </div>
        </div>
    );
}
