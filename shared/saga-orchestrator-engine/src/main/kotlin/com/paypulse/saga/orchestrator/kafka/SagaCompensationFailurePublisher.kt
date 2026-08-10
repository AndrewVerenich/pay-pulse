package com.paypulse.saga.orchestrator.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.saga.model.SagaCompensationFailedEvent
import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class SagaCompensationFailurePublisher(
  @Qualifier("sagaEventKafkaTemplate") private val kafkaTemplate: KafkaTemplate<String, String>,
  private val objectMapper: ObjectMapper,
  @Value("\${paypulse.saga.compensation-failed-topic:saga.compensation.failed}") private val topic: String,
) {
  private val log = LoggerFactory.getLogger(SagaCompensationFailurePublisher::class.java)

  fun publish(instance: SagaInstanceEntity, reason: String) {
    val event = SagaCompensationFailedEvent(
      sagaId = instance.sagaId.toString(),
      sagaType = instance.sagaType,
      reason = reason,
      paymentId = extractPaymentId(instance.payload),
    )
    try {
      val json = objectMapper.writeValueAsString(event)
      kafkaTemplate.send(topic, event.sagaId, json)
    } catch (e: Exception) {
      log.warn("Failed to publish compensation failure sagaId={}: {}", instance.sagaId, e.message)
    }
  }

  private fun extractPaymentId(payload: String?): String? {
    if (payload.isNullOrBlank()) return null
    return runCatching {
      objectMapper.readTree(payload).path("paymentId").let { if (it.isMissingNode || it.isNull) null else it.asText() }
    }.getOrNull()
  }
}
