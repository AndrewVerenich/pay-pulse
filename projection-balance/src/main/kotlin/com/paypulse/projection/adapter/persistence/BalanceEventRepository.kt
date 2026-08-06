package com.paypulse.projection.adapter.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Table(value = "balance_events", schema = "account_query")
data class BalanceEventRow(
  @Id
  val id: Long? = null,
  @Column("source_event_id")
  val sourceEventId: String,
  @Column("account_id")
  val accountId: String,
  val currency: String,
  val delta: BigDecimal,
  @Column("balance_after")
  val balanceAfter: BigDecimal,
  @Column("occurred_at")
  val occurredAt: OffsetDateTime,
  @Column("aggregate_id")
  val aggregateId: UUID,
)

interface BalanceEventRepository : ReactiveCrudRepository<BalanceEventRow, Long> {

  @Query(
    """
    INSERT INTO account_query.balance_events
      (source_event_id, account_id, currency, delta, balance_after, occurred_at, aggregate_id)
    VALUES (:sourceEventId, :accountId, :currency, :delta, 0, :occurredAt, :aggregateId)
    ON CONFLICT (source_event_id) DO NOTHING
    RETURNING id
    """,
  )
  fun tryInsertEvent(
    sourceEventId: String,
    accountId: String,
    currency: String,
    delta: BigDecimal,
    occurredAt: OffsetDateTime,
    aggregateId: UUID,
  ): Mono<Long>

  @Modifying
  @Query(
    """
    UPDATE account_query.balance_events
       SET balance_after = :balanceAfter
     WHERE source_event_id = :sourceEventId
    """,
  )
  fun updateBalanceAfter(sourceEventId: String, balanceAfter: BigDecimal): Mono<Long>
}
