package com.paypulse.generator.scenario

import com.paypulse.generator.GeneratorProfile
import com.paypulse.generator.GeneratorProfiles
import java.math.BigDecimal

object ScenarioCatalog {

  private val supported = setOf(
    "structuring",
    "velocity",
    "geo-anomaly",
    "normal-load",
    "mixed",
  )

  fun isSupported(name: String): Boolean = name in supported

  fun resolve(name: String, defaultIntervalMs: Long): ScenarioConfig? = when (name) {
    "structuring" -> ScenarioConfig(
      name = name,
      profile = GeneratorProfile.RISKY,
      intervalMs = 800,
      fixedAccountId = "acc-structuring-demo",
      amountMin = BigDecimal("9700.00"),
      amountMax = GeneratorProfiles.structuringThreshold - BigDecimal("0.01"),
      burstCount = 12,
    )
    "velocity" -> ScenarioConfig(
      name = name,
      profile = GeneratorProfile.FRAUD,
      intervalMs = 150,
      fixedAccountId = "acc-velocity-demo",
      burstCount = 60,
    )
    "geo-anomaly" -> ScenarioConfig(
      name = name,
      profile = GeneratorProfile.RISKY,
      intervalMs = 1_000,
      fixedAccountId = "acc-geo-demo",
      merchantId = "eu-shop:foreign",
      burstCount = 8,
    )
    "normal-load" -> ScenarioConfig(
      name = name,
      profile = GeneratorProfile.NORMAL,
      intervalMs = defaultIntervalMs.coerceAtLeast(500),
    )
    "mixed" -> ScenarioConfig(
      name = name,
      profile = GeneratorProfile.NORMAL,
      intervalMs = 1_200,
      burstCount = 20,
    )
    else -> null
  }
}
