package com.paypulse.saga.definition

import com.paypulse.saga.model.StepType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class PaymentSagaDefinitionTest {

  private val saga = PaymentSagaConfiguration().paymentSaga()

  @Test
  fun `saga has four steps in declared order`() {
    assertEquals("PaymentSaga", saga.sagaType)
    assertEquals(
      listOf("FRAUD_CHECK", "RISK_SCORING", "LEDGER_APPLY", "NOTIFY"),
      saga.steps.map { it.stepName },
    )
  }

  @Test
  fun `step types follow SEC model`() {
    val byName = saga.steps.associateBy { it.stepName }
    assertEquals(StepType.COMPENSABLE, byName.getValue("FRAUD_CHECK").stepType)
    assertEquals(StepType.COMPENSABLE, byName.getValue("RISK_SCORING").stepType)
    assertEquals(StepType.PIVOT, byName.getValue("LEDGER_APPLY").stepType)
    assertEquals(StepType.RETRYABLE, byName.getValue("NOTIFY").stepType)
  }

  @Test
  fun `compensable steps before pivot have compensation, pivot and after do not`() {
    val byName = saga.steps.associateBy { it.stepName }
    assertNotNull(byName.getValue("FRAUD_CHECK").compensationBuilder)
    assertNotNull(byName.getValue("RISK_SCORING").compensationBuilder)
    assertNull(byName.getValue("LEDGER_APPLY").compensationBuilder)
    assertNull(byName.getValue("NOTIFY").compensationBuilder)
  }

  @Test
  fun `participants map to dedicated command topics`() {
    val byName = saga.steps.associateBy { it.stepName }
    assertEquals("fraud-check", byName.getValue("FRAUD_CHECK").participant)
    assertEquals("risk-scoring", byName.getValue("RISK_SCORING").participant)
    assertEquals("ledger-apply", byName.getValue("LEDGER_APPLY").participant)
    assertEquals("notification", byName.getValue("NOTIFY").participant)
  }
}
