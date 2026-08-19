import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { formatDistanceToNow } from "date-fns";
import { Topbar } from "@/components/Topbar";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { useSSE } from "@/hooks/useSSE";
import { fetchRecentPayments } from "@/api/paymentsClient";
import { formatAmount, shortId } from "@/lib/utils";
import type { PaymentLiveEvent } from "@/types";

export function LivePage() {
  const navigate = useNavigate();

  const { data: initial = [], isLoading } = useQuery({
    queryKey: ["recent-payments"],
    queryFn: () => fetchRecentPayments(50),
    refetchInterval: 30_000,
  });

  const { data: liveEvents, status } = useSSE<PaymentLiveEvent>("/api/live/payments/stream", {
    withAuth: true,
  });

  const rows = useMemo(() => {
    const byId = new Map<string, PaymentLiveEvent>();
    for (const p of initial) byId.set(p.paymentId, p);
    for (const p of liveEvents) byId.set(p.paymentId, p);
    return Array.from(byId.values())
      .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
      .slice(0, 50);
  }, [initial, liveEvents]);

  return (
    <div className="min-h-screen">
      <Topbar />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Live payments</h1>
          <span className="flex items-center gap-2 text-sm text-slate-400">
            <span
              className={
                "h-2 w-2 rounded-full " +
                (status === "open" ? "bg-emerald-400 animate-pulse" : "bg-slate-600")
              }
            />
            {status === "open" ? "Streaming" : "Connecting…"}
          </span>
        </div>

        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-900/80 text-left text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-4 py-3">When</th>
                <th className="px-4 py-3">Payment</th>
                <th className="px-4 py-3">Account</th>
                <th className="px-4 py-3 text-right">Amount</th>
                <th className="px-4 py-3">Saga</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {isLoading && rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-slate-500">
                    Loading…
                  </td>
                </tr>
              )}
              {!isLoading && rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-slate-500">
                    No payments yet. Waiting for live events…
                  </td>
                </tr>
              )}
              {rows.map((p) => (
                <tr
                  key={p.paymentId}
                  onClick={() => navigate(`/payments/${p.paymentId}`)}
                  className="cursor-pointer transition-colors hover:bg-slate-800/50"
                >
                  <td className="px-4 py-3 text-slate-400">
                    {formatDistanceToNow(new Date(p.occurredAt), { addSuffix: true })}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs">{shortId(p.paymentId)}</td>
                  <td className="px-4 py-3">{p.accountId}</td>
                  <td className="px-4 py-3 text-right font-medium">{formatAmount(p.amount, p.currency)}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={p.sagaStatus} />
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
