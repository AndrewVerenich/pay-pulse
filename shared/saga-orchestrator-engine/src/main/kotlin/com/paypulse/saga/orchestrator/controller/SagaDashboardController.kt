package com.paypulse.saga.orchestrator.controller

import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import com.paypulse.saga.orchestrator.repository.SagaInstanceRepository
import com.paypulse.saga.orchestrator.repository.SagaStepRepository
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sagas")
@CrossOrigin(origins = ["*"])
class SagaDashboardController(
  private val instanceRepository: SagaInstanceRepository,
  private val stepRepository: SagaStepRepository
) {

  @GetMapping
  fun listSagas(
    @RequestParam(required = false) status: String?,
    @RequestParam(required = false) sagaType: String?,
    @RequestParam(defaultValue = "50") limit: Int,
    @RequestParam(defaultValue = "0") offset: Int
  ): Flux<SagaListItem> {
    return instanceRepository.findFiltered(status, sagaType, limit, offset)
      .map { it.toListItem() }
  }

  @GetMapping("/{sagaId}")
  fun getSagaDetail(@PathVariable sagaId: UUID): Mono<SagaDetailResponse> {
    return instanceRepository.findBySagaId(sagaId)
      .flatMap { instance ->
        stepRepository.findBySagaInstanceIdOrderByStepOrder(instance.id!!)
          .collectList()
          .map { steps ->
            SagaDetailResponse(
              sagaId = instance.sagaId.toString(),
              sagaType = instance.sagaType,
              status = instance.status,
              currentStep = instance.currentStep,
              payload = instance.payload,
              createdAt = instance.createdAt.toString(),
              updatedAt = instance.updatedAt.toString(),
              completedAt = instance.completedAt?.toString(),
              steps = steps.map { it.toStepResponse() }
            )
          }
      }
  }

  @GetMapping("/{sagaId}/steps")
  fun getSagaSteps(@PathVariable sagaId: UUID): Flux<StepResponse> {
    return instanceRepository.findBySagaId(sagaId)
      .flatMapMany { instance ->
        stepRepository.findBySagaInstanceIdOrderByStepOrder(instance.id!!)
      }
      .map { it.toStepResponse() }
  }

  @GetMapping("/stats")
  fun getStats(): Mono<SagaStats> {
    return Mono.zip(
      instanceRepository.count(),
      instanceRepository.countByStatus("STARTED")
        .zipWith(instanceRepository.countByStatus("EXECUTING"))
        .map { it.t1 + it.t2 },
      instanceRepository.countByStatus("COMPLETED"),
      instanceRepository.countByStatus("COMPENSATED"),
      instanceRepository.countByStatus("FAILED")
    ).map { tuple ->
      SagaStats(
        total = tuple.t1,
        active = tuple.t2,
        completed = tuple.t3,
        compensated = tuple.t4,
        failed = tuple.t5
      )
    }
  }
}

data class SagaListItem(
  val sagaId: String,
  val sagaType: String,
  val status: String,
  val currentStep: String?,
  val createdAt: String,
  val completedAt: String?
)

data class SagaDetailResponse(
  val sagaId: String,
  val sagaType: String,
  val status: String,
  val currentStep: String?,
  val payload: String,
  val createdAt: String,
  val updatedAt: String,
  val completedAt: String?,
  val steps: List<StepResponse>
)

data class StepResponse(
  val stepName: String,
  val stepType: String,
  val stepOrder: Int,
  val status: String,
  val commandPayload: String?,
  val replyPayload: String?,
  val errorMessage: String?,
  val retryCount: Int,
  val startedAt: String?,
  val completedAt: String?
)

data class SagaStats(
  val total: Long,
  val active: Long,
  val completed: Long,
  val compensated: Long,
  val failed: Long
)

fun SagaInstanceEntity.toListItem() = SagaListItem(
  sagaId = sagaId.toString(),
  sagaType = sagaType,
  status = status,
  currentStep = currentStep,
  createdAt = createdAt.toString(),
  completedAt = completedAt?.toString()
)

fun SagaStepEntity.toStepResponse() = StepResponse(
  stepName = stepName,
  stepType = stepType,
  stepOrder = stepOrder,
  status = status,
  commandPayload = commandPayload,
  replyPayload = replyPayload,
  errorMessage = errorMessage,
  retryCount = retryCount,
  startedAt = startedAt?.toString(),
  completedAt = completedAt?.toString()
)
