package com.paypulse.saga.orchestrator.sse

import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Component
class SagaEventPublisher(
  private val objectMapper: ObjectMapper
) {
  private val log = LoggerFactory.getLogger(SagaEventPublisher::class.java)

  private val sink: Sinks.Many<SagaSseEvent> = Sinks.many().multicast().onBackpressureBuffer()

  fun publishSagaEvent(instance: SagaInstanceEntity) {
    val event = SagaSseEvent(
      type = "SAGA_UPDATE",
      sagaId = instance.sagaId.toString(),
      sagaType = instance.sagaType,
      status = instance.status,
      payload = objectMapper.writeValueAsString(instance)
    )
    emit(event)
  }

  fun publishStepEvent(instance: SagaInstanceEntity, step: SagaStepEntity) {
    val event = SagaSseEvent(
      type = "STEP_UPDATE",
      sagaId = instance.sagaId.toString(),
      sagaType = instance.sagaType,
      status = step.status,
      stepName = step.stepName,
      payload = objectMapper.writeValueAsString(step)
    )
    emit(event)
  }

  fun stream(): Flux<SagaSseEvent> = sink.asFlux()

  private fun emit(event: SagaSseEvent) {
    val result = sink.tryEmitNext(event)
    if (result.isFailure) {
      log.warn("Failed to emit SSE event: {}", result)
    }
  }
}

data class SagaSseEvent(
  val type: String,
  val sagaId: String,
  val sagaType: String,
  val status: String,
  val stepName: String? = null,
  val payload: String
)
