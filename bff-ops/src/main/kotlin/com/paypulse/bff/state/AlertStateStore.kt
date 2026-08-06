package com.paypulse.bff.state

import com.paypulse.bff.model.FraudAlertEvent
import com.paypulse.bff.properties.BffProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.ArrayDeque

interface AlertStateStore {
  fun onAlert(alert: FraudAlertEvent)
  fun stream(): Flux<FraudAlertEvent>
  fun recent(limit: Int): List<FraudAlertEvent>
}

@Component
class DefaultAlertStateStore(
  private val properties: BffProperties,
) : AlertStateStore {
  private val log = LoggerFactory.getLogger(DefaultAlertStateStore::class.java)

  private val recent = ArrayDeque<FraudAlertEvent>()
  private val recentLock = Any()

  private val sink: Sinks.Many<FraudAlertEvent> =
    Sinks.many().multicast().onBackpressureBuffer(512, false)

  override fun onAlert(alert: FraudAlertEvent) {
    synchronized(recentLock) {
      recent.addFirst(alert)
      while (recent.size > properties.recentBufferSize) {
        recent.removeLast()
      }
    }
    val result = sink.tryEmitNext(alert)
    if (result.isFailure) {
      log.warn("Failed to emit fraud alert alertId={}: {}", alert.alertId, result)
    }
  }

  override fun stream(): Flux<FraudAlertEvent> = sink.asFlux()

  override fun recent(limit: Int): List<FraudAlertEvent> =
    synchronized(recentLock) { recent.toList().take(limit) }
}
