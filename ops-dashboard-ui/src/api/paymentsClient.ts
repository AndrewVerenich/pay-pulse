import { request } from "@/api/authClient";
import type { PaymentFull, PaymentLiveEvent } from "@/types";

export function fetchRecentPayments(limit = 50): Promise<PaymentLiveEvent[]> {
  return request<PaymentLiveEvent[]>(`/api/payments/recent?limit=${limit}`);
}

export function fetchPaymentFull(paymentId: string): Promise<PaymentFull> {
  return request<PaymentFull>(`/api/payments/${paymentId}/full`);
}
