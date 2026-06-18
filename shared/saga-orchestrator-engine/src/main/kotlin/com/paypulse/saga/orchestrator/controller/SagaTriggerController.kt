package com.paypulse.saga.orchestrator.controller

import com.paypulse.saga.orchestrator.engine.SagaOrchestrator
import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/sagas")
@CrossOrigin(origins = ["*"])
class SagaTriggerController(
  private val sagaOrchestrator: SagaOrchestrator,
  private val objectMapper: ObjectMapper
) {

  @PostMapping("/{sagaType}")
  @ResponseStatus(HttpStatus.CREATED)
  fun startSaga(
    @PathVariable sagaType: String,
    @RequestBody payload: Map<String, Any>
  ): Mono<SagaInstanceResponse> {
    val payloadJson = objectMapper.writeValueAsString(payload)
    return sagaOrchestrator.startSaga(sagaType, payloadJson)
      .map { it.toResponse() }
  }
}

data class SagaInstanceResponse(
  val sagaId: String,
  val sagaType: String,
  val status: String,
  val currentStep: String?,
  val createdAt: String
)

fun SagaInstanceEntity.toResponse() = SagaInstanceResponse(
  sagaId = sagaId.toString(),
  sagaType = sagaType,
  status = status,
  currentStep = currentStep,
  createdAt = createdAt.toString()
)
