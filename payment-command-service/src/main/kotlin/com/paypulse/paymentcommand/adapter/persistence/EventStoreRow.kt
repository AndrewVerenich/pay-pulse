package com.paypulse.paymentcommand.adapter.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

@Table(value = "event_store", schema = "payment_command")
data class EventStoreRow(
  @Id
  val id: Long? = null,
  @Column("aggregate_id")
  val aggregateId: UUID,
  @Column("aggregate_type")
  val aggregateType: String,
  @Column("event_type")
  val eventType: String,
  val version: Int,
  @Column("account_id")
  val accountId: String,
  val payload: String,
  @Column("occurred_at")
  val occurredAt: OffsetDateTime,
)
