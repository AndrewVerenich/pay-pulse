package com.paypulse.accountquery.application

import com.paypulse.accountquery.adapter.`in`.BalanceResponse
import com.paypulse.accountquery.adapter.persistence.BalanceReadRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Service
class BalanceQueryService(
  private val balanceReadRepository: BalanceReadRepository,
) {

  fun getBalance(accountId: String, currency: String, at: OffsetDateTime?): Mono<BalanceResponse> =
    if (at == null) {
      balanceReadRepository.currentBalance(accountId, currency)
        .map { (bal, ts) -> BalanceResponse(accountId, currency, bal, ts) }
    } else {
      balanceReadRepository.balanceAt(accountId, currency, at)
        .map { (bal, ts) -> BalanceResponse(accountId, currency, bal, ts) }
    }
}
