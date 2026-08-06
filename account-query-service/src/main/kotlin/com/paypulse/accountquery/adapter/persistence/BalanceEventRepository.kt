package com.paypulse.accountquery.adapter.persistence

import org.springframework.data.annotation.Id
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
    SELECT id, source_event_id, account_id, currency, delta, balance_after, occurred_at, aggregate_id
      FROM account_query.balance_events
     WHERE account_id = :accountId AND currency = :currency AND occurred_at <= :at
     ORDER BY occurred_at DESC, id DESC
     LIMIT 1
    """,
  )
  fun findLatestAt(accountId: String, currency: String, at: OffsetDateTime): Mono<BalanceEventRow>
}
