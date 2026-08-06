package com.paypulse.bff.model

import java.math.BigDecimal
import java.time.Instant

data class PaymentLiveEvent(
  val paymentId: String,
  val accountId: String,
  val amount: BigDecimal,
  val currency: String,
  val merchantId: String? = null,
  val sagaId: String? = null,
  val sagaStatus: String? = null,
  val occurredAt: Instant,
)

data class SagaLifecycleEvent(
  val sagaId: String,
  val eventType: String,
  val stepName: String? = null,
  val status: String? = null,
  val paymentId: String? = null,
  val occurredAt: Instant,
  val attempt: Int = 1,
)

data class PaymentFull(
  val payment: PaymentLiveEvent,
  val saga: SagaSummary?,
)

data class SagaSummary(
  val sagaId: String,
  val status: String,
  val currentStep: String?,
  val steps: List<SagaStepSummary>,
)

data class SagaStepSummary(
  val stepName: String,
  val status: String,
  val attempts: Int,
  val startedAt: String?,
  val finishedAt: String?,
)
