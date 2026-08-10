package com.paypulse.saga.orchestrator.repository

import com.paypulse.saga.orchestrator.entity.CompensationFailureEntity
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface CompensationFailureRepository : ReactiveCrudRepository<CompensationFailureEntity, UUID> {

  @Modifying
  @Query(
    """
    INSERT INTO saga.compensation_failure (saga_id, reason, payload)
    VALUES (:sagaId, :reason, :payload)
    ON CONFLICT (saga_id) DO UPDATE
      SET reason = EXCLUDED.reason,
          payload = EXCLUDED.payload,
          created_at = timezone('utc', now()),
          resolved = false
  """,
  )
  fun upsert(sagaId: UUID, reason: String, payload: String?): Mono<Void>

  @Modifying
  @Query("UPDATE saga.compensation_failure SET resolved = true WHERE saga_id = :sagaId")
  fun markResolved(sagaId: UUID): Mono<Int>

  @Query("SELECT * FROM saga.compensation_failure WHERE resolved = false ORDER BY created_at DESC")
  fun findUnresolved(): Flux<CompensationFailureEntity>
}
