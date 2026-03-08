import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import Link from "next/link";
import { Activity, Workflow, LayoutDashboard, Settings } from "lucide-react";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "PTS Orchestrator",
  description: "Performance Test Workflow Orchestrator",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${inter.className} bg-background text-foreground min-h-screen flex`}>
        {/* Sidebar */}
        <aside className="w-64 glass border-r border-border h-screen sticky top-0 flex flex-col items-center py-6 px-4 z-50">
          <div className="flex items-center gap-3 w-full px-2 mb-10">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-blue-500 to-indigo-500 flex items-center justify-center text-white shadow-lg">
              <Activity size={24} />
            </div>
            <h1 className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-700 to-indigo-700">PT Orchestrator</h1>
          </div>

          <nav className="w-full flex flex-col gap-2 flex-1">
            <Link href="/" className="flex items-center gap-3 px-4 py-3 rounded-lg text-slate-600 hover:bg-white hover:text-blue-600 hover:shadow-sm transition-all">
              <Workflow size={20} />
              <span className="font-medium">Workflows</span>
            </Link>
            <Link href="/runs" className="flex items-center gap-3 px-4 py-3 rounded-lg text-slate-600 hover:bg-white hover:text-blue-600 hover:shadow-sm transition-all">
              <LayoutDashboard size={20} />
              <span className="font-medium">Runs</span>
            </Link>
            <Link href="/configs" className="flex items-center gap-3 px-4 py-3 rounded-lg text-slate-600 hover:bg-white hover:text-blue-600 hover:shadow-sm transition-all">
              <Settings size={20} />
              <span className="font-medium">Integrations</span>
            </Link>
          </nav>
        </aside>

        {/* Main Content */}
        <main className="flex-1 p-8 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-50 relative overflow-x-hidden min-h-screen">
          <div className="max-w-7xl mx-auto">
            {children}
          </div>
        </main>
      </body>
    </html>
  );
}
