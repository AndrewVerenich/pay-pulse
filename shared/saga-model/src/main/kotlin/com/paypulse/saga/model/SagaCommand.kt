package com.paypulse.saga.model

import java.util.UUID

data class SagaCommand(
  val sagaId: UUID,
  val sagaType: String,
  val stepName: String,
  val payload: String,
  val isCompensation: Boolean = false
)
