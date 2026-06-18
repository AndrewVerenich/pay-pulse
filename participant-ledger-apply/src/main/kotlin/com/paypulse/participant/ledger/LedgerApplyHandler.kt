package com.paypulse.participant.ledger

import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import com.paypulse.saga.participant.handler.SagaCommandHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class LedgerApplyHandler(
  private val objectMapper: ObjectMapper,
  private val processedCommands: ProcessedCommandRepository,
  private val eventStoreRepository: EventStoreRepository,
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(LedgerApplyHandler::class.java)
  override val commandType = "LEDGER_APPLY"

  override fun handle(command: SagaCommand): Mono<SagaReply> =
    processedCommands.findReply(command.sagaId, command.stepName)
      .map { cached -> objectMapper.readValue(cached, SagaReply::class.java) }
      .switchIfEmpty(execute(command))

  override fun compensate(command: SagaCommand): Mono<SagaReply> =
    Mono.just(
      SagaReply(
        sagaId = command.sagaId,
        stepName = command.stepName,
        status = ReplyStatus.FAILURE,
        errorMessage = "Ledger apply is a pivot step",
        isCompensation = true,
      ),
    )

  private fun execute(command: SagaCommand): Mono<SagaReply> = Mono.defer {
    val payload = objectMapper.readTree(command.payload)
    val paymentId = UUID.fromString(payload.get("paymentId").asText())
    val accountId = payload.get("accountId").asText()
    val amount = BigDecimal(payload.get("amount").asText()).setScale(2, RoundingMode.HALF_UP)
    val currency = payload.get("currency").asText()
    val eventId = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    eventStoreRepository.findMaxVersionByAggregateId(paymentId)
      .defaultIfEmpty(0)
      .flatMap { currentVersion ->
        val nextVersion = currentVersion + 1
        val settledPayload = mapOf(
          "eventId" to eventId,
          "paymentId" to paymentId,
          "accountId" to accountId,
          "amount" to amount,
          "currency" to currency,
          "occurredAt" to now.toString(),
        )
        val event = EventStoreRow(
          aggregateId = paymentId,
          aggregateType = "Payment",
          eventType = "PaymentSettledV1",
          version = nextVersion,
          accountId = accountId,
          payload = objectMapper.writeValueAsString(settledPayload),
          occurredAt = now,
        )
        eventStoreRepository.save(event)
          .onErrorResume(DuplicateKeyException::class.java) {
            Mono.error(IllegalStateException("Optimistic concurrency on payment $paymentId"))
          }
          .map {
            SagaReply(
              sagaId = command.sagaId,
              stepName = command.stepName,
              status = ReplyStatus.SUCCESS,
              payload = objectMapper.writeValueAsString(mapOf("ledgerEventId" to eventId.toString())),
            )
          }
      }
      .flatMap { reply ->
        val replyJson = objectMapper.writeValueAsString(reply)
        processedCommands.save(command.sagaId, command.stepName, replyJson)
          .thenReturn(reply)
          .doOnSuccess { log.info("Ledger applied sagaId={} paymentId={}", command.sagaId, paymentId) }
      }
  }
}
