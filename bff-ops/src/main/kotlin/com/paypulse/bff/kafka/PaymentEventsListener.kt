package com.paypulse.bff.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.bff.model.PaymentLiveEvent
import com.paypulse.bff.state.LiveStateStore
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

interface PaymentEventsListener {
  fun onPaymentEvent(record: ConsumerRecord<String, String>)
}

@Component
class DefaultPaymentEventsListener(
  private val objectMapper: ObjectMapper,
  private val store: LiveStateStore,
) : PaymentEventsListener {
  private val log = LoggerFactory.getLogger(DefaultPaymentEventsListener::class.java)

  @KafkaListener(
    topics = ["\${paypulse.bff.payment-events-topic}"],
    groupId = "bff-ops-live-payments",
  )
  override fun onPaymentEvent(record: ConsumerRecord<String, String>) {
    try {
      val node = objectMapper.readTree(record.value())
      val event = PaymentLiveEvent(
        paymentId = node.requiredText("paymentId"),
        accountId = node.requiredText("accountId"),
        amount = node.path("amount").let { if (it.isMissingNode || it.isNull) BigDecimal.ZERO else it.decimalValue() },
        currency = node.optText("currency") ?: "UNKNOWN",
        merchantId = node.optText("merchantId"),
        sagaId = node.optText("sagaId"),
        occurredAt = parseInstant(node.optText("occurredAt")),
      )
      log.debug("received payment event paymentId={}", event.paymentId)
      store.onPaymentEvent(event)
    } catch (e: Exception) {
      log.warn("Skipping malformed payment event offset={}: {}", record.offset(), e.message)
    }
  }

  private fun parseInstant(value: String?): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }
      .recoverCatching { Instant.parse(value) }
      .getOrDefault(Instant.now())

  private fun JsonNode.requiredText(field: String): String =
    optText(field) ?: error("missing field: $field")

  private fun JsonNode.optText(field: String): String? {
    val v = path(field)
    return if (v.isMissingNode || v.isNull || !v.isTextual || v.asText().isBlank()) null else v.asText()
  }
}
