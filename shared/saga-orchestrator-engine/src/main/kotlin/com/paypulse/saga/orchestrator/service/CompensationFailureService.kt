package com.paypulse.saga.orchestrator.service

import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.kafka.SagaCompensationFailurePublisher
import com.paypulse.saga.orchestrator.repository.CompensationFailureRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class CompensationFailureService(
  private val repository: CompensationFailureRepository,
  private val publisher: SagaCompensationFailurePublisher,
) {

  fun record(instance: SagaInstanceEntity, reason: String): Mono<Void> =
    repository.upsert(instance.sagaId, reason, instance.payload)
      .doOnSuccess { publisher.publish(instance, reason) }
      .then()

  fun markResolved(sagaId: java.util.UUID): Mono<Void> =
    repository.markResolved(sagaId).then()
}
