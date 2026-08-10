package com.paypulse.saga.orchestrator.repository

import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

interface SagaInstanceRepository : ReactiveCrudRepository<SagaInstanceEntity, Long> {

  @Query(
    """
    INSERT INTO saga.saga_instance (saga_id, saga_type, status, current_step, payload, created_at, updated_at, completed_at)
    VALUES (:sagaId, :sagaType, :status, :currentStep, CAST(:payload AS jsonb), :createdAt, :updatedAt, :completedAt)
    RETURNING *
    """
  )
  fun insertWithJsonb(
    sagaId: UUID,
    sagaType: String,
    status: String,
    currentStep: String?,
    payload: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    completedAt: LocalDateTime? = null
  ): Mono<SagaInstanceEntity>

  @Query(
    """
    UPDATE saga.saga_instance
    SET status = :status,
        current_step = :currentStep,
        updated_at = :updatedAt,
        completed_at = :completedAt
    WHERE id = :id
    """
  )
  fun updateState(
    id: Long,
    status: String,
    currentStep: String?,
    updatedAt: LocalDateTime,
    completedAt: LocalDateTime? = null
  ): Mono<Int>

  @Query(
    """
    UPDATE saga.saga_instance
    SET payload = CAST(:payload AS jsonb),
        updated_at = :updatedAt
    WHERE id = :id
    RETURNING *
    """
  )
  fun updatePayloadWithJsonb(
    id: Long,
    payload: String,
    updatedAt: LocalDateTime
  ): Mono<SagaInstanceEntity>

  fun findBySagaId(sagaId: UUID): Mono<SagaInstanceEntity>

  fun findByStatus(status: String): Flux<SagaInstanceEntity>

  fun findBySagaType(sagaType: String): Flux<SagaInstanceEntity>

  @Query("SELECT * FROM saga.saga_instance ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
  fun findAllPaged(limit: Int, offset: Int): Flux<SagaInstanceEntity>

  @Query(
    """
    SELECT * FROM saga.saga_instance 
    WHERE (:status IS NULL OR status = :status) 
      AND (:sagaType IS NULL OR saga_type = :sagaType) 
    ORDER BY created_at DESC 
    LIMIT :limit OFFSET :offset
    """
  )
  fun findFiltered(status: String?, sagaType: String?, limit: Int, offset: Int): Flux<SagaInstanceEntity>

  @Query("SELECT COUNT(*) FROM saga.saga_instance WHERE status = :status")
  fun countByStatus(status: String): Mono<Long>

  @Query(
    """
    SELECT * FROM saga.saga_instance
    WHERE status IN ('FAILED', 'COMPENSATING')
    ORDER BY updated_at DESC
    LIMIT :limit
    """,
  )
  fun findProblematic(limit: Int): Flux<SagaInstanceEntity>
}
