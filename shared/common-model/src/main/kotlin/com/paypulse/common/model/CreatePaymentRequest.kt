package com.paypulse.common.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.UUID

data class CreatePaymentRequest(
  @field:NotBlank val accountId: String,
  @field:Positive val amount: BigDecimal,
  @field:NotBlank val currency: String,
  @JsonProperty("merchantId") val merchantId: String? = null,
)

data class CreatePaymentResponse(
  val paymentId: UUID,
  val aggregateVersion: Int,
  val status: String,
  val sagaId: UUID,
)
