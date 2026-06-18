package com.paypulse.projection.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Service
class BalanceProjectionService(
  private val jdbc: JdbcTemplate,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(BalanceProjectionService::class.java)

  @Transactional
  fun handlePaymentEvent(rawJson: String) {
    val node = objectMapper.readTree(rawJson)
    val event = parse(node)

    val reserved = jdbc.update(
      """
      INSERT INTO account_query.balance_events
        (source_event_id, account_id, currency, delta, balance_after, occurred_at, aggregate_id)
      VALUES (?, ?, ?, ?, 0, ?, ?)
      ON CONFLICT (source_event_id) DO NOTHING
      """.trimIndent(),
      event.eventId,
      event.accountId,
      event.currency,
      event.amount,
      event.occurredAt,
      event.paymentId,
    )
    if (reserved == 0) {
      log.debug("Skip duplicate event eventId={}", event.eventId)
      return
    }

    val newBalance: BigDecimal = jdbc.queryForObject(
      """
      INSERT INTO account_query.account_balance
        (account_id, currency, balance, last_occurred_at, last_source_event_id)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (account_id, currency) DO UPDATE SET
        balance = account_query.account_balance.balance + EXCLUDED.balance,
        last_occurred_at = GREATEST(account_query.account_balance.last_occurred_at, EXCLUDED.last_occurred_at),
        last_source_event_id = EXCLUDED.last_source_event_id
      RETURNING balance
      """.trimIndent(),
      BigDecimal::class.java,
      event.accountId,
      event.currency,
      event.amount,
      event.occurredAt,
      event.eventId,
    )

    jdbc.update(
      """
      UPDATE account_query.balance_events
      SET balance_after = ?
      WHERE source_event_id = ?
      """.trimIndent(),
      newBalance,
      event.eventId,
    )

    log.debug(
      "Balance projection eventId={} account={} delta={} balance_after={}",
      event.eventId,
      event.accountId,
      event.amount,
      newBalance,
    )
  }

  private fun parse(node: JsonNode): PaymentInitiatedEvent {
    val eventId = requireText(node, "eventId")
    val paymentId = UUID.fromString(requireText(node, "paymentId"))
    val accountId = requireText(node, "accountId")
    val currency = requireText(node, "currency")
    val amount = node.path("amount").let {
      if (it.isMissingNode || it.isNull) error("missing amount")
      it.decimalValue() ?: error("amount is not a number")
    }
    val occurredAt = OffsetDateTime.parse(requireText(node, "occurredAt"))
    return PaymentInitiatedEvent(
      eventId = eventId,
      paymentId = paymentId,
      accountId = accountId,
      currency = currency,
      amount = amount,
      occurredAt = occurredAt,
    )
  }

  private fun requireText(node: JsonNode, field: String): String {
    val value = node.path(field)
    if (value.isMissingNode || value.isNull || !value.isTextual || value.asText().isBlank()) {
      error("missing or blank field: $field")
    }
    return value.asText()
  }

  private data class PaymentInitiatedEvent(
    val eventId: String,
    val paymentId: UUID,
    val accountId: String,
    val currency: String,
    val amount: BigDecimal,
    val occurredAt: OffsetDateTime,
  )
}
