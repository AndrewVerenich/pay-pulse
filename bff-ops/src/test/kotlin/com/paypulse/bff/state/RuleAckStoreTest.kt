package com.paypulse.bff.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration

class RuleAckStoreTest {

  @Test
  fun `tracks latest applied version per rule`() {
    val store = DefaultRuleAckStore()
    assertNull(store.latestVersion("r1"))

    store.onRuleApplied("r1", 1)
    store.onRuleApplied("r1", 3)
    store.onRuleApplied("r1", 2) // out of order, keeps max

    assertEquals(3, store.latestVersion("r1"))
  }

  @Test
  fun `stream emits acks to subscribers`() {
    val store = DefaultRuleAckStore()
    val flux = store.stream()

    StepVerifier.create(flux.take(2))
      .then { store.onRuleApplied("r1", 1) }
      .assertNext { assertEquals("r1", it.ruleId); assertEquals(1, it.version) }
      .then { store.onRuleApplied("r2", 5) }
      .assertNext { assertEquals("r2", it.ruleId); assertEquals(5, it.version) }
      .verifyComplete()
  }

  @Test
  fun `subscriber receives nothing without events`() {
    val store = DefaultRuleAckStore()
    StepVerifier.create(store.stream())
      .expectSubscription()
      .expectNoEvent(Duration.ofMillis(100))
      .thenCancel()
      .verify()
  }
}
