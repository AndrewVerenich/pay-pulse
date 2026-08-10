package com.paypulse.saga.model

import java.time.Instant

data class SagaLifecycleEvent(
  val sagaId: String,
  val sagaType: String,
  val eventType: String,
  val status: String,
  val stepName: String? = null,
  val paymentId: String? = null,
  val attempt: Int = 1,
  val occurredAt: Instant = Instant.now(),
)
