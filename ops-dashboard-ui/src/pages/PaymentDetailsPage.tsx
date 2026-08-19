import { useMemo, type ReactNode } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { Topbar } from "@/components/Topbar";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { StepTimeline } from "@/components/StepTimeline";
import { useSSE } from "@/hooks/useSSE";
import { fetchPaymentFull } from "@/api/paymentsClient";
import { formatAmount, shortId } from "@/lib/utils";
import type { SagaLifecycleEvent, SagaStepSummary } from "@/types";

export function PaymentDetailsPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();

  const { data, isLoading } = useQuery({
    queryKey: ["payment-full", id],
    queryFn: () => fetchPaymentFull(id),
    enabled: Boolean(id),
  });

  const sagaId = data?.saga?.sagaId ?? data?.payment.sagaId ?? null;

  const { data: liveEvents } = useSSE<SagaLifecycleEvent>(
    sagaId ? `/api/live/sagas/${sagaId}/stream` : null,
    { withAuth: true, enabled: Boolean(sagaId) },
  );

  const steps: SagaStepSummary[] = useMemo(() => {
    const base = new Map<string, SagaStepSummary>();
    for (const s of data?.saga?.steps ?? []) base.set(s.stepName, { ...s });
    for (const ev of [...liveEvents].reverse()) {
      if (!ev.stepName) continue;
      const prev = base.get(ev.stepName) ?? {
        stepName: ev.stepName,
        status: "PENDING",
        attempts: ev.attempt,
        startedAt: null,
        finishedAt: null,
      };
      base.set(ev.stepName, { ...prev, status: ev.status ?? prev.status, attempts: ev.attempt });
    }
    return Array.from(base.values());
  }, [data, liveEvents]);

  const sagaStatus = useMemo(() => {
    const terminal = [...liveEvents].find((e) => e.eventType.startsWith("SAGA_") && e.status);
    return terminal?.status ?? data?.saga?.status ?? data?.payment.sagaStatus ?? null;
  }, [liveEvents, data]);

  return (
    <div className="min-h-screen">
      <Topbar />
      <main className="mx-auto max-w-4xl px-6 py-8">
        <Button variant="ghost" onClick={() => navigate("/live")} className="mb-6 px-3 py-1.5">
          <ArrowLeft className="h-4 w-4" />
          Back to live
        </Button>

        {isLoading && <p className="text-slate-500">Loading…</p>}

        {data && (
          <div className="grid gap-6 md:grid-cols-2">
            <Card className="p-6">
              <h2 className="mb-4 text-lg font-semibold">Payment</h2>
              <dl className="space-y-2 text-sm">
                <Row label="Payment ID" value={<span className="font-mono">{shortId(data.payment.paymentId)}</span>} />
                <Row label="Account" value={data.payment.accountId} />
                <Row label="Amount" value={formatAmount(data.payment.amount, data.payment.currency)} />
                <Row label="Merchant" value={data.payment.merchantId ?? "—"} />
                <Row label="Saga" value={<StatusBadge status={sagaStatus} />} />
              </dl>
            </Card>

            <Card className="p-6">
              <h2 className="mb-4 text-lg font-semibold">Saga timeline</h2>
              <StepTimeline steps={steps} />
            </Card>
          </div>
        )}
      </main>
    </div>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-slate-400">{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
