import { Handle, Position } from '@xyflow/react';
import { Activity, Github, Clock, Settings } from 'lucide-react';
import { SiJenkins } from 'react-icons/si';
import clsx from 'clsx';

export default function CustomNode({ data }: any) {
    const isJenkins = data.type === 'JENKINS_JOB';
    const isGithub = data.type === 'GITHUB_WORKFLOW';
    const isWait = data.type === 'WAIT';

    return (
        <div
            title={data.id}
            className={clsx(
                "px-4 py-3 shadow-md rounded-xl bg-white border-2 w-[250px] overflow-hidden transition-all",
                isJenkins ? "border-blue-400" : isGithub ? "border-slate-800" : "border-amber-400"
            )}>
            <Handle type="target" position={Position.Top} className="w-2 h-2" />

            <div className="flex items-center gap-3 w-full">
                <div className={clsx(
                    "w-10 h-10 rounded-lg flex-shrink-0 flex items-center justify-center text-white",
                    isJenkins ? "bg-blue-600" : isGithub ? "bg-slate-800" : "bg-amber-500"
                )}>
                    {isJenkins && <SiJenkins size={22} />}
                    {isGithub && <Github size={20} />}
                    {isWait && <Clock size={20} />}
                </div>

                <div className="flex flex-col flex-1 min-w-0">
                    <span className="font-bold text-slate-800 text-sm truncate block" title={data.id}>{data.id || 'New Step'}</span>
                    <span className="text-xs text-slate-500 font-medium truncate block">{data.type}</span>
                </div>
            </div>

            {data.config && Object.keys(data.config).length > 0 && (
                <div className="mt-3 pt-2 border-t border-slate-100 flex items-center gap-1 text-slate-400 text-xs font-medium">
                    <Settings size={12} /> Configured
                </div>
            )}

            <Handle type="source" position={Position.Bottom} className="w-2 h-2" />
        </div>
    );
}
