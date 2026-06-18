package com.paypulse.paymentcommand.adapter.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table(value = "idempotency_keys", schema = "payment_command")
data class IdempotencyRow(
  @Id
  @Column("key_hash")
  val keyHash: String,
  @Column("request_hash")
  val requestHash: String,
  @Column("response_body")
  val responseBody: String,
  @Column("created_at")
  val createdAt: OffsetDateTime? = null,
  @Column("expires_at")
  val expiresAt: OffsetDateTime,
)
