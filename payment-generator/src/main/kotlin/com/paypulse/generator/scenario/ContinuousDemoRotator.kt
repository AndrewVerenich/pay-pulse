package com.paypulse.generator.scenario

import com.paypulse.generator.GeneratorProfile
import com.paypulse.generator.GeneratorProfiles
import java.math.BigDecimal

/**
 * Continuous demo traffic: rotates through normal / structuring / velocity / geo
 * so Ops, Grafana and Flink stay visually interesting without manual scenario POSTs.
 */
object ContinuousDemoRotator {

  /** How long each pattern stays active before switching. */
  const val SLICE_MS: Long = 25_000L

  private val threshold = GeneratorProfiles.structuringThreshold

  private val patterns: List<ScenarioConfig> = listOf(
    ScenarioConfig(
      name = "continuous-normal",
      profile = GeneratorProfile.NORMAL,
      intervalMs = 1_500,
      merchantId = "demo-merchant",
    ),
    ScenarioConfig(
      name = "continuous-structuring",
      profile = GeneratorProfile.RISKY,
      intervalMs = 700,
      fixedAccountId = "acc-struct-live",
      merchantId = "demo-merchant",
      amountMin = threshold - BigDecimal("250.00"),
      amountMax = threshold - BigDecimal("0.01"),
    ),
    ScenarioConfig(
      name = "continuous-velocity",
      profile = GeneratorProfile.FRAUD,
      intervalMs = 250,
      fixedAccountId = "acc-velocity-live",
    ),
    ScenarioConfig(
      name = "continuous-geo",
      profile = GeneratorProfile.RISKY,
      intervalMs = 900,
      fixedAccountId = "acc-geo-live",
      merchantId = "eu-shop:foreign",
      amountMin = BigDecimal("80.00"),
      amountMax = BigDecimal("400.00"),
    ),
    ScenarioConfig(
      name = "continuous-mixed-burst",
      profile = GeneratorProfile.NORMAL,
      intervalMs = 800,
      // random accounts via null fixedAccountId — spreads RFM / merchant charts
      merchantId = null,
    ),
  )

  fun current(nowMs: Long = System.currentTimeMillis()): ScenarioConfig {
    val idx = ((nowMs / SLICE_MS) % patterns.size).toInt()
    return patterns[idx]
  }

  fun patternIndex(nowMs: Long = System.currentTimeMillis()): Int =
    ((nowMs / SLICE_MS) % patterns.size).toInt()
}
