"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { WorkflowDefinition } from "@/types/workflow";
import { Play, FileEdit, Trash2, Plus, AlertCircle, CheckCircle2, Workflow } from "lucide-react";
import axios from "axios";

export default function WorkflowList() {
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notification, setNotification] = useState<{ message: string, type: 'success' | 'error' } | null>(null);

  const fetchWorkflows = async () => {
    try {
      setLoading(true);
      const res = await axios.get("/api/workflows");
      setWorkflows(res.data);
      setError("");
    } catch (err: any) {
      setError(err.message || "Failed to fetch workflows");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWorkflows();
  }, []);

  const showNotification = (message: string, type: 'success' | 'error') => {
    setNotification({ message, type });
    setTimeout(() => setNotification(null), 3000);
  };

  const handleRun = async (workflowId: string) => {
    try {
      const res = await axios.post(`/api/workflows/${workflowId}/run`);
      showNotification(`Workflow run started! Run ID: ${res.data.runId}`, 'success');
      // Could also route to the run details page here natively
    } catch (err: any) {
      showNotification(`Failed to run workflow: ${err.message}`, 'error');
    }
  };

  const handleDelete = async (workflowId: string) => {
    if (!confirm("Are you sure you want to delete this workflow and all its runs?")) return;
    try {
      await axios.delete(`/api/workflows/${workflowId}`);
      showNotification(`Workflow ${workflowId} deleted.`, 'success');
      fetchWorkflows();
    } catch (err: any) {
      showNotification(`Failed to delete workflow: ${err.message}`, 'error');
    }
  };

  return (
    <div className="py-8">
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-extrabold text-slate-800 tracking-tight">Workflows</h1>
          <p className="text-slate-500 mt-1">Manage and execute your registered orchestrations.</p>
        </div>
        <Link
          href="/designer/new"
          className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-lg flex items-center gap-2 font-medium shadow-sm hover:shadow transition-all"
        >
          <Plus size={20} />
          Create New Workflow
        </Link>
      </div>

      {notification && (
        <div className={`mb-6 p-4 rounded-lg flex items-center gap-3 animate-in fade-in slide-in-from-top-4 ${notification.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'
          }`}>
          {notification.type === 'success' ? <CheckCircle2 className="text-green-500" /> : <AlertCircle className="text-red-500" />}
          <span className="font-medium">{notification.message}</span>
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map(i => (
            <div key={i} className="glass-card rounded-xl p-6 h-48 animate-pulse flex flex-col justify-between">
              <div className="space-y-3">
                <div className="h-6 bg-slate-200 rounded w-2/3"></div>
                <div className="h-4 bg-slate-100 rounded w-1/3"></div>
              </div>
              <div className="flex gap-2">
                <div className="h-9 bg-slate-100 rounded flex-1"></div>
                <div className="h-9 bg-slate-100 rounded w-12"></div>
              </div>
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 text-red-700 p-6 rounded-xl flex items-start gap-4">
          <AlertCircle className="mt-1 flex-shrink-0" />
          <div>
            <h3 className="font-semibold text-lg">Error loading workflows</h3>
            <p className="mt-1 opacity-90">{error}</p>
            <button onClick={fetchWorkflows} className="mt-4 text-sm font-medium underline underline-offset-2">Try again</button>
          </div>
        </div>
      ) : workflows.length === 0 ? (
        <div className="glass-card rounded-2xl p-12 text-center border-dashed border-2 border-slate-300">
          <div className="w-16 h-16 bg-blue-50 text-blue-500 rounded-full flex justify-center items-center mx-auto mb-4">
            <Workflow size={32} />
          </div>
          <h3 className="text-xl font-bold text-slate-800 mb-2">No workflows found</h3>
          <p className="text-slate-500 mb-6 max-w-md mx-auto">Get started by building a new workflow or registering a YAML definition to see it appear here.</p>
          <Link href="/designer/new" className="inline-flex items-center gap-2 bg-slate-800 hover:bg-slate-900 text-white px-6 py-3 rounded-lg font-medium transition-colors">
            <Plus size={20} />
            Create Your First Workflow
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {workflows.map(wf => (
            <div key={wf.workflowId} className="glass-card rounded-2xl p-6 flex flex-col group border border-slate-200">
              <div className="flex-1">
                <div className="flex justify-between items-start mb-4">
                  <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center text-blue-600 mb-2">
                    <Workflow size={24} />
                  </div>
                  <span className="text-xs font-semibold px-2.5 py-1 bg-slate-100 text-slate-600 rounded-full">
                    v{wf.version || '1.0'}
                  </span>
                </div>
                <h3 className="text-lg font-bold text-slate-900 group-hover:text-blue-600 transition-colors line-clamp-1" title={wf.name || wf.workflowId}>
                  {wf.name || wf.workflowId}
                </h3>
                <p className="text-sm text-slate-500 mt-1 font-mono text-xs text-slate-400">ID: {wf.workflowId}</p>
              </div>

              <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-4 gap-2">
                <button
                  onClick={() => handleRun(wf.workflowId)}
                  className="flex-1 flex justify-center items-center gap-2 bg-green-50 hover:bg-green-100 text-green-700 py-2 px-3 rounded-lg font-medium transition-colors text-sm"
                >
                  <Play size={16} fill="currentColor" />
                  Run
                </button>
                <Link
                  href={`/designer/${wf.workflowId}`}
                  className="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                  title="Edit Workflow"
                >
                  <FileEdit size={18} />
                </Link>
                <button
                  onClick={() => handleDelete(wf.workflowId)}
                  className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  title="Delete Workflow"
                >
                  <Trash2 size={18} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
