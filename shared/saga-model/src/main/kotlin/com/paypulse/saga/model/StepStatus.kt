package com.paypulse.saga.model

enum class StepStatus {
  PENDING,
  EXECUTING,
  COMPLETED,
  FAILED,
  COMPENSATING,
  COMPENSATED,
  SKIPPED
}
