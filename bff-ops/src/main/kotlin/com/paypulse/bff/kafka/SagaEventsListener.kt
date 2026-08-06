package com.paypulse.bff.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.bff.model.SagaLifecycleEvent
import com.paypulse.bff.state.LiveStateStore
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime

interface SagaEventsListener {
  fun onSagaEvent(record: ConsumerRecord<String, String>)
}

@Component
class DefaultSagaEventsListener(
  private val objectMapper: ObjectMapper,
  private val store: LiveStateStore,
) : SagaEventsListener {
  private val log = LoggerFactory.getLogger(DefaultSagaEventsListener::class.java)

  @KafkaListener(
    topics = ["\${paypulse.bff.saga-events-topic}"],
    groupId = "bff-ops-saga-events",
  )
  override fun onSagaEvent(record: ConsumerRecord<String, String>) {
    try {
      val node = objectMapper.readTree(record.value())
      val event = SagaLifecycleEvent(
        sagaId = node.requiredText("sagaId"),
        eventType = node.optText("eventType") ?: "SAGA_UPDATE",
        stepName = node.optText("stepName"),
        status = node.optText("status"),
        paymentId = node.optText("paymentId"),
        occurredAt = parseInstant(node.optText("occurredAt")),
        attempt = node.path("attempt").let { if (it.isInt) it.asInt() else 1 },
      )
      log.debug("received saga event sagaId={} type={}", event.sagaId, event.eventType)
      store.onSagaEvent(event)
    } catch (e: Exception) {
      log.warn("Skipping malformed saga event offset={}: {}", record.offset(), e.message)
    }
  }

  private fun parseInstant(value: String?): Instant =
    runCatching { Instant.parse(value) }
      .recoverCatching { OffsetDateTime.parse(value).toInstant() }
      .getOrDefault(Instant.now())

  private fun JsonNode.requiredText(field: String): String =
    optText(field) ?: error("missing field: $field")

  private fun JsonNode.optText(field: String): String? {
    val v = path(field)
    return if (v.isMissingNode || v.isNull || !v.isTextual || v.asText().isBlank()) null else v.asText()
  }
}
