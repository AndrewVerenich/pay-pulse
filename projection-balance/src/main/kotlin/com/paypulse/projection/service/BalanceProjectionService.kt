package com.paypulse.projection.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.projection.adapter.persistence.AccountBalanceRepository
import com.paypulse.projection.adapter.persistence.BalanceEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

interface BalanceProjectionService {
  fun handlePaymentEvent(rawJson: String): Mono<Void>
}

@Service
class DefaultBalanceProjectionService(
  private val balanceEventRepository: BalanceEventRepository,
  private val accountBalanceRepository: AccountBalanceRepository,
  private val transactionalOperator: TransactionalOperator,
  private val objectMapper: ObjectMapper,
) : BalanceProjectionService {
  private val log = LoggerFactory.getLogger(DefaultBalanceProjectionService::class.java)

  override fun handlePaymentEvent(rawJson: String): Mono<Void> {
    val event = parse(objectMapper.readTree(rawJson))
    val flow = balanceEventRepository.tryInsertEvent(
      sourceEventId = event.eventId,
      accountId = event.accountId,
      currency = event.currency,
      delta = event.amount,
      occurredAt = event.occurredAt,
      aggregateId = event.paymentId,
    )
      .flatMap { _ ->
        accountBalanceRepository.upsertReturningBalance(
          accountId = event.accountId,
          currency = event.currency,
          delta = event.amount,
          occurredAt = event.occurredAt,
          sourceEventId = event.eventId,
        )
      }
      .flatMap { newBalance ->
        balanceEventRepository.updateBalanceAfter(event.eventId, newBalance)
          .doOnSuccess {
            log.debug(
              "Balance projection eventId={} account={} delta={} balance_after={}",
              event.eventId,
              event.accountId,
              event.amount,
              newBalance,
            )
          }
      }
      .switchIfEmpty(
        Mono.fromRunnable {
          log.debug("Skip duplicate event eventId={}", event.eventId)
        },
      )
      .then()

    return transactionalOperator.transactional(flow)
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
