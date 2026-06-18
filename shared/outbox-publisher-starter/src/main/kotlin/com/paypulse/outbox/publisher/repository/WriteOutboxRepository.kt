package com.paypulse.outbox.publisher.repository

import com.paypulse.outbox.publisher.model.OutboxEvent
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface WriteOutboxRepository : ReactiveCrudRepository<OutboxEvent, Long> {
  @Query(
    """
    INSERT INTO payment_command.outbox (partitioning_key, type, payload)
    VALUES (:partitioningKey, :type, CAST(:payload AS jsonb))
    RETURNING id, partitioning_key, type, payload::text, idempotency_key, status, created_at, processed_at
    """,
  )
  fun saveWithJsonb(
    partitioningKey: String,
    type: String,
    payload: String,
  ): Mono<OutboxEvent>
}
