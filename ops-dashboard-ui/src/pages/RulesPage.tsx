import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Loader2, Plus, Trash2 } from "lucide-react";
import { Topbar } from "@/components/Topbar";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useSSE } from "@/hooks/useSSE";
import { listRules, createRule, updateRule, deleteRule } from "@/api/rulesClient";
import type { FraudRule, RuleAck } from "@/types";

// Zod-схема зеркальна fraud-rule.schema.json на бэкенде (ручное дублирование, см. S5 §9).
const ruleSchema = z.object({
  name: z.string().min(3, "Min 3 chars").max(128),
  enabled: z.boolean(),
  maxAmount: z.coerce.number().min(0),
  velocityWindowMs: z.coerce.number().int().min(1000),
  velocityMaxCount: z.coerce.number().int().min(1),
  structuringThreshold: z.coerce.number().min(0),
  structuringWindowHours: z.coerce.number().int().min(1).max(168),
  structuringMinPayments: z.coerce.number().int().min(2),
});

type RuleFormValues = z.infer<typeof ruleSchema>;

const DEFAULTS: RuleFormValues = {
  name: "",
  enabled: true,
  maxAmount: 10000,
  velocityWindowMs: 3600000,
  velocityMaxCount: 50,
  structuringThreshold: 9900,
  structuringWindowHours: 24,
  structuringMinPayments: 3,
};

type AckStatus = "waiting" | "applied" | "timeout";

function specOf(rule: FraudRule): Partial<RuleFormValues> {
  try {
    return JSON.parse(rule.json_spec) as Partial<RuleFormValues>;
  } catch {
    return {};
  }
}

export function RulesPage() {
  const queryClient = useQueryClient();
  const { data: rules = [], isLoading } = useQuery({
    queryKey: ["fraud-rules"],
    queryFn: listRules,
  });

  const [editing, setEditing] = useState<FraudRule | "new" | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [ackWatch, setAckWatch] = useState<{ ruleId: string; target: number } | null>(null);
  const [ackStatus, setAckStatus] = useState<AckStatus | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<RuleFormValues>({ resolver: zodResolver(ruleSchema), defaultValues: DEFAULTS });

  useEffect(() => {
    if (editing === "new") {
      reset(DEFAULTS);
    } else if (editing) {
      reset({ ...DEFAULTS, ...specOf(editing), name: editing.name, enabled: editing.enabled });
    }
    setServerError(null);
  }, [editing, reset]);

  // SSE ack «применено во Flink».
  const ackUrl = ackWatch
    ? `/api/live/rules/ack?ruleId=${ackWatch.ruleId}&sinceVersion=${ackWatch.target}`
    : null;
  const { data: acks } = useSSE<RuleAck>(ackUrl, { withAuth: true, enabled: !!ackWatch });

  useEffect(() => {
    if (!ackWatch) return;
    if (acks.some((a) => a.ruleId === ackWatch.ruleId && a.version >= ackWatch.target)) {
      setAckStatus("applied");
      const t = setTimeout(() => setAckWatch(null), 4000);
      return () => clearTimeout(t);
    }
  }, [acks, ackWatch]);

  useEffect(() => {
    if (!ackWatch) return;
    setAckStatus("waiting");
    const timeout = setTimeout(() => setAckStatus("timeout"), 10000);
    return () => clearTimeout(timeout);
  }, [ackWatch]);

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    const json_spec = JSON.stringify({
      maxAmount: values.maxAmount,
      velocityWindowMs: values.velocityWindowMs,
      velocityMaxCount: values.velocityMaxCount,
      structuringThreshold: values.structuringThreshold,
      structuringWindowHours: values.structuringWindowHours,
      structuringMinPayments: values.structuringMinPayments,
    });
    const payload = { name: values.name, enabled: values.enabled, json_spec };
    try {
      const saved = editing === "new"
        ? await createRule(payload)
        : await updateRule((editing as FraudRule).id, payload);
      await queryClient.invalidateQueries({ queryKey: ["fraud-rules"] });
      setEditing(null);
      setAckWatch({ ruleId: saved.id, target: saved.version });
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Save failed");
    }
  });

  const onDelete = async (rule: FraudRule) => {
    if (!confirm(`Delete rule "${rule.name}"?`)) return;
    await deleteRule(rule.id);
    await queryClient.invalidateQueries({ queryKey: ["fraud-rules"] });
  };

  const ackBanner = useMemo(() => {
    if (!ackStatus) return null;
    if (ackStatus === "applied") {
      return (
        <span className="flex items-center gap-2 text-emerald-400">
          <CheckCircle2 className="h-4 w-4" /> Applied in Flink
        </span>
      );
    }
    if (ackStatus === "timeout") {
      return <span className="text-amber-400">No ack within 10s (check Flink job)</span>;
    }
    return (
      <span className="flex items-center gap-2 text-slate-400">
        <Loader2 className="h-4 w-4 animate-spin" /> Waiting for Flink to apply…
      </span>
    );
  }, [ackStatus]);

  return (
    <div className="min-h-screen">
      <Topbar />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Fraud rules</h1>
          <div className="flex items-center gap-4 text-sm">
            {ackBanner}
            <Button onClick={() => setEditing("new")}>
              <Plus className="h-4 w-4" /> New rule
            </Button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <Card className="overflow-hidden lg:col-span-2">
            <table className="w-full text-sm">
              <thead className="bg-slate-900/80 text-left text-xs uppercase tracking-wide text-slate-400">
                <tr>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Enabled</th>
                  <th className="px-4 py-3 text-right">Max amount</th>
                  <th className="px-4 py-3 text-right">v</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {isLoading && (
                  <tr><td colSpan={5} className="px-4 py-10 text-center text-slate-500">Loading…</td></tr>
                )}
                {!isLoading && rules.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-10 text-center text-slate-500">No rules yet.</td></tr>
                )}
                {rules.map((rule) => {
                  const spec = specOf(rule);
                  return (
                    <tr key={rule.id} className="transition-colors hover:bg-slate-800/50">
                      <td
                        className="cursor-pointer px-4 py-3 font-medium"
                        onClick={() => setEditing(rule)}
                      >
                        {rule.name}
                      </td>
                      <td className="px-4 py-3">
                        <span className={rule.enabled ? "text-emerald-400" : "text-slate-500"}>
                          {rule.enabled ? "on" : "off"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">{spec.maxAmount ?? "—"}</td>
                      <td className="px-4 py-3 text-right text-slate-400">{rule.version}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => onDelete(rule)}
                          className="text-slate-500 hover:text-rose-400"
                          title="Delete"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Card>

          {editing && (
            <Card className="p-6">
              <h2 className="mb-4 text-lg font-semibold">
                {editing === "new" ? "New rule" : "Edit rule"}
              </h2>
              <form onSubmit={onSubmit} className="space-y-3">
                <Field label="Name" error={errors.name?.message}>
                  <Input {...register("name")} placeholder="high-amount" />
                </Field>
                <label className="flex items-center gap-2 text-sm text-slate-300">
                  <input type="checkbox" {...register("enabled")} className="accent-indigo-500" />
                  Enabled
                </label>
                <Field label="Max amount" error={errors.maxAmount?.message}>
                  <Input type="number" step="any" {...register("maxAmount")} />
                </Field>
                <Field label="Velocity window (ms)" error={errors.velocityWindowMs?.message}>
                  <Input type="number" {...register("velocityWindowMs")} />
                </Field>
                <Field label="Velocity max count" error={errors.velocityMaxCount?.message}>
                  <Input type="number" {...register("velocityMaxCount")} />
                </Field>
                <Field label="Structuring threshold" error={errors.structuringThreshold?.message}>
                  <Input type="number" step="any" {...register("structuringThreshold")} />
                </Field>
                <Field label="Structuring window (hours)" error={errors.structuringWindowHours?.message}>
                  <Input type="number" {...register("structuringWindowHours")} />
                </Field>
                <Field label="Structuring min payments" error={errors.structuringMinPayments?.message}>
                  <Input type="number" {...register("structuringMinPayments")} />
                </Field>
                {serverError && (
                  <p className="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
                    {serverError}
                  </p>
                )}
                <div className="flex gap-2 pt-2">
                  <Button type="submit" disabled={isSubmitting} className="flex-1">
                    {isSubmitting ? "Saving…" : "Save"}
                  </Button>
                  <Button type="button" variant="ghost" onClick={() => setEditing(null)}>
                    Cancel
                  </Button>
                </div>
              </form>
            </Card>
          )}
        </div>
      </main>
    </div>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1 block text-sm text-slate-400">{label}</label>
      {children}
      {error && <p className="mt-1 text-xs text-rose-400">{error}</p>}
    </div>
  );
}
