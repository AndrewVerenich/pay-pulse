package com.paypulse.rules.adapter.`in`

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class CreateFraudRuleRequest(
  @field:NotBlank @field:Size(min = 3, max = 128) val name: String,
  val enabled: Boolean = true,
  @field:NotBlank @param:JsonProperty("json_spec") @get:JsonProperty("json_spec") val jsonSpec: String,
)

data class UpdateFraudRuleRequest(
  @field:NotBlank @field:Size(min = 3, max = 128) val name: String,
  val enabled: Boolean = true,
  @field:NotBlank @param:JsonProperty("json_spec") @get:JsonProperty("json_spec") val jsonSpec: String,
)

data class FraudRuleDto(
  val id: String,
  val name: String,
  val enabled: Boolean,
  @get:JsonProperty("json_spec") val jsonSpec: String,
  val version: Long,
  val updatedAt: OffsetDateTime?,
)
