package com.paypulse.saga.model

import java.util.UUID

data class SagaReply(
  val sagaId: UUID,
  val stepName: String,
  val status: ReplyStatus,
  val payload: String? = null,
  val errorMessage: String? = null,
  val isCompensation: Boolean = false
)

enum class ReplyStatus {
  SUCCESS,
  FAILURE
}
