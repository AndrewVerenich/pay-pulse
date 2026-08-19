import { request } from "@/api/authClient";
import type { FraudRule } from "@/types";

export interface FraudRulePayload {
  name: string;
  enabled: boolean;
  json_spec: string;
}

export function listRules(): Promise<FraudRule[]> {
  return request<FraudRule[]>("/api/v1/fraud-rules");
}

export function createRule(payload: FraudRulePayload): Promise<FraudRule> {
  return request<FraudRule>("/api/v1/fraud-rules", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateRule(id: string, payload: FraudRulePayload): Promise<FraudRule> {
  return request<FraudRule>(`/api/v1/fraud-rules/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function deleteRule(id: string): Promise<void> {
  return request<void>(`/api/v1/fraud-rules/${id}`, { method: "DELETE" });
}
