package com.paypulse.saga.orchestrator.controller

import com.paypulse.saga.orchestrator.sse.SagaEventPublisher
import com.paypulse.saga.orchestrator.sse.SagaSseEvent
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/api/v1/sagas")
@CrossOrigin(origins = ["*"])
class SagaSseController(
  private val eventPublisher: SagaEventPublisher
) {

  @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun streamEvents(): Flux<SagaSseEvent> = eventPublisher.stream()
}
