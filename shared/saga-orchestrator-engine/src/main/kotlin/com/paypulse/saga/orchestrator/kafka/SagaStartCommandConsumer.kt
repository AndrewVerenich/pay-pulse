package com.paypulse.saga.orchestrator.kafka

import com.paypulse.saga.orchestrator.engine.SagaOrchestrator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SagaStartCommandConsumer(
  private val sagaOrchestrator: SagaOrchestrator,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(SagaStartCommandConsumer::class.java)

  @KafkaListener(
    topics = ["\${paypulse.saga.start-topic:saga.commands.start}"],
    groupId = "\${spring.kafka.consumer.group-id:paypulse-saga-orchestrator}-start",
    containerFactory = "sagaStartKafkaListenerContainerFactory",
  )
  fun onStart(payload: JsonNode) {
    val sagaId = UUID.fromString(payload.get("sagaId").asText())
    val sagaType = payload.path("sagaType").asText("PaymentSaga")
    val payloadJson = objectMapper.writeValueAsString(payload)
    log.info("Starting saga from outbox sagaId={} type={}", sagaId, sagaType)
    sagaOrchestrator.startSaga(sagaType, payloadJson, sagaId).block()
  }
}
