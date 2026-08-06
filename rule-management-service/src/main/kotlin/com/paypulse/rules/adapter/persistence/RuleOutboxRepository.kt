package com.paypulse.rules.adapter.persistence

import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface RuleOutboxRepository : ReactiveCrudRepository<RuleOutboxRow, Long> {

  @Query(
    """
    INSERT INTO rule_management.outbox (partitioning_key, type, payload)
    VALUES (:partitioningKey, :type, CAST(:payload AS jsonb))
    RETURNING id
    """,
  )
  fun insertReturningId(partitioningKey: String, type: String, payload: String): Mono<Long>

  @Modifying
  @Query("DELETE FROM rule_management.outbox WHERE id = :id")
  fun deleteOutboxById(id: Long): Mono<Long>
}
