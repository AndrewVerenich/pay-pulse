package com.paypulse.flink.config

import java.io.Serializable

/**
 * Конфиг job'ы, читается из переменных окружения (Flink session cluster прокидывает их в TM/JM).
 */
data class FlinkJobProperties(
  val bootstrapServers: String,
  val paymentEventsTopic: String,
  val fraudRulesTopic: String,
  val userRiskProfilesTopic: String,
  val fraudAlertsTopic: String,
  val deadLetterTopic: String,
  val paymentMetricsTopic: String,
  val userFraudScoresTopic: String,
  val consumerGroup: String,
  val checkpointDir: String,
) : Serializable {
  companion object {
    fun fromEnv(): FlinkJobProperties {
      fun env(key: String, default: String) = System.getenv(key) ?: default
      return FlinkJobProperties(
        bootstrapServers = env("PAYPULSE_KAFKA_BOOTSTRAP", "kafka:9092"),
        paymentEventsTopic = env("PAYPULSE_PAYMENT_EVENTS_TOPIC", "payment.events"),
        fraudRulesTopic = env("PAYPULSE_FRAUD_RULES_TOPIC", "fraud_rules"),
        userRiskProfilesTopic = env("PAYPULSE_USER_RISK_PROFILES_TOPIC", "user_risk_profiles"),
        fraudAlertsTopic = env("PAYPULSE_FRAUD_ALERTS_TOPIC", "fraud_alerts"),
        deadLetterTopic = env("PAYPULSE_DEAD_LETTER_TOPIC", "dead_letter"),
        paymentMetricsTopic = env("PAYPULSE_PAYMENT_METRICS_TOPIC", "payment_metrics_hourly"),
        userFraudScoresTopic = env("PAYPULSE_USER_FRAUD_SCORES_TOPIC", "user_fraud_scores"),
        consumerGroup = env("PAYPULSE_FLINK_CONSUMER_GROUP", "flink-paypulse-pi"),
        checkpointDir = env("PAYPULSE_FLINK_CHECKPOINT_DIR", "file:///checkpoints/paypulse-flink"),
      )
    }
  }
}
