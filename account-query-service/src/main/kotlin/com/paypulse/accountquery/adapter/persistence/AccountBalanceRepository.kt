package com.paypulse.accountquery.adapter.persistence

import org.springframework.data.annotation.Id
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
  fun findByAccountIdAndCurrency(accountId: String, currency: String): Mono<AccountBalanceRow>
}
