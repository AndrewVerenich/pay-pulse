package com.paypulse.paymentcommand.application.domain

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentInitiatedPayload(
  val eventId: UUID,
  val paymentId: UUID,
  val accountId: String,
  val amount: BigDecimal,
  val currency: String,
  val merchantId: String?,
  val occurredAt: OffsetDateTime,
)
