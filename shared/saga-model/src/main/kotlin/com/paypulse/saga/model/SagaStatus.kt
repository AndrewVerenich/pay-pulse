package com.paypulse.saga.model

enum class SagaStatus {
  STARTED,
  EXECUTING,
  COMPENSATING,
  COMPLETED,
  COMPENSATED,
  FAILED
}
