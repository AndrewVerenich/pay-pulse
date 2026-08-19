import { cn } from "@/lib/utils";

const palette: Record<string, string> = {
  STARTED: "bg-sky-500/15 text-sky-300 border-sky-500/30",
  RUNNING: "bg-sky-500/15 text-sky-300 border-sky-500/30",
  EXECUTING: "bg-sky-500/15 text-sky-300 border-sky-500/30",
  PENDING: "bg-slate-500/15 text-slate-300 border-slate-500/30",
  COMPENSATING: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  COMPLETED: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  COMPENSATED: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  FAILED: "bg-rose-500/15 text-rose-300 border-rose-500/30",
};

export function StatusBadge({ status }: { status?: string | null }) {
  const label = status ?? "—";
  const cls = (status && palette[status]) ?? "bg-slate-500/15 text-slate-300 border-slate-500/30";
  return (
    <span className={cn("inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium", cls)}>
      {label}
    </span>
  );
}
