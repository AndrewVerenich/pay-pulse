import { request } from "@/api/authClient";
import type { StuckSaga } from "@/types";

export function fetchStuckSagas(): Promise<StuckSaga[]> {
  return request<StuckSaga[]>("/api/sagas/stuck");
}

export function retrySaga(sagaId: string): Promise<void> {
  return request<void>(`/api/v1/sagas/${sagaId}/retry`, { method: "POST" });
}

export function forceCompleteSaga(sagaId: string): Promise<void> {
  return request<void>(`/api/v1/sagas/${sagaId}/force-complete`, { method: "POST" });
}

export function markSagaResolved(sagaId: string): Promise<void> {
  return request<void>(`/api/v1/sagas/${sagaId}/mark-resolved`, { method: "POST" });
}
