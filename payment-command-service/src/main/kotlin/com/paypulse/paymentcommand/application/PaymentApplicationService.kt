package com.paypulse.paymentcommand.application

import com.paypulse.common.model.CreatePaymentRequest
import com.paypulse.common.model.CreatePaymentResponse
import com.paypulse.paymentcommand.adapter.persistence.EventStoreRepository
import com.paypulse.paymentcommand.adapter.persistence.EventStoreRow
import com.paypulse.paymentcommand.adapter.persistence.IdempotencyRepository
import com.paypulse.paymentcommand.application.domain.PaymentInitiatedPayload
import com.paypulse.outbox.publisher.OutboxPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID

@Service
class PaymentApplicationService(
  private val eventStoreRepository: EventStoreRepository,
  private val idempotencyRepository: IdempotencyRepository,
  private val transactionalOperator: TransactionalOperator,
  private val objectMapper: ObjectMapper,
  private val meterRegistry: MeterRegistry,
  private val outboxPublisher: OutboxPublisher,
) {

  fun createPayment(
    request: CreatePaymentRequest,
    idempotencyKey: String?,
    canonicalRequestJson: String,
  ): Mono<CreatePaymentResponse> {
    if (idempotencyKey.isNullOrBlank()) {
      return transactionalOperator.transactional(insertPayment(request, null, null))
    }
    val keyHash = sha256Hex(idempotencyKey)
    val requestHash = sha256Hex(canonicalRequestJson)
    return idempotencyRepository.findById(keyHash)
      .flatMap { row ->
        if (row.requestHash != requestHash) {
          Mono.error(IdempotencyConflictException())
        } else {
          Mono.fromCallable { objectMapper.readValue<CreatePaymentResponse>(row.responseBody) }
            .doOnSuccess { meterRegistry.counter("paypulse_payments_total", "result", "idempotent_hit").increment() }
        }
      }
      .switchIfEmpty(transactionalOperator.transactional(insertPayment(request, keyHash, requestHash)))
  }

  private fun insertPayment(
    request: CreatePaymentRequest,
    idempotencyKeyHash: String?,
    requestHash: String?,
  ): Mono<CreatePaymentResponse> {
    return Mono.defer {
      val paymentId = UUID.randomUUID()
      val sagaId = UUID.randomUUID()
      val eventId = UUID.randomUUID()
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val amount = request.amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP)

      eventStoreRepository.findMaxVersionByAggregateId(paymentId)
        .defaultIfEmpty(0)
        .flatMap { currentVersion ->
          val nextVersion = currentVersion + 1
          val payload = PaymentInitiatedPayload(
            eventId = eventId,
            paymentId = paymentId,
            accountId = request.accountId,
            amount = amount,
            currency = request.currency,
            merchantId = request.merchantId,
            occurredAt = now,
          )
          val payloadJson = objectMapper.writeValueAsString(payload)
          val event = EventStoreRow(
            aggregateId = paymentId,
            aggregateType = "Payment",
            eventType = "PaymentInitiatedV1",
            version = nextVersion,
            accountId = request.accountId,
            payload = payloadJson,
            occurredAt = now,
          )
          val response = CreatePaymentResponse(
            paymentId = paymentId,
            aggregateVersion = nextVersion,
            status = "INITIATED",
            sagaId = sagaId,
          )
          val sagaStartPayload = mapOf(
            "sagaId" to sagaId,
            "sagaType" to "PaymentSaga",
            "paymentId" to paymentId,
            "accountId" to request.accountId,
            "amount" to amount,
            "currency" to request.currency,
            "merchantId" to request.merchantId,
          )
          eventStoreRepository.save(event)
            .onErrorResume(DuplicateKeyException::class.java) {
              Mono.error(OptimisticConcurrencyException(paymentId, nextVersion))
            }
            .then(
              outboxPublisher.publish(
                partitioningKey = sagaId.toString(),
                eventType = "SagaStart:PaymentSaga",
                payloadJson = objectMapper.writeValueAsString(sagaStartPayload),
              ),
            )
            .then(
              Mono.defer {
                if (idempotencyKeyHash == null || requestHash == null) {
                  Mono.just(response)
                } else {
                  val body = objectMapper.writeValueAsString(response)
                  idempotencyRepository.insert(
                    keyHash = idempotencyKeyHash,
                    requestHash = requestHash,
                    responseBody = body,
                    expiresAt = now.plusDays(1),
                  ).thenReturn(response)
                }
              },
            )
        }
        .doOnSuccess { meterRegistry.counter("paypulse_payments_total", "result", "created").increment() }
    }
  }

  private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
    return HexFormat.of().formatHex(digest)
  }

  private companion object {
    const val MONEY_SCALE = 2
  }
}

class IdempotencyConflictException : RuntimeException("Idempotency-Key reused with different request body")

class OptimisticConcurrencyException(aggregateId: UUID, version: Int) :
  RuntimeException("Optimistic concurrency conflict on aggregate $aggregateId @ version=$version")
