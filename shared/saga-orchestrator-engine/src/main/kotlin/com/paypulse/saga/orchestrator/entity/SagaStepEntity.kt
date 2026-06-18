package com.paypulse.saga.orchestrator.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table(name = "saga_step", schema = "saga")
data class SagaStepEntity(
  @Id val id: Long? = null,
  val sagaInstanceId: Long,
  val stepName: String,
  val stepType: String,
  val stepOrder: Int,
  val status: String = "PENDING",
  val commandPayload: String? = null,
  val replyPayload: String? = null,
  val errorMessage: String? = null,
  val retryCount: Int = 0,
  val startedAt: LocalDateTime? = null,
  val completedAt: LocalDateTime? = null
)
