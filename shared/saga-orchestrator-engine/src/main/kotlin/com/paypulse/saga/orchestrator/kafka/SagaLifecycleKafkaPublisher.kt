package com.paypulse.saga.orchestrator.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.saga.model.SagaLifecycleEvent
import com.paypulse.saga.model.SagaStatus
import com.paypulse.saga.model.StepStatus
import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class SagaLifecycleKafkaPublisher(
  @Qualifier("sagaEventKafkaTemplate") private val kafkaTemplate: KafkaTemplate<String, String>,
  private val objectMapper: ObjectMapper,
  @Value("\${paypulse.saga.events-topic:saga.events}") private val topic: String,
) {
  private val log = LoggerFactory.getLogger(SagaLifecycleKafkaPublisher::class.java)

  fun publishStarted(instance: SagaInstanceEntity) {
    emit(
      SagaLifecycleEvent(
        sagaId = instance.sagaId.toString(),
        sagaType = instance.sagaType,
        eventType = "SAGA_STARTED",
        status = instance.status,
        paymentId = extractPaymentId(instance.payload),
      )
    )
  }

  fun publishStep(instance: SagaInstanceEntity, step: SagaStepEntity) {
    emit(
      SagaLifecycleEvent(
        sagaId = instance.sagaId.toString(),
        sagaType = instance.sagaType,
        eventType = stepEventType(step.status),
        status = step.status,
        stepName = step.stepName,
        paymentId = extractPaymentId(instance.payload),
        attempt = step.retryCount + 1,
      )
    )
  }

  fun publishTerminal(instance: SagaInstanceEntity) {
    emit(
      SagaLifecycleEvent(
        sagaId = instance.sagaId.toString(),
        sagaType = instance.sagaType,
        eventType = terminalEventType(instance.status),
        status = instance.status,
        stepName = instance.currentStep,
        paymentId = extractPaymentId(instance.payload),
      )
    )
  }

  private fun stepEventType(stepStatus: String): String = when (stepStatus) {
    StepStatus.COMPLETED.name -> "STEP_COMPLETED"
    StepStatus.COMPENSATED.name -> "STEP_COMPENSATED"
    StepStatus.FAILED.name -> "STEP_FAILED"
    else -> "STEP_UPDATE"
  }

  private fun terminalEventType(sagaStatus: String): String = when (sagaStatus) {
    SagaStatus.COMPLETED.name -> "SAGA_COMPLETED"
    SagaStatus.COMPENSATED.name -> "SAGA_COMPENSATED"
    SagaStatus.FAILED.name -> "SAGA_FAILED"
    else -> "SAGA_UPDATE"
  }

  private fun extractPaymentId(payload: String?): String? {
    if (payload.isNullOrBlank()) return null
    return runCatching {
      objectMapper.readTree(payload).path("paymentId").let { if (it.isMissingNode || it.isNull) null else it.asText() }
    }.getOrNull()
  }

  private fun emit(event: SagaLifecycleEvent) {
    try {
      val json = objectMapper.writeValueAsString(event)
      kafkaTemplate.send(topic, event.sagaId, json)
    } catch (e: Exception) {
      log.warn("Failed to publish saga lifecycle event sagaId={} type={}: {}", event.sagaId, event.eventType, e.message)
    }
  }
}
