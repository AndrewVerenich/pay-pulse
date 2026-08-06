package com.paypulse.rules.adapter.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "fraud_rule", schema = "rule_management")
data class FraudRuleRow(
  @Id val id: UUID? = null,
  val name: String,
  val enabled: Boolean,
  @Column("json_spec") val jsonSpec: String,
  val version: Long = 1,
  @Column("updated_at") val updatedAt: OffsetDateTime? = null,
)

@Table(name = "outbox", schema = "rule_management")
data class RuleOutboxRow(
  @Id val id: Long? = null,
  @Column("partitioning_key") val partitioningKey: String,
  val type: String,
  val payload: String,
)
