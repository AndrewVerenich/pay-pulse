export interface PaymentLiveEvent {
  paymentId: string;
  accountId: string;
  amount: number | string;
  currency: string;
  merchantId?: string | null;
  sagaId?: string | null;
  sagaStatus?: string | null;
  occurredAt: string;
}

export interface SagaLifecycleEvent {
  sagaId: string;
  eventType: string;
  stepName?: string | null;
  status?: string | null;
  paymentId?: string | null;
  occurredAt: string;
  attempt: number;
}

export interface SagaStepSummary {
  stepName: string;
  status: string;
  attempts: number;
  startedAt?: string | null;
  finishedAt?: string | null;
}

export interface SagaSummary {
  sagaId: string;
  status: string;
  currentStep?: string | null;
  steps: SagaStepSummary[];
}

export interface PaymentFull {
  payment: PaymentLiveEvent;
  saga: SagaSummary | null;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface FraudRuleSpec {
  maxAmount: number;
  velocityWindowMs: number;
  velocityMaxCount: number;
  structuringThreshold: number;
  structuringWindowHours: number;
  structuringMinPayments: number;
}

export interface FraudRule {
  id: string;
  name: string;
  enabled: boolean;
  json_spec: string;
  version: number;
  updatedAt?: string | null;
}

export interface RuleAck {
  ruleId: string;
  version: number;
  appliedAt: string;
}

export interface FraudAlert {
  alertId: string;
  userId: string;
  paymentId: string;
  score: number;
  reasons: string[];
  ruleId: string;
  occurredAt: string;
}

export interface StuckSaga {
  sagaId: string;
  sagaType: string;
  status: string;
  currentStep?: string | null;
  reason: string;
  createdAt: string;
  resolved: boolean;
}
