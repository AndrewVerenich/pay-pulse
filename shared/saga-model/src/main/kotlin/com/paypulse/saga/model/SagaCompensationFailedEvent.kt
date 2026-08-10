package com.paypulse.saga.model

import java.time.Instant

data class SagaCompensationFailedEvent(
  val sagaId: String,
  val sagaType: String,
  val reason: String,
  val paymentId: String? = null,
  val occurredAt: Instant = Instant.now(),
)
