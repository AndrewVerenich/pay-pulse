package com.paypulse.bff.sse

import com.paypulse.bff.model.FraudAlertEvent
import com.paypulse.bff.state.AlertStateStore
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@RequestMapping("/api/live/alerts")
class AlertsLiveController(private val store: AlertStateStore) {

  @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun stream(): Flux<ServerSentEvent<FraudAlertEvent>> {
    val events = store.stream()
      .map { ServerSentEvent.builder(it).build() }
    val heartbeat = Flux.interval(Duration.ofSeconds(20))
      .map { ServerSentEvent.builder<FraudAlertEvent>().comment("keep-alive").build() }
    return Flux.merge(events, heartbeat)
  }
}
