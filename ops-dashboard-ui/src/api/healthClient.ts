import { request } from "@/api/authClient";

export type HealthProbeResult = {
  id: string;
  name: string;
  status: "UP" | "DOWN" | "UNKNOWN";
  link?: string | null;
  detail?: string | null;
};

export type HealthSummaryResponse = {
  checkedAt: string;
  probes: HealthProbeResult[];
};

export async function fetchHealthSummary(): Promise<HealthSummaryResponse> {
  return request<HealthSummaryResponse>("/api/health/summary");
}
