package com.paypulse.bff.state

import com.paypulse.bff.model.PaymentLiveEvent
import com.paypulse.bff.model.SagaLifecycleEvent
import com.paypulse.bff.properties.BffProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class LiveStateStoreTest {

  private fun store(buffer: Int = 200) = DefaultLiveStateStore(BffProperties(recentBufferSize = buffer))

  private fun payment(id: String, at: Instant = Instant.now()) = PaymentLiveEvent(
    paymentId = id,
    accountId = "acc-1",
    amount = BigDecimal("10.00"),
    currency = "USD",
    occurredAt = at,
  )

  @Test
  fun `recent returns newest first and respects buffer size`() {
    val store = store(buffer = 2)
    store.onPaymentEvent(payment("p1", Instant.parse("2026-01-01T00:00:00Z")))
    store.onPaymentEvent(payment("p2", Instant.parse("2026-01-01T00:00:01Z")))
    store.onPaymentEvent(payment("p3", Instant.parse("2026-01-01T00:00:02Z")))

    val recent = store.recent(10)
    assertEquals(2, recent.size)
    assertEquals("p3", recent[0].paymentId)
    assertEquals("p2", recent[1].paymentId)
  }

  @Test
  fun `saga event correlates payment to saga and enriches status`() {
    val store = store()
    store.onPaymentEvent(payment("p1"))
    assertNull(store.recent(10).first().sagaStatus)

    store.onSagaEvent(
      SagaLifecycleEvent(
        sagaId = "saga-1",
        eventType = "SAGA_COMPLETED",
        status = "COMPLETED",
        paymentId = "p1",
        occurredAt = Instant.now(),
      ),
    )

    assertEquals("saga-1", store.sagaIdForPayment("p1"))
    assertEquals("COMPLETED", store.sagaStatusOf("saga-1"))
    val updated = store.findRecentPayment("p1")
    assertEquals("COMPLETED", updated?.sagaStatus)
    assertEquals("saga-1", updated?.sagaId)
  }

  @Test
  fun `payment arriving after saga event picks up known status`() {
    val store = store()
    store.onSagaEvent(
      SagaLifecycleEvent(
        sagaId = "saga-2",
        eventType = "STEP_COMPLETED",
        status = "EXECUTING",
        paymentId = "p2",
        occurredAt = Instant.now(),
      ),
    )
    store.onPaymentEvent(payment("p2"))

    val p = store.findRecentPayment("p2")
    assertEquals("saga-2", p?.sagaId)
    assertEquals("EXECUTING", p?.sagaStatus)
  }
}
