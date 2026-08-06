package com.paypulse.bff.sse

import com.paypulse.bff.model.SagaLifecycleEvent
import com.paypulse.bff.state.LiveStateStore
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@RequestMapping("/api/live/sagas")
class SagaLiveController(private val store: LiveStateStore) {

  @GetMapping("/{id}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun stream(@PathVariable id: String): Flux<ServerSentEvent<SagaLifecycleEvent>> {
    val events = store.sagaStream(id)
      .map { ServerSentEvent.builder(it).build() }
    val heartbeat = Flux.interval(Duration.ofSeconds(20))
      .map { ServerSentEvent.builder<SagaLifecycleEvent>().comment("keep-alive").build() }
    return Flux.merge(events, heartbeat)
  }
}
