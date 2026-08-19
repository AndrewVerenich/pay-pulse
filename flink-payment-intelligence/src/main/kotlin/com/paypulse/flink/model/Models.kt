package com.paypulse.flink.model

import java.io.Serializable

/**
 * Платёжное событие из `payment.events`. Поля плоские, типы примитивные —
 * чтобы Flink-сериализация (Kryo fallback для Kotlin data class) была дешёвой.
 */
data class PaymentEvent(
  val eventId: String = "",
  val paymentId: String = "",
  val accountId: String = "",
  val amount: Double = 0.0,
  val currency: String = "",
  val merchantId: String? = null,
  val occurredAtEpochMs: Long = 0L,
) : Serializable

/**
 * Правило фрода из compact-топика `fraud_rules`. До S5 менеджится seed'ом.
 */
data class FraudRule(
  val ruleId: String = "rule-default",
  val version: Int = 1,
  val enabled: Boolean = true,
  val maxAmount: Double = 10_000.0,
  val velocityWindowMs: Long = 3_600_000L,
  val velocityMaxCount: Int = 50,
  val structuringThreshold: Double = 9_900.0,
  val structuringWindowHours: Int = 24,
  val structuringMinPayments: Int = 3,
) : Serializable {
  companion object {
    val DEFAULT = FraudRule()
  }
}

data class UserRiskProfile(
  val userId: String = "",
  val baselineRisk: Double = 0.1,
) : Serializable

/** Платёж, обогащённый baseline-риском из `user_risk_profiles` (через UserRiskEnricher). */
data class EnrichedPayment(
  val event: PaymentEvent = PaymentEvent(),
  val baselineRisk: Double = 0.1,
) : Serializable

data class FraudAlert(
  val alertId: String,
  val userId: String,
  val paymentId: String,
  val score: Double,
  val reasons: List<String>,
  val ruleId: String,
  val occurredAtEpochMs: Long,
) : Serializable

data class UserFraudScore(
  val userId: String,
  val score: Double,
  val lastPaymentId: String,
  val updatedAtEpochMs: Long,
) : Serializable

data class PaymentMetrics(
  val windowStartEpochMs: Long,
  val windowEndEpochMs: Long,
  val currency: String,
  val count: Long,
  val totalAmount: Double,
) : Serializable

data class DeadLetter(
  val raw: String,
  val error: String,
  val occurredAtEpochMs: Long,
) : Serializable
