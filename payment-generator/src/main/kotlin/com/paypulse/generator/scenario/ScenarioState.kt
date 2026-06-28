package com.paypulse.generator.scenario

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Component
class ScenarioState {
  private val active = AtomicReference(ScenarioConfig.baseline(4_000))
  private val paymentsSent = AtomicLong(0)
  private val lastSentAtMs = AtomicLong(0)

  fun activate(config: ScenarioConfig) {
    active.set(config)
    paymentsSent.set(0)
    lastSentAtMs.set(0)
  }

  fun current(): ScenarioConfig = active.get()

  fun markSent() {
    paymentsSent.incrementAndGet()
    lastSentAtMs.set(System.currentTimeMillis())
  }

  fun paymentsSent(): Long = paymentsSent.get()

  fun millisSinceLastSent(): Long {
    val last = lastSentAtMs.get()
    if (last == 0L) return Long.MAX_VALUE
    return System.currentTimeMillis() - last
  }
}
