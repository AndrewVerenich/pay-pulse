package com.paypulse.paymentcommand.adapter.persistence

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.util.*

interface EventStoreRepository : ReactiveCrudRepository<EventStoreRow, Long> {

  @Query(
    """
    SELECT COALESCE(MAX(version), 0)
      FROM payment_command.event_store
     WHERE aggregate_id = :aggregateId
    """,
  )
  fun findMaxVersionByAggregateId(aggregateId: UUID): Mono<Int>
}
