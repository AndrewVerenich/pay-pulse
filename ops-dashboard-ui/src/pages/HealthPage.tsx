import { useQuery } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import { Topbar } from "@/components/Topbar";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { fetchHealthSummary } from "@/api/healthClient";
import { formatDistanceToNow } from "date-fns";

export function HealthPage() {
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ["health-summary"],
    queryFn: fetchHealthSummary,
    refetchInterval: 30_000,
  });

  return (
    <div className="min-h-screen">
      <Topbar />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold">Platform health</h1>
            <p className="mt-1 text-sm text-slate-400">
              {data?.checkedAt
                ? `Checked ${formatDistanceToNow(new Date(data.checkedAt), { addSuffix: true })}`
                : "Loading service probes…"}
            </p>
          </div>
          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800 disabled:opacity-50"
          >
            Refresh
          </button>
        </div>

        {isLoading && <p className="text-slate-400">Loading…</p>}
        {isError && <p className="text-rose-400">Failed to load health summary.</p>}

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data?.probes.map((probe) => (
            <Card key={probe.id} className="p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="font-medium">{probe.name}</h2>
                  {probe.detail && (
                    <p className="mt-1 text-xs text-slate-500 line-clamp-2">{probe.detail}</p>
                  )}
                </div>
                <StatusBadge status={probe.status === "UP" ? "COMPLETED" : "FAILED"} />
              </div>
              {probe.link && (
                <a
                  href={probe.link}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-flex items-center gap-1 text-xs text-indigo-400 hover:text-indigo-300"
                >
                  Open
                  <ExternalLink className="h-3 w-3" />
                </a>
              )}
            </Card>
          ))}
        </div>
      </main>
    </div>
  );
}
