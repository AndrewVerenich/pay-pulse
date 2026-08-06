package com.paypulse.bff.model

import java.time.Instant

data class FraudAlertEvent(
  val alertId: String,
  val userId: String,
  val paymentId: String,
  val score: Double,
  val reasons: List<String>,
  val ruleId: String,
  val occurredAt: Instant,
)

data class StuckSagaItem(
  val sagaId: String,
  val sagaType: String,
  val status: String,
  val currentStep: String?,
  val reason: String,
  val createdAt: String,
  val resolved: Boolean,
)
