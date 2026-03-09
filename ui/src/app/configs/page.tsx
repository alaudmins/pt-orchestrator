"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import { Plus, Trash2, Shield, Settings2, Link as LinkIcon, AlertCircle } from "lucide-react";

interface SecretSummary {
    name: string;
    description: string;
    createdAt: string;
}

interface ConfigProfile {
    id: string;
    name: string;
    profileType: string;
    url: string;
    username: string;
    secretReference: string;
}

export default function ConfigsPage() {
    const [secrets, setSecrets] = useState<SecretSummary[]>([]);
    const [profiles, setProfiles] = useState<ConfigProfile[]>([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Modals state
    const [showSecretModal, setShowSecretModal] = useState(false);
    const [showProfileModal, setShowProfileModal] = useState(false);

    // Forms
    const [newSecret, setNewSecret] = useState({ name: "", value: "", description: "" });
    const [newProfile, setNewProfile] = useState({ name: "", profileType: "JENKINS", url: "", username: "", secretReference: "" });

    const fetchData = async () => {
        try {
            setLoading(true);
            const [secRes, profRes] = await Promise.all([
                axios.get("/api/secrets"),
                axios.get("/api/profiles")
            ]);
            setSecrets(secRes.data);
            setProfiles(profRes.data);
            setError(null);
        } catch (err: any) {
            console.error(err);
            setError("Failed to load integrations. Check backend connection.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const saveSecret = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await axios.post("/api/secrets", newSecret);
            setShowSecretModal(false);
            setNewSecret({ name: "", value: "", description: "" });
            fetchData();
        } catch (err: any) {
            alert("Failed to save secret.");
        }
    };

    const deleteSecret = async (name: string) => {
        if (!confirm(`Delete secret '${name}'?`)) return;
        try {
            await axios.delete(`/api/secrets/${name}`);
            fetchData();
        } catch (err) {
            alert("Failed to delete secret.");
        }
    };

    const saveProfile = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await axios.post("/api/profiles", newProfile);
            setShowProfileModal(false);
            setNewProfile({ name: "", profileType: "JENKINS", url: "", username: "", secretReference: "" });
            fetchData();
        } catch (err: any) {
            alert("Failed to save profile.");
        }
    };

    const deleteProfile = async (id: string, name: string) => {
        if (!confirm(`Delete profile '${name}'?`)) return;
        try {
            await axios.delete(`/api/profiles/${id}`);
            fetchData();
        } catch (err) {
            alert("Failed to delete profile.");
        }
    };

    if (loading && secrets.length === 0) return <div className="p-8"><div className="animate-pulse h-8 w-64 bg-slate-200 rounded"></div></div>;

    return (
        <div className="max-w-6xl mx-auto p-8 pt-10 space-y-12 animate-in fade-in slide-in-from-bottom-4 duration-500">
            <header>
                <h1 className="text-3xl font-bold tracking-tight text-slate-800">Integrations</h1>
                <p className="text-slate-500 mt-2 text-lg">Manage connected environments and execution secrets.</p>
            </header>

            {error && (
                <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded-md flex items-center gap-3">
                    <AlertCircle className="text-red-500 h-5 w-5" />
                    <p className="text-sm text-red-700">{error}</p>
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                {/* PROFILES SECTION */}
                <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
                    <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-blue-100 rounded-lg text-blue-600">
                                <LinkIcon size={20} />
                            </div>
                            <h2 className="text-xl font-semibold text-slate-800">Connection Profiles</h2>
                        </div>
                        <button
                            onClick={() => setShowProfileModal(true)}
                            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
                        >
                            <Plus size={16} /> Add Profile
                        </button>
                    </div>

                    <div className="flex-1 p-6">
                        {profiles.length === 0 ? (
                            <div className="text-center py-12 text-slate-400">
                                <LinkIcon className="mx-auto h-12 w-12 opacity-20 mb-3" />
                                <p>No profiles configured yet.</p>
                            </div>
                        ) : (
                            <div className="space-y-4">
                                {profiles.map(p => (
                                    <div key={p.id} className="p-4 border border-slate-200 rounded-xl hover:border-blue-300 transition-colors group">
                                        <div className="flex items-start justify-between">
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <span className="font-semibold text-slate-800">{p.name}</span>
                                                    <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200">{p.profileType}</span>
                                                </div>
                                                <div className="mt-2 text-sm text-slate-500 grid gap-1">
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-slate-400 w-16">URL:</span>
                                                        <span className="text-slate-700">{p.url}</span>
                                                    </div>
                                                    {p.username && (
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-slate-400 w-16">User:</span>
                                                            <span className="text-slate-700">{p.username}</span>
                                                        </div>
                                                    )}
                                                    {p.secretReference && (
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-slate-400 w-16">Auth:</span>
                                                            <span className="flex items-center gap-1 text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded text-xs">
                                                                <Shield size={12} /> {p.secretReference} (Hidden)
                                                            </span>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                            <button onClick={() => deleteProfile(p.id, p.name)} className="text-slate-400 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <Trash2 size={18} />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                {/* SECRETS SECTION */}
                <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
                    <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-emerald-100 rounded-lg text-emerald-600">
                                <Shield size={20} />
                            </div>
                            <h2 className="text-xl font-semibold text-slate-800">Encrypted Secrets</h2>
                        </div>
                        <button
                            onClick={() => setShowSecretModal(true)}
                            className="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
                        >
                            <Plus size={16} /> Add Secret
                        </button>
                    </div>

                    <div className="flex-1 p-6">
                        {secrets.length === 0 ? (
                            <div className="text-center py-12 text-slate-400">
                                <Shield className="mx-auto h-12 w-12 opacity-20 mb-3" />
                                <p>No secure tokens stored yet.</p>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {secrets.map(s => (
                                    <div key={s.name} className="flex flex-col p-4 bg-slate-50 rounded-xl border border-slate-100 hover:bg-slate-100 transition-colors group">
                                        <div className="flex items-start justify-between">
                                            <div>
                                                <div className="font-mono text-sm font-medium text-emerald-700 bg-emerald-100/50 px-2 py-1 rounded inline-block">
                                                    {s.name}
                                                </div>
                                                <p className="text-sm text-slate-500 mt-2">{s.description || "No description provided."}</p>
                                            </div>
                                            <button onClick={() => deleteSecret(s.name)} className="text-slate-400 hover:text-red-500 opacity-0 group-hover:opacity-100 p-2 -mr-2 -mt-2 transition-opacity">
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Modals */}
            {showSecretModal && (
                <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50 backdrop-blur-sm">
                    <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 animate-in zoom-in-95 duration-200">
                        <h3 className="text-xl font-bold mb-4">Store New Secret</h3>
                        <form onSubmit={saveSecret} className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Secret Key Name <span className="text-slate-400 font-normal">(Used in references)</span></label>
                                <input required type="text" value={newSecret.name} onChange={e => setNewSecret({ ...newSecret, name: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-emerald-500 focus:border-emerald-500 font-mono text-sm" placeholder="e.g. jenkins-prod-token" />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Raw Value <span className="text-slate-400 font-normal">(Will be AES-256 encrypted)</span></label>
                                <input required type="password" value={newSecret.value} onChange={e => setNewSecret({ ...newSecret, value: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-emerald-500 focus:border-emerald-500 font-mono text-sm" placeholder="ghp_xxx..." />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Description</label>
                                <input type="text" value={newSecret.description} onChange={e => setNewSecret({ ...newSecret, description: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-emerald-500 focus:border-emerald-500 text-sm" placeholder="Service account token for Production Jenkins" />
                            </div>
                            <div className="flex justify-end gap-3 mt-6">
                                <button type="button" onClick={() => setShowSecretModal(false)} className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium transition-colors">Cancel</button>
                                <button type="submit" className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-sm font-medium transition-colors">Encrypt & Save</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {showProfileModal && (
                <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50 backdrop-blur-sm">
                    <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 animate-in zoom-in-95 duration-200">
                        <h3 className="text-xl font-bold mb-4">Add Connection Profile</h3>
                        <form onSubmit={saveProfile} className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Profile Name</label>
                                <input required type="text" value={newProfile.name} onChange={e => setNewProfile({ ...newProfile, name: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 text-sm" placeholder="e.g. Jenkins Main Cluster" />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
                                <select value={newProfile.profileType} onChange={e => setNewProfile({ ...newProfile, profileType: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 text-sm">
                                    <option value="JENKINS">Jenkins</option>
                                    <option value="GITHUB">GitHub</option>
                                </select>
                            </div>
                            {newProfile.profileType === 'JENKINS' && (
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Endpoint URL</label>
                                    <input required type="url" value={newProfile.url} onChange={e => setNewProfile({ ...newProfile, url: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 text-sm" placeholder="https://jenkins.company.com" />
                                </div>
                            )}
                            <div className="grid grid-cols-2 gap-4">
                                {newProfile.profileType === 'JENKINS' ? (
                                    <div>
                                        <label className="block text-sm font-medium text-slate-700 mb-1">Username <span className="text-slate-400 font-normal">(Optional)</span></label>
                                        <input type="text" value={newProfile.username} onChange={e => setNewProfile({ ...newProfile, username: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 text-sm" placeholder="service_account" />
                                    </div>
                                ) : <div />}
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Attach Secret <span className="text-slate-400 font-normal">(Optional)</span></label>
                                    <select value={newProfile.secretReference} onChange={e => setNewProfile({ ...newProfile, secretReference: e.target.value })} className="w-full border-slate-300 rounded-lg shadow-sm focus:ring-emerald-500 focus:border-emerald-500 text-sm font-mono text-emerald-700 bg-emerald-50">
                                        <option value="">-- None --</option>
                                        {secrets.map(s => <option key={s.name} value={s.name}>{s.name}</option>)}
                                    </select>
                                </div>
                            </div>
                            <div className="flex justify-end gap-3 mt-6">
                                <button type="button" onClick={() => setShowProfileModal(false)} className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium transition-colors">Cancel</button>
                                <button type="submit" className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium transition-colors">Save Profile</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
