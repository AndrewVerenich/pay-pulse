import { useMemo, useState } from "react";
import { formatDistanceToNow } from "date-fns";
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Topbar } from "@/components/Topbar";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useSSE } from "@/hooks/useSSE";
import { alertsPerMinute, filterAlerts } from "@/lib/alertsFilter";
import { shortId } from "@/lib/utils";
import type { FraudAlert } from "@/types";

const BUFFER_SIZE = 500;

export function AlertsPage() {
  const [userId, setUserId] = useState("");
  const [ruleId, setRuleId] = useState("");
  const [minScore, setMinScore] = useState("");

  const { data: liveAlerts, status } = useSSE<FraudAlert>("/api/live/alerts/stream", {
    withAuth: true,
    bufferSize: BUFFER_SIZE,
  });

  const filtered = useMemo(
    () =>
      filterAlerts(liveAlerts, {
        userId: userId || undefined,
        ruleId: ruleId || undefined,
        minScore: minScore ? Number(minScore) : undefined,
      }).slice(0, BUFFER_SIZE),
    [liveAlerts, userId, ruleId, minScore],
  );

  const chartData = useMemo(() => alertsPerMinute(liveAlerts), [liveAlerts]);

  return (
    <div className="min-h-screen">
      <Topbar />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Fraud alerts</h1>
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

        <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs text-slate-400">User ID contains</label>
            <Input value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="acc-1" />
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-400">Rule ID</label>
            <Input value={ruleId} onChange={(e) => setRuleId(e.target.value)} placeholder="rule-default" />
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-400">Min score</label>
            <Input
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={minScore}
              onChange={(e) => setMinScore(e.target.value)}
              placeholder="0.8"
            />
          </div>
        </div>

        <Card className="mb-6 p-4">
          <h2 className="mb-3 text-sm font-medium text-slate-300">Alerts / minute (last 15 min)</h2>
          <div className="h-48">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData}>
                <XAxis dataKey="minute" tick={{ fill: "#94a3b8", fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fill: "#94a3b8", fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ background: "#0f172a", border: "1px solid #334155", borderRadius: 8 }}
                />
                <Line type="monotone" dataKey="count" stroke="#818cf8" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-900/80 text-left text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-4 py-3">When</th>
                <th className="px-4 py-3">User</th>
                <th className="px-4 py-3">Payment</th>
                <th className="px-4 py-3 text-right">Score</th>
                <th className="px-4 py-3">Rule</th>
                <th className="px-4 py-3">Reasons</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-500">
                    No alerts yet. Waiting for live events…
                  </td>
                </tr>
              )}
              {filtered.map((alert) => (
                <tr key={alert.alertId} className="hover:bg-slate-800/40">
                  <td className="px-4 py-3 text-slate-400">
                    {formatDistanceToNow(new Date(alert.occurredAt), { addSuffix: true })}
                  </td>
                  <td className="px-4 py-3">{alert.userId}</td>
                  <td className="px-4 py-3 font-mono text-xs">{shortId(alert.paymentId)}</td>
                  <td className="px-4 py-3 text-right font-medium text-rose-300">
                    {alert.score.toFixed(2)}
                  </td>
                  <td className="px-4 py-3 text-slate-400">{alert.ruleId}</td>
                  <td className="px-4 py-3 text-xs text-slate-400">{alert.reasons.join(", ")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </main>
    </div>
  );
}
