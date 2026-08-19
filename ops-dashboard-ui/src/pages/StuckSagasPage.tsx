import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { formatDistanceToNow } from "date-fns";
import { Topbar } from "@/components/Topbar";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import {
  fetchStuckSagas,
  forceCompleteSaga,
  markSagaResolved,
  retrySaga,
} from "@/api/sagasAdminClient";
import { shortId } from "@/lib/utils";
import type { StuckSaga } from "@/types";

export function StuckSagasPage() {
  const queryClient = useQueryClient();
  const [toast, setToast] = useState<{ type: "ok" | "err"; message: string } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const { data: rows = [], isLoading, isFetching } = useQuery({
    queryKey: ["stuck-sagas"],
    queryFn: fetchStuckSagas,
    refetchInterval: 10_000,
  });

  const runAction = async (saga: StuckSaga, action: "retry" | "force" | "resolve") => {
    setBusyId(saga.sagaId);
    setToast(null);
    try {
      if (action === "retry") await retrySaga(saga.sagaId);
      if (action === "force") await forceCompleteSaga(saga.sagaId);
      if (action === "resolve") await markSagaResolved(saga.sagaId);
      setToast({ type: "ok", message: `${action} accepted for ${shortId(saga.sagaId)}` });
      await queryClient.invalidateQueries({ queryKey: ["stuck-sagas"] });
    } catch (e) {
      setToast({
        type: "err",
        message: e instanceof Error ? e.message : "Action failed",
      });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="min-h-screen">
      <Topbar />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Stuck sagas</h1>
          <span className="text-sm text-slate-400">{isFetching ? "Refreshing…" : "Poll every 10s"}</span>
        </div>

        {toast && (
          <p
            className={
              "mb-4 rounded-md border px-3 py-2 text-sm " +
              (toast.type === "ok"
                ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                : "border-rose-500/30 bg-rose-500/10 text-rose-300")
            }
          >
            {toast.message}
          </p>
        )}

        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-900/80 text-left text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-4 py-3">Saga</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Reason</th>
                <th className="px-4 py-3">When</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {isLoading && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-500">Loading…</td>
                </tr>
              )}
              {!isLoading && rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-500">
                    No stuck sagas. All clear.
                  </td>
                </tr>
              )}
              {rows.map((saga) => (
                <tr key={saga.sagaId}>
                  <td className="px-4 py-3 font-mono text-xs">{shortId(saga.sagaId)}</td>
                  <td className="px-4 py-3">{saga.sagaType}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={saga.status} />
                  </td>
                  <td className="max-w-xs truncate px-4 py-3 text-slate-400" title={saga.reason}>
                    {saga.reason}
                  </td>
                  <td className="px-4 py-3 text-slate-400">
                    {formatDistanceToNow(new Date(saga.createdAt), { addSuffix: true })}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="ghost"
                        className="px-2 py-1 text-xs"
                        disabled={busyId === saga.sagaId}
                        onClick={() => runAction(saga, "retry")}
                      >
                        Retry
                      </Button>
                      <Button
                        variant="ghost"
                        className="px-2 py-1 text-xs"
                        disabled={busyId === saga.sagaId}
                        onClick={() => runAction(saga, "force")}
                      >
                        Force complete
                      </Button>
                      <Button
                        variant="ghost"
                        className="px-2 py-1 text-xs"
                        disabled={busyId === saga.sagaId}
                        onClick={() => runAction(saga, "resolve")}
                      >
                        Mark resolved
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </main>
    </div>
  );
}
