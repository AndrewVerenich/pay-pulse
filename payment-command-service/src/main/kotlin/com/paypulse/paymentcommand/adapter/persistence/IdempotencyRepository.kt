package com.paypulse.paymentcommand.adapter.persistence

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface IdempotencyRepository : ReactiveCrudRepository<IdempotencyRow, String> {
  @Query(
    """
    INSERT INTO payment_command.idempotency_keys (key_hash, request_hash, response_body, expires_at)
    VALUES (:keyHash, :requestHash, :responseBody, :expiresAt)
    ON CONFLICT (key_hash) DO NOTHING
    RETURNING key_hash
    """,
  )
  fun insert(
    keyHash: String,
    requestHash: String,
    responseBody: String,
    expiresAt: java.time.OffsetDateTime,
  ): Mono<String>
}
