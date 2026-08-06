package com.paypulse.rules

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.rules.adapter.`in`.CreateFraudRuleRequest
import com.paypulse.rules.adapter.persistence.FraudRuleRepository
import com.paypulse.rules.adapter.persistence.FraudRuleRow
import com.paypulse.rules.adapter.persistence.RuleOutboxRepository
import com.paypulse.rules.application.DefaultFraudRuleService
import com.paypulse.rules.application.DefaultRuleSpecValidator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

class FraudRuleServiceTest {

  private val objectMapper = ObjectMapper()
  private val validator = DefaultRuleSpecValidator(objectMapper)
  private val ruleRepository: FraudRuleRepository = mock()
  private val outboxRepository: RuleOutboxRepository = mock()

  private val tx: TransactionalOperator = mock {
    on { transactional(any<Mono<Any>>()) } doAnswer { it.getArgument(0) }
  }

  private val service = DefaultFraudRuleService(ruleRepository, outboxRepository, validator, tx, objectMapper)

  private val spec = """
    {"maxAmount":10000,"velocityWindowMs":3600000,"velocityMaxCount":50,
     "structuringThreshold":9900,"structuringWindowHours":24,"structuringMinPayments":3}
  """.trimIndent()

  @Test
  fun `create persists rule and publishes merged Flink payload via outbox`() {
    val id = UUID.randomUUID()
    val row = FraudRuleRow(id = id, name = "high-amount", enabled = true, jsonSpec = spec, version = 1)
    whenever(ruleRepository.insertRule(eq("high-amount"), eq(true), any())).doReturn(Mono.just(row))
    whenever(outboxRepository.insertReturningId(any(), any(), any())).doReturn(Mono.just(99L))
    whenever(outboxRepository.deleteOutboxById(eq(99L))).doReturn(Mono.just(1L))

    val result = service.create(
      CreateFraudRuleRequest(name = "high-amount", enabled = true, jsonSpec = spec),
    )

    StepVerifier.create(result)
      .assertNext { dto -> assertEquals(id.toString(), dto.id) }
      .verifyComplete()

    val payloadCaptor = argumentCaptor<String>()
    verify(outboxRepository).insertReturningId(eq(id.toString()), eq("FraudRuleUpdated"), payloadCaptor.capture())
    verify(outboxRepository).deleteOutboxById(99L)

    val payload = objectMapper.readTree(payloadCaptor.firstValue)
    assertEquals(id.toString(), payload.path("ruleId").asText())
    assertEquals(1, payload.path("version").asInt())
    assertTrue(payload.path("enabled").asBoolean())
    assertEquals(10000, payload.path("maxAmount").asInt())
    assertEquals(50, payload.path("velocityMaxCount").asInt())
  }
}
