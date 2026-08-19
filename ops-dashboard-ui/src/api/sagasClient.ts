import { request } from "@/api/authClient";
import type { SagaSummary } from "@/types";

export function fetchSaga(sagaId: string): Promise<SagaSummary> {
  return request<SagaSummary>(`/api/sagas/${sagaId}`);
}
