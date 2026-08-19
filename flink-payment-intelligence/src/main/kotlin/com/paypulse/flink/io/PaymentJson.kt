package com.paypulse.flink.io

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.paypulse.flink.model.FraudRule
import com.paypulse.flink.model.PaymentEvent
import com.paypulse.flink.model.UserRiskProfile
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Чистый (без Flink-зависимостей) парсинг JSON-сообщений. Бросает исключение на некорректный вход,
 * чтобы вызывающий Flink-оператор мог отправить запись в dead-letter side output.
 */
object PaymentJson {
  val mapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  fun parsePayment(json: String): PaymentEvent {
    val node = mapper.readTree(json)
    return PaymentEvent(
      eventId = node.requireText("eventId"),
      paymentId = node.requireText("paymentId"),
      accountId = node.requireText("accountId"),
      amount = node.path("amount").let {
        require(!it.isMissingNode && !it.isNull) { "missing amount" }
        it.asDouble()
      },
      currency = node.optText("currency") ?: "UNKNOWN",
      merchantId = node.optText("merchantId"),
      occurredAtEpochMs = parseEpochMs(node.optText("occurredAt")),
    )
  }

  fun parseRule(json: String): FraudRule = mapper.readValue(json, FraudRule::class.java)

  fun parseProfile(json: String): UserRiskProfile = mapper.readValue(json, UserRiskProfile::class.java)

  private fun parseEpochMs(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
      .recoverCatching { Instant.parse(value).toEpochMilli() }
      .getOrDefault(System.currentTimeMillis())
  }

  private fun JsonNode.requireText(field: String): String =
    optText(field) ?: error("missing or blank field: $field")

  private fun JsonNode.optText(field: String): String? {
    val v = path(field)
    return if (v.isMissingNode || v.isNull || !v.isTextual || v.asText().isBlank()) null else v.asText()
  }
}
