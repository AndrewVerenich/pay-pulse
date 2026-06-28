package com.paypulse.generator

import com.paypulse.generator.config.GeneratorProperties
import com.paypulse.generator.scenario.ScenarioCatalog
import com.paypulse.generator.scenario.ScenarioConfig
import com.paypulse.generator.scenario.ScenarioState
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

data class ScenarioActivationResponse(
  val scenario: String,
  val profile: String,
  val intervalMs: Long,
  val burstCount: Int,
  val burstSent: Long,
  val activatedAt: Instant,
  val paymentsSentTotal: Long,
)

@RestController
@RequestMapping("/generator/scenarios", produces = [MediaType.APPLICATION_JSON_VALUE])
@ConditionalOnProperty(name = ["paypulse.generator.scenarios-enabled"], havingValue = "true", matchIfMissing = true)
class ScenarioController(
  private val props: GeneratorProperties,
  private val scenarioState: ScenarioState,
  private val paymentSender: PaymentSender,
) {

  @PostMapping("/{name}")
  fun activate(@PathVariable name: String): Mono<ScenarioActivationResponse> {
    if (!ScenarioCatalog.isSupported(name)) {
      return Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scenario: $name"))
    }

    val resolved = ScenarioCatalog.resolve(name, props.intervalMs)!!
    val config = if (name == "mixed") pickMixedVariant(resolved) else resolved
    scenarioState.activate(config)

    val base = WebClient.builder().baseUrl(props.baseUrl).build()
    return paymentSender.resolveToken(base)
      .flatMap { token ->
        val client = paymentSender.authenticatedClient(token)
        paymentSender.burst(client, { GeneratorProfiles.buildPayment(config) }, config.burstCount)
          .map { burstSent ->
            repeat(burstSent.toInt()) { scenarioState.markSent() }
            ScenarioActivationResponse(
              scenario = config.name,
              profile = config.profile.name.lowercase(),
              intervalMs = config.intervalMs,
              burstCount = config.burstCount,
              burstSent = burstSent,
              activatedAt = config.activatedAt,
              paymentsSentTotal = scenarioState.paymentsSent(),
            )
          }
      }
  }

  private fun pickMixedVariant(base: ScenarioConfig): ScenarioConfig {
    val threshold = GeneratorProfiles.structuringThreshold
    val variants = listOf(
      base.copy(profile = GeneratorProfile.NORMAL, merchantId = "demo-merchant", name = "mixed"),
      base.copy(
        profile = GeneratorProfile.RISKY,
        fixedAccountId = "acc-mixed-structuring",
        amountMin = threshold - BigDecimal("200.00"),
        amountMax = threshold - BigDecimal("0.01"),
        name = "mixed",
      ),
      base.copy(
        profile = GeneratorProfile.FRAUD,
        fixedAccountId = "acc-mixed-velocity",
        intervalMs = 200,
        name = "mixed",
      ),
      base.copy(
        profile = GeneratorProfile.RISKY,
        fixedAccountId = "acc-mixed-geo",
        merchantId = "apac-store:foreign",
        name = "mixed",
      ),
    )
    return variants.random()
  }
}
