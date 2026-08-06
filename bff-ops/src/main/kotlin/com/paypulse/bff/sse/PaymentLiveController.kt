package com.paypulse.bff.sse

import com.paypulse.bff.model.PaymentLiveEvent
import com.paypulse.bff.state.LiveStateStore
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@RequestMapping("/api/live/payments")
class PaymentLiveController(private val store: LiveStateStore) {

  @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun stream(): Flux<ServerSentEvent<PaymentLiveEvent>> {
    val events = store.paymentsStream()
      .map { ServerSentEvent.builder(it).build() }
    val heartbeat = Flux.interval(Duration.ofSeconds(20))
      .map { ServerSentEvent.builder<PaymentLiveEvent>().comment("keep-alive").build() }
    return Flux.merge(events, heartbeat)
  }
}
