package com.paypulse.accountquery.adapter.`in`

import java.math.BigDecimal
import java.time.OffsetDateTime

data class BalanceResponse(
  val accountId: String,
  val currency: String,
  val balance: BigDecimal,
  val asOf: OffsetDateTime?,
)
