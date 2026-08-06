package com.paypulse.projection.adapter.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime

@Table(value = "account_balance", schema = "account_query")
data class AccountBalanceRow(
  @Id
  @Column("account_id")
  val accountId: String,
  val currency: String,
  val balance: BigDecimal,
  @Column("last_occurred_at")
  val lastOccurredAt: OffsetDateTime,
  @Column("last_source_event_id")
  val lastSourceEventId: String,
)

interface AccountBalanceRepository : ReactiveCrudRepository<AccountBalanceRow, String> {

  @Query(
    """
    INSERT INTO account_query.account_balance
      (account_id, currency, balance, last_occurred_at, last_source_event_id)
    VALUES (:accountId, :currency, :delta, :occurredAt, :sourceEventId)
    ON CONFLICT (account_id, currency) DO UPDATE SET
      balance = account_query.account_balance.balance + EXCLUDED.balance,
      last_occurred_at = GREATEST(account_query.account_balance.last_occurred_at, EXCLUDED.last_occurred_at),
      last_source_event_id = EXCLUDED.last_source_event_id
    RETURNING balance
    """,
  )
  fun upsertReturningBalance(
    accountId: String,
    currency: String,
    delta: BigDecimal,
    occurredAt: OffsetDateTime,
    sourceEventId: String,
  ): Mono<BigDecimal>

  @Query(
    """
    SELECT max(last_occurred_at)
      FROM account_query.account_balance
    """,
  )
  fun findMaxLastOccurredAt(): Mono<OffsetDateTime>
}
