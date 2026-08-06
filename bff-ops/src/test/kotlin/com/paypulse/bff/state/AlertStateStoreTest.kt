package com.paypulse.bff.state

import com.paypulse.bff.model.FraudAlertEvent
import com.paypulse.bff.properties.BffProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

class AlertStateStoreTest {

  private val store = DefaultAlertStateStore(BffProperties(recentBufferSize = 10))

  private fun alert(id: String) = FraudAlertEvent(
    alertId = id,
    userId = "acc-1",
    paymentId = "pay-1",
    score = 0.9,
    reasons = listOf("velocity"),
    ruleId = "rule-default",
    occurredAt = Instant.now(),
  )

  @Test
  fun `recent buffer keeps last items`() {
    store.onAlert(alert("a1"))
    store.onAlert(alert("a2"))
    assertEquals(2, store.recent(10).size)
  }

  @Test
  fun `stream emits alerts to subscribers`() {
    val flux = store.stream()
    StepVerifier.create(flux.take(1))
      .then { store.onAlert(alert("a3")) }
      .assertNext { assertEquals("a3", it.alertId) }
      .verifyComplete()
  }
}
