package com.paypulse.saga.orchestrator.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

@Table(name = "compensation_failure", schema = "saga")
data class CompensationFailureEntity(
  @Id
  @Column("saga_id")
  val sagaId: UUID,
  val reason: String,
  val payload: String? = null,
  @Column("created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
  val resolved: Boolean = false,
)
