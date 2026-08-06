package com.paypulse.bff.health

import com.paypulse.bff.properties.HealthProbeProperties
import com.paypulse.bff.properties.HealthProbeTarget
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

data class HealthProbeResult(
  val id: String,
  val name: String,
  val status: String,
  val link: String?,
  val detail: String? = null,
)

data class HealthSummaryResponse(
  val checkedAt: Instant,
  val probes: List<HealthProbeResult>,
)

interface HealthSummaryService {
  fun summary(): Flux<HealthSummaryResponse>
}

@Service
class DefaultHealthSummaryService(
  private val properties: HealthProbeProperties,
  webClientBuilder: WebClient.Builder,
) : HealthSummaryService {
  private val webClient = webClientBuilder.build()

  override fun summary(): Flux<HealthSummaryResponse> =
    Flux.fromIterable(properties.probes)
      .flatMap { probe -> check(probe) }
      .collectList()
      .map { results ->
        HealthSummaryResponse(
          checkedAt = Instant.now(),
          probes = results.sortedBy { it.name },
        )
      }
      .flux()

  private fun check(probe: HealthProbeTarget): Mono<HealthProbeResult> =
    webClient.get()
      .uri(probe.url)
      .retrieve()
      .bodyToMono(String::class.java)
      .timeout(Duration.ofSeconds(3))
      .map { body ->
        val normalized = body.replace(" ", "")
        val up = normalized.contains("\"status\":\"UP\"", ignoreCase = true) ||
          body.contains("Healthy", ignoreCase = true) ||
          normalized.contains("\"database\":\"ok\"", ignoreCase = true) ||
          normalized.contains("\"flink-version\"", ignoreCase = true) ||
          normalized.contains("\"taskmanagers\"", ignoreCase = true) ||
          body.trim().equals("OK", ignoreCase = true) ||
          body.contains("<!DOCTYPE html>", ignoreCase = true) ||
          body.contains("<html", ignoreCase = true)
        HealthProbeResult(
          id = probe.id,
          name = probe.name,
          status = if (up) "UP" else "DOWN",
          link = probe.link,
          detail = if (up) null else body.take(120),
        )
      }
      .onErrorResume {
        Mono.just(
          HealthProbeResult(
            id = probe.id,
            name = probe.name,
            status = "DOWN",
            link = probe.link,
            detail = it.message,
          ),
        )
      }
}
