package com.paypulse.saga.orchestrator.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

@Table(name = "saga_instance", schema = "saga")
data class SagaInstanceEntity(
  @Id val id: Long? = null,
  val sagaId: UUID,
  val sagaType: String,
  val status: String = "STARTED",
  val currentStep: String? = null,
  val payload: String,
  val createdAt: LocalDateTime = LocalDateTime.now(),
  val updatedAt: LocalDateTime = LocalDateTime.now(),
  val completedAt: LocalDateTime? = null
)
