package com.paypulse.accountquery.application

import com.paypulse.accountquery.adapter.`in`.BalanceResponse
import com.paypulse.accountquery.adapter.persistence.AccountBalanceRepository
import com.paypulse.accountquery.adapter.persistence.BalanceEventRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime

interface BalanceQueryService {
  fun getBalance(accountId: String, currency: String, at: OffsetDateTime?): Mono<BalanceResponse>
}

@Service
class DefaultBalanceQueryService(
  private val accountBalanceRepository: AccountBalanceRepository,
  private val balanceEventRepository: BalanceEventRepository,
) : BalanceQueryService {

  override fun getBalance(accountId: String, currency: String, at: OffsetDateTime?): Mono<BalanceResponse> =
    if (at == null) {
      accountBalanceRepository.findByAccountIdAndCurrency(accountId, currency)
        .map { row -> BalanceResponse(accountId, currency, row.balance, row.lastOccurredAt) }
        .defaultIfEmpty(BalanceResponse(accountId, currency, BigDecimal.ZERO, null))
    } else {
      balanceEventRepository.findLatestAt(accountId, currency, at)
        .map { row -> BalanceResponse(accountId, currency, row.balanceAfter, row.occurredAt) }
        .defaultIfEmpty(BalanceResponse(accountId, currency, BigDecimal.ZERO, at))
    }
}
