package com.paypulse.outbox.publisher.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(value = "outbox", schema = "payment_command")
data class OutboxEvent(
  @Id
  val id: Long? = null,
  @Column("partitioning_key")
  val partitioningKey: String,
  val type: String,
  val payload: String,
  @Column("idempotency_key")
  val idempotencyKey: UUID? = null,
  val status: OutboxStatus = OutboxStatus.PENDING,
  @Column("created_at")
  val createdAt: OffsetDateTime? = null,
  @Column("processed_at")
  val processedAt: OffsetDateTime? = null,
)
