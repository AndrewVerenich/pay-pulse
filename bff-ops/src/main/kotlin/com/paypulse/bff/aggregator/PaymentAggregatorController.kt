package com.paypulse.bff.aggregator

import com.fasterxml.jackson.databind.JsonNode
import com.paypulse.bff.model.PaymentFull
import com.paypulse.bff.model.PaymentLiveEvent
import com.paypulse.bff.model.StuckSagaItem
import com.paypulse.bff.state.LiveStateStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class PaymentAggregatorController(
  private val store: LiveStateStore,
  private val sagaQueryService: SagaQueryService,
  private val stuckSagasQueryService: StuckSagasQueryService,
) {

  @GetMapping("/api/payments/recent")
  fun recent(@RequestParam(defaultValue = "50") limit: Int): List<PaymentLiveEvent> =
    store.recent(limit.coerceIn(1, 500))

  @GetMapping("/api/payments/{paymentId}/full")
  fun full(@PathVariable paymentId: String): Mono<ResponseEntity<PaymentFull>> {
    val payment = store.findRecentPayment(paymentId)
      ?: return Mono.just(ResponseEntity.notFound().build())

    val sagaId = payment.sagaId ?: store.sagaIdForPayment(paymentId)
    val sagaMono = sagaId?.let { sagaQueryService.fetchSummary(it) } ?: Mono.empty()

    return sagaMono
      .map { saga -> ResponseEntity.ok(PaymentFull(payment.copy(sagaStatus = saga.status), saga)) }
      .defaultIfEmpty(ResponseEntity.ok(PaymentFull(payment, null)))
  }

  @GetMapping("/api/sagas/stuck")
  fun stuckSagas(): Flux<StuckSagaItem> = stuckSagasQueryService.listStuck()

  @GetMapping("/api/sagas/{id}")
  fun saga(@PathVariable id: String): Mono<ResponseEntity<JsonNode>> =
    sagaQueryService.fetchRaw(id)
      .map { ResponseEntity.ok(it) }
      .defaultIfEmpty(ResponseEntity.notFound().build())
}
