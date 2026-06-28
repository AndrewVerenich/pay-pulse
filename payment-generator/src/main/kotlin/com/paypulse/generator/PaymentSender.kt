package com.paypulse.generator

import com.paypulse.common.model.CreatePaymentRequest
import com.paypulse.generator.config.GeneratorProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class PaymentSender(
  private val props: GeneratorProperties,
) {
  private val log = LoggerFactory.getLogger(PaymentSender::class.java)

  fun resolveToken(base: WebClient): Mono<String> =
    if (props.accessToken.isNotBlank()) {
      Mono.just(props.accessToken)
    } else {
      base.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("username" to props.loginUsername, "password" to props.loginPassword))
        .retrieve()
        .bodyToMono(LoginResponseDto::class.java)
        .map { it.accessToken }
        .doOnNext { log.info("Obtained JWT via /auth/login") }
    }

  fun authenticatedClient(token: String): WebClient =
    WebClient.builder()
      .baseUrl(props.baseUrl)
      .defaultHeader("Authorization", "Bearer $token")
      .build()

  fun sendOnce(client: WebClient, body: CreatePaymentRequest): Mono<Void> =
    client.post()
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

  fun burst(client: WebClient, config: () -> CreatePaymentRequest, count: Int): Mono<Long> {
    if (count <= 0) return Mono.just(0L)
    return Flux.range(1, count)
      .concatMap { sendOnce(client, config()).thenReturn(1L) }
      .reduce(0L) { acc, _ -> acc + 1 }
  }

  private data class LoginResponseDto(val accessToken: String)
}
