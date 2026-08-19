import { Check, X, Loader2, Circle, RotateCcw } from "lucide-react";
import { cn } from "@/lib/utils";
import type { SagaStepSummary } from "@/types";

function iconFor(status: string) {
  switch (status) {
    case "COMPLETED":
      return <Check className="h-4 w-4 text-emerald-400" />;
    case "FAILED":
      return <X className="h-4 w-4 text-rose-400" />;
    case "COMPENSATED":
      return <RotateCcw className="h-4 w-4 text-amber-400" />;
    case "EXECUTING":
    case "COMPENSATING":
      return <Loader2 className="h-4 w-4 animate-spin text-sky-400" />;
    default:
      return <Circle className="h-4 w-4 text-slate-500" />;
  }
}

function lineColor(status: string) {
  switch (status) {
    case "COMPLETED":
      return "bg-emerald-500/40";
    case "FAILED":
      return "bg-rose-500/40";
    case "COMPENSATED":
      return "bg-amber-500/40";
    default:
      return "bg-slate-700";
  }
}

export function StepTimeline({ steps }: { steps: SagaStepSummary[] }) {
  if (steps.length === 0) {
    return <p className="text-sm text-slate-500">No steps yet.</p>;
  }
  return (
    <ol className="space-y-1">
      {steps.map((step, idx) => (
        <li key={step.stepName} className="flex gap-3">
          <div className="flex flex-col items-center">
            <span className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-700 bg-slate-900">
              {iconFor(step.status)}
            </span>
            {idx < steps.length - 1 && (
              <span className={cn("my-1 w-0.5 flex-1", lineColor(step.status))} style={{ minHeight: 16 }} />
            )}
          </div>
          <div className="pb-4">
            <div className="font-medium">{step.stepName}</div>
            <div className="text-xs text-slate-400">
              {step.status}
              {step.attempts > 1 ? ` · attempt ${step.attempts}` : ""}
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}
