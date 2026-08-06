package com.paypulse.bff.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/health")
class HealthSummaryController(
  private val healthSummaryService: HealthSummaryService,
) {

  @GetMapping("/summary")
  fun summary(): Mono<HealthSummaryResponse> =
    healthSummaryService.summary().next()
}
