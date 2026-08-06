package com.paypulse.bff.sse

import com.paypulse.bff.model.RuleAck
import com.paypulse.bff.state.RuleAckStore
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@RequestMapping("/api/live/rules")
class RuleAckController(private val store: RuleAckStore) {

  @GetMapping("/ack", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun ack(
    @RequestParam(required = false) ruleId: String?,
    @RequestParam(required = false) sinceVersion: Long?,
  ): Flux<ServerSentEvent<RuleAck>> {
    val since = sinceVersion ?: 0L

    val alreadyApplied: Flux<RuleAck> =
      if (ruleId != null) {
        val current = store.latestVersion(ruleId)
        if (current != null && current >= since) Flux.just(RuleAck(ruleId, current)) else Flux.empty()
      } else {
        Flux.empty()
      }

    val live = store.stream()
      .filter { ruleId == null || it.ruleId == ruleId }
      .filter { it.version >= since }

    val events = Flux.concat(alreadyApplied, live)
      .map { ServerSentEvent.builder(it).build() }

    val heartbeat = Flux.interval(Duration.ofSeconds(20))
      .map { ServerSentEvent.builder<RuleAck>().comment("keep-alive").build() }

    return Flux.merge(events, heartbeat)
  }
}
