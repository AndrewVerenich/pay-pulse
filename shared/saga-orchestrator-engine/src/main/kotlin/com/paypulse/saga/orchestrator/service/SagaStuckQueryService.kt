package com.paypulse.saga.orchestrator.service

import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.repository.CompensationFailureRepository
import com.paypulse.saga.orchestrator.repository.SagaInstanceRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

data class StuckSagaItem(
  val sagaId: String,
  val sagaType: String,
  val status: String,
  val currentStep: String?,
  val reason: String,
  val createdAt: String,
  val resolved: Boolean,
)

@Service
class SagaStuckQueryService(
  private val compensationFailureRepository: CompensationFailureRepository,
  private val instanceRepository: SagaInstanceRepository,
) {

  fun listStuck(limit: Int = 100): Flux<StuckSagaItem> =
    compensationFailureRepository.findUnresolved()
      .take(limit.toLong())
      .flatMap { failure ->
        instanceRepository.findBySagaId(failure.sagaId)
          .map { instance -> failure.toItem(instance) }
          .defaultIfEmpty(failure.toItem(null))
      }
      .concatWith(problematicWithoutFailureRecord(limit))

  private fun problematicWithoutFailureRecord(limit: Int): Flux<StuckSagaItem> =
    instanceRepository.findProblematic(limit)
      .filterWhen { instance ->
        compensationFailureRepository.existsById(instance.sagaId).map { exists -> !exists }
      }
      .map { it.toStuckItem() }

  private fun com.paypulse.saga.orchestrator.entity.CompensationFailureEntity.toItem(
    instance: SagaInstanceEntity?,
  ) = StuckSagaItem(
    sagaId = sagaId.toString(),
    sagaType = instance?.sagaType ?: "unknown",
    status = instance?.status ?: "FAILED",
    currentStep = instance?.currentStep,
    reason = reason,
    createdAt = createdAt.toString(),
    resolved = resolved,
  )

  private fun SagaInstanceEntity.toStuckItem() = StuckSagaItem(
    sagaId = sagaId.toString(),
    sagaType = sagaType,
    status = status,
    currentStep = currentStep,
    reason = "Saga in $status state",
    createdAt = updatedAt.toString(),
    resolved = false,
  )
}
