import type { ReactNode } from "react";

export function GlassCard({ title, children, className = "" }: {
  title?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`glass p-5 ${className}`}>
      {title && (
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-slate-400">
          {title}
        </h2>
      )}
      {children}
    </section>
  );
}

export function StatCard({ label, value, hint, tone = "default" }: {
  label: string;
  value: string;
  hint?: string;
  tone?: "default" | "good" | "bad";
}) {
  const toneClass =
    tone === "good" ? "text-emerald-300" : tone === "bad" ? "text-rose-300" : "accent";
  return (
    <div className="glass p-5" data-testid="stat-card">
      <div className="text-xs font-semibold uppercase tracking-widest text-slate-400">{label}</div>
      <div className={`mt-2 text-3xl font-bold ${toneClass}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </div>
  );
}

const STATUS_STYLES: Record<string, string> = {
  delivered: "bg-emerald-400/10 text-emerald-300 border-emerald-400/20",
  pending: "bg-amber-400/10 text-amber-300 border-amber-400/20",
  failed: "bg-rose-400/10 text-rose-300 border-rose-400/20",
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${
      STATUS_STYLES[status] ?? "bg-white/5 text-slate-300 border-white/10"
    }`}>
      {status}
    </span>
  );
}

export function TypeBadge({ type }: { type: string }) {
  return (
    <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-2.5 py-0.5 text-xs font-medium text-sky-300 mono">
      {type}
    </span>
  );
}

export function ago(iso: string | null): string {
  if (!iso) return "—";
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return `${Math.round(seconds)}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)}h ago`;
  return `${Math.round(seconds / 86400)}d ago`;
}

export function fmtTime(iso: string): string {
  return new Date(iso).toISOString().replace("T", " ").replace(/\.\d+Z$/, "Z");
}
