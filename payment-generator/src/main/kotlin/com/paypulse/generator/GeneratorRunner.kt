package com.paypulse.generator

import com.paypulse.generator.config.GeneratorProperties
import com.paypulse.generator.scenario.ScenarioState
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class GeneratorRunner(
  private val props: GeneratorProperties,
  private val scenarioState: ScenarioState,
  private val paymentSender: PaymentSender,
) : ApplicationRunner {

  private val log = LoggerFactory.getLogger(GeneratorRunner::class.java)

  override fun run(args: ApplicationArguments) {
    if (!props.enabled) {
      log.info("Payment generator disabled (paypulse.generator.enabled=false)")
      return
    }

    val base = WebClient.builder().baseUrl(props.baseUrl).build()
    val tickMs = 200L

    paymentSender.resolveToken(base)
      .doOnNext { log.info("Payment generator started baseUrl={}, tickMs={}", props.baseUrl, tickMs) }
      .flatMapMany { token ->
        val client = paymentSender.authenticatedClient(token)
        Flux.interval(Duration.ofMillis(tickMs))
          .flatMap {
            val scenario = scenarioState.current()
            if (scenarioState.millisSinceLastSent() < scenario.intervalMs) {
              Mono.empty()
            } else {
              val body = GeneratorProfiles.buildPayment(scenario)
              paymentSender.sendOnce(client, body)
                .doOnSuccess { scenarioState.markSent() }
            }
          }
      }
      .subscribe(
        {},
        { e -> log.error("Generator error", e) },
      )
  }
}
