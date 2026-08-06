package com.paypulse.bff.aggregator

import com.paypulse.bff.model.StuckSagaItem
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux

interface StuckSagasQueryService {
  fun listStuck(): Flux<StuckSagaItem>
}

@Service
class DefaultStuckSagasQueryService(
  private val sagaOrchestratorClient: WebClient,
) : StuckSagasQueryService {

  override fun listStuck(): Flux<StuckSagaItem> =
    sagaOrchestratorClient.get()
      .uri("/api/v1/sagas/stuck")
      .retrieve()
      .bodyToFlux(StuckSagaItem::class.java)
}
