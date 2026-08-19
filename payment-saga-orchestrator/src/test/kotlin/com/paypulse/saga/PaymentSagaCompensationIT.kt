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
 * S2 acceptance: сбой на pivot-шаге LEDGER_APPLY запускает компенсацию.
 * Ожидаем терминальный статус COMPENSATED, а ранее выполненные компенсируемые шаги
 * (RISK_SCORING, FRAUD_CHECK) — в статусе COMPENSATED.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(FakeParticipants::class)
class PaymentSagaCompensationIT : AbstractSagaIT() {

  @Autowired
  private lateinit var orchestrator: SagaOrchestrator

  @Autowired
  private lateinit var stepRepository: SagaStepRepository

  @Autowired
  private lateinit var behavior: FakeParticipants.Behavior

  @BeforeEach
  fun failLedger() {
    behavior.ledgerShouldFail.set(true)
  }

  @Test
  fun `ledger failure compensates prior steps`() {
    val sagaId = UUID.randomUUID()

    orchestrator.startSaga("PaymentSaga", paymentPayload(), sagaId).block()

    val instance = awaitTerminal(sagaId)
    assertThat(instance.status).isEqualTo(SagaStatus.COMPENSATED.name)

    val stepsByName = stepRepository.findBySagaInstanceIdOrderByStepOrder(instance.id!!)
      .collectList()
      .block()!!
      .associateBy { it.stepName }

    assertThat(stepsByName["FRAUD_CHECK"]!!.status).isEqualTo(StepStatus.COMPENSATED.name)
    assertThat(stepsByName["RISK_SCORING"]!!.status).isEqualTo(StepStatus.COMPENSATED.name)
    assertThat(stepsByName["LEDGER_APPLY"]!!.status).isEqualTo(StepStatus.FAILED.name)
    assertThat(stepsByName["NOTIFY"]!!.status).isEqualTo(StepStatus.PENDING.name)
  }

  private fun paymentPayload(): String =
    """
    {
      "paymentId": "${UUID.randomUUID()}",
      "accountId": "acc-comp",
      "amount": 999.00,
      "currency": "USD",
      "merchantId": "merch-2"
    }
    """.trimIndent()
}
