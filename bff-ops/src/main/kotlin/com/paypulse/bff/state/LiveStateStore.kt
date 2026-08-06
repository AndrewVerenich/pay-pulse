package com.paypulse.bff.state

import com.paypulse.bff.model.PaymentLiveEvent
import com.paypulse.bff.model.SagaLifecycleEvent
import com.paypulse.bff.properties.BffProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory состояние live-слоя BFF. S3 не трогает Postgres, поэтому корреляция платёж↔сага
 * и буфер последних платежей держатся в памяти, питаясь из Kafka-консьюмеров.
 */
interface LiveStateStore {
  fun onPaymentEvent(event: PaymentLiveEvent)
  fun onSagaEvent(event: SagaLifecycleEvent)
  fun recent(limit: Int): List<PaymentLiveEvent>
  fun paymentsStream(): Flux<PaymentLiveEvent>
  fun sagaStream(sagaId: String): Flux<SagaLifecycleEvent>
  fun sagaStatusOf(sagaId: String): String?
  fun sagaIdForPayment(paymentId: String): String?
  fun findRecentPayment(paymentId: String): PaymentLiveEvent?
}

@Component
class DefaultLiveStateStore(
  private val properties: BffProperties,
) : LiveStateStore {
  private val log = LoggerFactory.getLogger(DefaultLiveStateStore::class.java)

  private val recent = ArrayDeque<PaymentLiveEvent>()
  private val recentLock = Any()

  private val sagaStatusById = ConcurrentHashMap<String, String>()
  private val sagaIdByPaymentId = ConcurrentHashMap<String, String>()

  private val paymentsSink: Sinks.Many<PaymentLiveEvent> =
    Sinks.many().multicast().onBackpressureBuffer(1024, false)

  private val sagaSinks = ConcurrentHashMap<String, Sinks.Many<SagaLifecycleEvent>>()

  private val terminalEvents = setOf("SAGA_COMPLETED", "SAGA_COMPENSATED", "SAGA_FAILED")

  override fun onPaymentEvent(event: PaymentLiveEvent) {
    val sagaId = event.sagaId ?: sagaIdByPaymentId[event.paymentId]
    val enriched = event.copy(
      sagaId = sagaId,
      sagaStatus = sagaId?.let { sagaStatusById[it] },
    )
    synchronized(recentLock) {
      recent.addFirst(enriched)
      while (recent.size > properties.recentBufferSize) {
        recent.removeLast()
      }
    }
    emitPayment(enriched)
  }

  override fun onSagaEvent(event: SagaLifecycleEvent) {
    event.status?.let { sagaStatusById[event.sagaId] = it }
    event.paymentId?.let { sagaIdByPaymentId[it] = event.sagaId }

    sinkFor(event.sagaId).tryEmitNext(event)

    val pid = event.paymentId
    if (pid != null && event.status != null) {
      val updated = snapshotRecent().firstOrNull { it.paymentId == pid }
        ?.copy(sagaId = event.sagaId, sagaStatus = event.status)
      if (updated != null) {
        replaceInRecent(updated)
        emitPayment(updated)
      }
    }

    if (event.eventType in terminalEvents) {
      scheduleSinkCleanup(event.sagaId)
    }
  }

  override fun recent(limit: Int): List<PaymentLiveEvent> = snapshotRecent().take(limit)

  override fun paymentsStream(): Flux<PaymentLiveEvent> = paymentsSink.asFlux()

  override fun sagaStream(sagaId: String): Flux<SagaLifecycleEvent> = sinkFor(sagaId).asFlux()

  override fun sagaStatusOf(sagaId: String): String? = sagaStatusById[sagaId]

  override fun sagaIdForPayment(paymentId: String): String? = sagaIdByPaymentId[paymentId]

  override fun findRecentPayment(paymentId: String): PaymentLiveEvent? =
    snapshotRecent().firstOrNull { it.paymentId == paymentId }

  private fun emitPayment(event: PaymentLiveEvent) {
    val result = paymentsSink.tryEmitNext(event)
    if (result.isFailure) {
      log.warn("Failed to emit live payment event paymentId={}: {}", event.paymentId, result)
    }
  }

  private fun sinkFor(sagaId: String): Sinks.Many<SagaLifecycleEvent> =
    sagaSinks.computeIfAbsent(sagaId) { Sinks.many().multicast().onBackpressureBuffer(256, false) }

  private fun snapshotRecent(): List<PaymentLiveEvent> = synchronized(recentLock) { recent.toList() }

  private fun replaceInRecent(updated: PaymentLiveEvent) {
    synchronized(recentLock) {
      val iterator = recent.iterator()
      val rebuilt = ArrayList<PaymentLiveEvent>(recent.size)
      while (iterator.hasNext()) {
        val item = iterator.next()
        rebuilt.add(if (item.paymentId == updated.paymentId) updated else item)
      }
      recent.clear()
      recent.addAll(rebuilt)
    }
  }

  private fun scheduleSinkCleanup(sagaId: String) {
    Mono.delay(Duration.ofSeconds(60), Schedulers.parallel())
      .doOnNext {
        sagaSinks.remove(sagaId)?.tryEmitComplete()
        log.debug("Cleaned up saga sink sagaId={}", sagaId)
      }
      .subscribe()
  }
}
