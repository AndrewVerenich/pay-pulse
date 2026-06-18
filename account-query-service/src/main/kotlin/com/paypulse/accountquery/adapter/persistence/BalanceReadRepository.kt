package com.paypulse.accountquery.adapter.persistence

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime

@Repository
class BalanceReadRepository(
  private val databaseClient: DatabaseClient,
) {

  /** Pair(balance, asOf) */
  fun currentBalance(accountId: String, currency: String): Mono<Pair<BigDecimal, OffsetDateTime?>> =
    databaseClient.sql(
      """
      SELECT balance, last_occurred_at
      FROM account_query.account_balance
      WHERE account_id = :accountId AND currency = :currency
      """.trimIndent(),
    )
      .bind("accountId", accountId)
      .bind("currency", currency)
      .map { row, _ ->
        Pair(
          row.get("balance", BigDecimal::class.java)!!,
          row.get("last_occurred_at", OffsetDateTime::class.java),
        )
      }
      .one()
      .switchIfEmpty(
        Mono.just(Pair(BigDecimal.ZERO, null)),
      )

  fun balanceAt(accountId: String, currency: String, at: OffsetDateTime): Mono<Pair<BigDecimal, OffsetDateTime>> =
    databaseClient.sql(
      """
      SELECT balance_after, occurred_at
      FROM account_query.balance_events
      WHERE account_id = :accountId AND currency = :currency AND occurred_at <= :at
      ORDER BY occurred_at DESC, id DESC
      LIMIT 1
      """.trimIndent(),
    )
      .bind("accountId", accountId)
      .bind("currency", currency)
      .bind("at", at)
      .map { row, _ ->
        Pair(
          row.get("balance_after", BigDecimal::class.java)!!,
          row.get("occurred_at", OffsetDateTime::class.java)!!,
        )
      }
      .one()
      .switchIfEmpty(
        Mono.just(Pair(BigDecimal.ZERO, at)),
      )
}
