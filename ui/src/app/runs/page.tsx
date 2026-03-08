"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import axios from "axios";
import { formatDistanceToNow } from "date-fns";
import { Copy, Clock, PlayCircle, CheckCircle, XCircle, AlertCircle } from "lucide-react";
import clsx from "clsx";

interface Run {
    runId: string;
    workflowId: string;
    status: string;
    startTime: string;
    endTime?: string;
}

export default function RunList() {
    const [runs, setRuns] = useState<Run[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchRuns = async () => {
            try {
                const res = await axios.get("/api/runs");
                setRuns(res.data);
            } catch (err) {
                console.error(err);
            } finally {
                setLoading(false);
            }
        };
        fetchRuns();
        const interval = setInterval(fetchRuns, 5000);
        return () => clearInterval(interval);
    }, []);

    const StatusIcon = ({ status }: { status: string }) => {
        switch (status) {
            case "SUCCESS": return <CheckCircle className="text-green-500" size={18} />;
            case "FAILED": return <XCircle className="text-red-500" size={18} />;
            case "RUNNING": return <PlayCircle className="text-blue-500 animate-pulse" size={18} />;
            default: return <Clock className="text-slate-400" size={18} />;
        }
    };

    const getStatusBg = (status: string) => {
        switch (status) {
            case "SUCCESS": return "bg-green-50 border-green-200 text-green-700";
            case "FAILED": return "bg-red-50 border-red-200 text-red-700";
            case "RUNNING": return "bg-blue-50 border-blue-200 text-blue-700";
            default: return "bg-slate-50 border-slate-200 text-slate-700";
        }
    };

    return (
        <div className="py-8">
            <div className="mb-8">
                <h1 className="text-3xl font-extrabold text-slate-800 tracking-tight">Execution Runs</h1>
                <p className="text-slate-500 mt-1">Live overview of recent orchestrator activity.</p>
            </div>

            {loading && runs.length === 0 ? (
                <div className="space-y-4">
                    {[1, 2, 3].map(i => (
                        <div key={i} className="h-20 bg-white/50 animate-pulse rounded-xl border border-slate-200"></div>
                    ))}
                </div>
            ) : runs.length === 0 ? (
                <div className="glass-card rounded-2xl p-12 text-center border-dashed border-2 border-slate-300">
                    <AlertCircle className="mx-auto text-slate-400 mb-4" size={32} />
                    <h3 className="text-lg font-bold text-slate-700">No runs found</h3>
                    <p className="text-slate-500">Trigger a workflow to see its execution history here.</p>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-slate-50 border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500 font-semibold">
                                <th className="px-6 py-4">Status</th>
                                <th className="px-6 py-4">Workflow</th>
                                <th className="px-6 py-4">Run ID</th>
                                <th className="px-6 py-4">Started</th>
                                <th className="px-6 py-4">Duration</th>
                                <th className="px-6 py-4 text-right">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {runs.map((run) => {
                                const start = new Date(run.startTime);
                                const end = run.endTime ? new Date(run.endTime) : new Date();
                                const duration = Math.round((end.getTime() - start.getTime()) / 1000);

                                return (
                                    <tr key={run.runId} className="hover:bg-slate-50 transition-colors group">
                                        <td className="px-6 py-4">
                                            <div className={clsx("inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold border", getStatusBg(run.status))}>
                                                <StatusIcon status={run.status} />
                                                {run.status}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 font-semibold text-slate-800">
                                            {run.workflowId}
                                        </td>
                                        <td className="px-6 py-4 font-mono text-xs text-slate-500 flex items-center gap-2">
                                            {run.runId.substring(0, 8)}...
                                            <button className="hover:text-slate-800 opacity-0 group-hover:opacity-100 transition-opacity" onClick={() => navigator.clipboard.writeText(run.runId)}>
                                                <Copy size={12} />
                                            </button>
                                        </td>
                                        <td className="px-6 py-4 text-slate-600 text-sm">
                                            {formatDistanceToNow(start, { addSuffix: true })}
                                        </td>
                                        <td className="px-6 py-4 text-slate-600 text-sm font-mono">
                                            {duration}s
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <Link
                                                href={`/runs/${run.runId}`}
                                                className="bg-white border border-slate-200 hover:border-blue-400 hover:text-blue-600 text-slate-700 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors shadow-sm"
                                            >
                                                View Details
                                            </Link>
                                        </td>
                                    </tr>
                                )
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
