package com.paypulse.generator

import com.paypulse.generator.config.GeneratorProperties
import com.paypulse.generator.scenario.ContinuousDemoRotator
import com.paypulse.generator.scenario.ScenarioState
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Component
class GeneratorRunner(
  private val props: GeneratorProperties,
  private val scenarioState: ScenarioState,
  private val paymentSender: PaymentSender,
) : ApplicationRunner {

  private val log = LoggerFactory.getLogger(GeneratorRunner::class.java)
  private val clientRef = AtomicReference<WebClient>()
  private val lastPatternIdx = AtomicInteger(-1)

  override fun run(args: ApplicationArguments) {
    if (!props.enabled) {
      log.info("Payment generator disabled (paypulse.generator.enabled=false)")
      return
    }

    val base = WebClient.builder().baseUrl(props.baseUrl).build()
    val tickMs = 200L
    // Refresh JWT before 15m gateway expiry (access token TTL).
    val tokenRefreshEvery = Duration.ofMinutes(10)

    paymentSender.resolveToken(base)
      .doOnNext { token ->
        clientRef.set(paymentSender.authenticatedClient(token))
        log.info(
          "Payment generator started baseUrl={}, continuousDemo={}, tickMs={}",
          props.baseUrl,
          props.continuousDemo,
          tickMs,
        )
      }
      .flatMapMany {
        Flux.merge(
          Flux.interval(tokenRefreshEvery)
            .flatMap {
              paymentSender.resolveToken(base)
                .doOnNext { token ->
                  clientRef.set(paymentSender.authenticatedClient(token))
                  log.info("Payment generator JWT refreshed")
                }
                .onErrorResume { e ->
                  log.warn("JWT refresh failed: {}", e.message)
                  Mono.empty()
                }
            },
          Flux.interval(Duration.ofMillis(tickMs))
            .flatMap { sendTick() },
        )
      }
      .subscribe(
        {},
        { e -> log.error("Generator error", e) },
      )
  }

  private fun sendTick(): Mono<Void> {
    val client = clientRef.get() ?: return Mono.empty()
    val scenario = if (props.continuousDemo) {
      val now = System.currentTimeMillis()
      val idx = ContinuousDemoRotator.patternIndex(now)
      if (lastPatternIdx.getAndSet(idx) != idx) {
        val cfg = ContinuousDemoRotator.current(now)
        log.info("Continuous demo pattern → {} (intervalMs={})", cfg.name, cfg.intervalMs)
      }
      ContinuousDemoRotator.current(now)
    } else {
      scenarioState.current()
    }

    if (scenarioState.millisSinceLastSent() < scenario.intervalMs) {
      return Mono.empty()
    }
    val body = GeneratorProfiles.buildPayment(scenario)
    return paymentSender.sendOnce(client, body)
      .doOnSuccess { scenarioState.markSent() }
  }
}
