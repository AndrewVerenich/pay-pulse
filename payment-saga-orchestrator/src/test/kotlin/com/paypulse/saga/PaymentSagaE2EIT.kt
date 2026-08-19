package com.paypulse.saga

import com.paypulse.saga.model.SagaStatus
import com.paypulse.saga.model.StepStatus
import com.paypulse.saga.orchestrator.engine.SagaOrchestrator
import com.paypulse.saga.orchestrator.repository.SagaStepRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * S2 acceptance: полный happy-path саги оплаты через реальную Kafka и Postgres.
 * Оркестратор гоняет команды по всем четырём participant-заглушкам,
 * сага завершается в COMPLETED, все шаги — COMPLETED.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(FakeParticipants::class)
class PaymentSagaE2EIT : AbstractSagaIT() {

  @Autowired
  private lateinit var orchestrator: SagaOrchestrator

  @Autowired
  private lateinit var stepRepository: SagaStepRepository

  @Autowired
  private lateinit var behavior: FakeParticipants.Behavior

  @BeforeEach
  fun resetBehavior() {
    behavior.ledgerShouldFail.set(false)
  }

  @Test
  fun `payment saga completes all four steps`() {
    val sagaId = UUID.randomUUID()
    val payload = paymentPayload()

    orchestrator.startSaga("PaymentSaga", payload, sagaId).block()

    val instance = awaitTerminal(sagaId)
    assertThat(instance.status).isEqualTo(SagaStatus.COMPLETED.name)
    assertThat(instance.completedAt).isNotNull()

    val steps = stepRepository.findBySagaInstanceIdOrderByStepOrder(instance.id!!)
      .collectList()
      .block()!!

    assertThat(steps).hasSize(4)
    assertThat(steps.map { it.stepName })
      .containsExactly("FRAUD_CHECK", "RISK_SCORING", "LEDGER_APPLY", "NOTIFY")
    assertThat(steps).allSatisfy { step ->
      assertThat(step.status).isEqualTo(StepStatus.COMPLETED.name)
    }
  }

  private fun paymentPayload(): String =
    """
    {
      "paymentId": "${UUID.randomUUID()}",
      "accountId": "acc-e2e",
      "amount": 42.50,
      "currency": "USD",
      "merchantId": "merch-1"
    }
    """.trimIndent()
}
