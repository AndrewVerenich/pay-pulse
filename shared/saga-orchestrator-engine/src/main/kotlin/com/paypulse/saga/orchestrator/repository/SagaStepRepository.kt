package com.paypulse.saga.orchestrator.repository

import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SagaStepRepository : ReactiveCrudRepository<SagaStepEntity, Long> {

  @Query(
    """
    UPDATE saga.saga_step
    SET status = :status,
        command_payload = CAST(:commandPayload AS jsonb),
        started_at = :startedAt
    WHERE id = :id
    """
  )
  fun markExecutingWithCommandPayload(
    id: Long,
    status: String,
    commandPayload: String,
    startedAt: java.time.LocalDateTime
  ): Mono<Int>

  @Query(
    """
    UPDATE saga.saga_step
    SET status = :status,
        started_at = :startedAt
    WHERE id = :id
    """
  )
  fun markCompensating(
    id: Long,
    status: String,
    startedAt: java.time.LocalDateTime
  ): Mono<Int>

  @Query(
    """
    UPDATE saga.saga_step
    SET status = :status,
        retry_count = :retryCount,
        started_at = :startedAt,
        error_message = :errorMessage
    WHERE id = :id
    """
  )
  fun markRetrying(
    id: Long,
    status: String,
    retryCount: Int,
    startedAt: java.time.LocalDateTime,
    errorMessage: String?
  ): Mono<Int>

  @Query(
    """
    UPDATE saga.saga_step
    SET status = :status,
        reply_payload = CASE WHEN :replyPayload IS NULL THEN NULL ELSE CAST(:replyPayload AS jsonb) END,
        error_message = :errorMessage,
        completed_at = :completedAt
    WHERE id = :id
    """
  )
  fun markCompletedFromReply(
    id: Long,
    status: String,
    replyPayload: String?,
    errorMessage: String?,
    completedAt: java.time.LocalDateTime
  ): Mono<Int>

  fun findBySagaInstanceIdOrderByStepOrder(sagaInstanceId: Long): Flux<SagaStepEntity>

  fun findBySagaInstanceIdAndStepName(sagaInstanceId: Long, stepName: String): Mono<SagaStepEntity>

  @Query(
    """
    SELECT ss.* FROM saga.saga_step ss 
    JOIN saga.saga_instance si ON ss.saga_instance_id = si.id 
    WHERE ss.status = 'EXECUTING' 
      AND ss.started_at < NOW() - INTERVAL '1 second' * :timeoutSeconds
    """
  )
  fun findTimedOutSteps(timeoutSeconds: Int): Flux<SagaStepEntity>
}
