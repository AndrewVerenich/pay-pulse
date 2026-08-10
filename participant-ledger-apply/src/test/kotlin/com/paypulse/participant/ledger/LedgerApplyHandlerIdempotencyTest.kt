package com.paypulse.participant.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

class LedgerApplyHandlerIdempotencyTest {

  private val objectMapper = ObjectMapper().registerKotlinModule()

  private fun command(sagaId: UUID): SagaCommand {
    val paymentId = UUID.randomUUID()
    val payload = objectMapper.writeValueAsString(
      mapOf(
        "paymentId" to paymentId,
        "accountId" to "acc-1",
        "amount" to "99.50",
        "currency" to "USD",
        "riskScore" to 0.1,
      ),
    )
    return SagaCommand(
      sagaId = sagaId,
      sagaType = "PaymentSaga",
      stepName = "LEDGER_APPLY",
      payload = payload,
    )
  }

  @Test
  fun `first command writes event_store and persists reply`() {
    val sagaId = UUID.randomUUID()
    val processed = mock<ProcessedCommandRepository>()
    val eventStore = mock<EventStoreRepository>()
    whenever(processed.findReply(any(), any())).thenReturn(Mono.empty())
    whenever(processed.save(any(), any(), any())).thenReturn(Mono.empty())
    whenever(eventStore.findMaxVersionByAggregateId(any())).thenReturn(Mono.just(1))
    whenever(eventStore.save(any())).thenAnswer { Mono.just(it.arguments[0]) }

    val handler = LedgerApplyHandler(objectMapper, processed, eventStore)

    StepVerifier.create(handler.handle(command(sagaId)))
      .assertNext { reply ->
        assertEquals(ReplyStatus.SUCCESS, reply.status)
        assertEquals("LEDGER_APPLY", reply.stepName)
      }
      .verifyComplete()

    verify(eventStore, times(1)).save(any())
    verify(processed, times(1)).save(eq(sagaId), eq("LEDGER_APPLY"), any())
  }

  @Test
  fun `replayed command returns cached reply without second event_store write`() {
    val sagaId = UUID.randomUUID()
    val cachedReply = objectMapper.writeValueAsString(
      com.paypulse.saga.model.SagaReply(
        sagaId = sagaId,
        stepName = "LEDGER_APPLY",
        status = ReplyStatus.SUCCESS,
        payload = objectMapper.writeValueAsString(mapOf("ledgerEventId" to UUID.randomUUID().toString())),
      ),
    )
    val processed = mock<ProcessedCommandRepository>()
    val eventStore = mock<EventStoreRepository>()
    whenever(processed.findReply(eq(sagaId), eq("LEDGER_APPLY"))).thenReturn(Mono.just(cachedReply))

    val handler = LedgerApplyHandler(objectMapper, processed, eventStore)

    StepVerifier.create(handler.handle(command(sagaId)))
      .assertNext { reply ->
        assertEquals(ReplyStatus.SUCCESS, reply.status)
        assertEquals("LEDGER_APPLY", reply.stepName)
      }
      .verifyComplete()

    verify(eventStore, never()).save(any())
    verify(eventStore, never()).findMaxVersionByAggregateId(any())
    verify(processed, never()).save(any(), any(), any())
  }
}
