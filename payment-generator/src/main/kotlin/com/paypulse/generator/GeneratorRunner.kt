package com.paypulse.generator

import com.paypulse.common.model.CreatePaymentRequest
import com.paypulse.generator.config.GeneratorProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.random.Random

@Component
class GeneratorRunner(
  private val props: GeneratorProperties,
) : ApplicationRunner {

  private val log = LoggerFactory.getLogger(GeneratorRunner::class.java)

  override fun run(args: ApplicationArguments) {
    if (!props.enabled) {
      log.info("Payment generator disabled (paypulse.generator.enabled=false)")
      return
    }
    val intervalMs = props.intervalMs.coerceAtLeast(500L)
    val base = WebClient.builder().baseUrl(props.baseUrl).build()

    val pipeline = resolveToken(base)
      .doOnNext { log.info("Payment generator using baseUrl={}, intervalMs={}", props.baseUrl, intervalMs) }
      .flatMapMany { token ->
        val payClient = WebClient.builder()
          .baseUrl(props.baseUrl)
          .defaultHeader("Authorization", "Bearer $token")
          .build()
        Flux.interval(Duration.ofMillis(intervalMs))
          .flatMap { sendOnce(payClient) }
      }

    pipeline.subscribe(
      {},
      { e -> log.error("Generator error", e) },
    )
  }

  private fun resolveToken(base: WebClient): Mono<String> =
    if (props.accessToken.isNotBlank()) {
      Mono.just(props.accessToken)
    } else {
      base.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("username" to "admin", "password" to "admin"))
        .retrieve()
        .bodyToMono(LoginResponseDto::class.java)
        .map { it.accessToken }
        .doOnNext { log.info("Obtained JWT via /auth/login") }
    }

  private fun sendOnce(client: WebClient): Mono<Void> {
    val accountId = "acc-${Random.nextInt(1, 1000)}"
    val body = CreatePaymentRequest(
      accountId = accountId,
      amount = BigDecimal.valueOf(Random.nextLong(100, 50_000)).movePointLeft(2),
      currency = "USD",
      merchantId = "demo-merchant",
    )
    return client.post()
      .uri("/api/v1/payments")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Idempotency-Key", UUID.randomUUID().toString())
      .bodyValue(body)
      .retrieve()
      .bodyToMono(String::class.java)
      .doOnNext { log.debug("Payment created: {}", it) }
      .doOnError { e -> log.warn("Payment request failed: {}", e.message) }
      .onErrorResume { Mono.empty() }
      .then()
  }

  private data class LoginResponseDto(val accessToken: String)
}
