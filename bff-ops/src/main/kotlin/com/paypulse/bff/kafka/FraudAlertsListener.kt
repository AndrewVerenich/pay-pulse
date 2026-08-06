package com.paypulse.bff.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.bff.model.FraudAlertEvent
import com.paypulse.bff.state.AlertStateStore
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant

interface FraudAlertsListener {
  fun onAlert(record: ConsumerRecord<String, String>)
}

@Component
class DefaultFraudAlertsListener(
  private val objectMapper: ObjectMapper,
  private val store: AlertStateStore,
  private val meterRegistry: MeterRegistry,
) : FraudAlertsListener {
  private val log = LoggerFactory.getLogger(DefaultFraudAlertsListener::class.java)

  @KafkaListener(
    topics = ["\${paypulse.bff.fraud-alerts-topic}"],
    groupId = "bff-ops-fraud-alerts",
  )
  override fun onAlert(record: ConsumerRecord<String, String>) {
    try {
      val node = objectMapper.readTree(record.value())
      val alert = FraudAlertEvent(
        alertId = node.requiredText("alertId"),
        userId = node.requiredText("userId"),
        paymentId = node.requiredText("paymentId"),
        score = node.path("score").asDouble(0.0),
        reasons = node.path("reasons").mapNotNull { if (it.isTextual) it.asText() else null },
        ruleId = node.optText("ruleId") ?: "unknown",
        occurredAt = parseInstant(node),
      )
      log.debug("fraud alert alertId={} score={}", alert.alertId, alert.score)
      val severity = when {
        alert.score >= 0.9 -> "critical"
        alert.score >= 0.7 -> "high"
        else -> "medium"
      }
      meterRegistry.counter("paypulse_fraud_alerts_total", "rule", alert.ruleId, "severity", severity).increment()
      store.onAlert(alert)
    } catch (e: Exception) {
      log.warn("Skipping malformed fraud_alerts record offset={}: {}", record.offset(), e.message)
    }
  }

  private fun parseInstant(node: JsonNode): Instant {
    val epochMs = node.path("occurredAtEpochMs")
    if (epochMs.isNumber) return Instant.ofEpochMilli(epochMs.asLong())
    val text = node.optText("occurredAt")
    if (text != null) return Instant.parse(text)
    return Instant.now()
  }

  private fun JsonNode.requiredText(field: String): String =
    optText(field) ?: error("missing field: $field")

  private fun JsonNode.optText(field: String): String? {
    val v = path(field)
    return if (v.isMissingNode || v.isNull || !v.isTextual || v.asText().isBlank()) null else v.asText()
  }
}
