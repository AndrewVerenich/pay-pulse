package com.paypulse.generator.scenario

import com.paypulse.generator.GeneratorProfile
import java.math.BigDecimal
import java.time.Instant

data class ScenarioConfig(
  val name: String,
  val profile: GeneratorProfile,
  val intervalMs: Long,
  val fixedAccountId: String? = null,
  val merchantId: String? = null,
  val amountMin: BigDecimal? = null,
  val amountMax: BigDecimal? = null,
  val burstCount: Int = 0,
  val activatedAt: Instant = Instant.now(),
) {
  companion object {
    fun baseline(intervalMs: Long): ScenarioConfig = ScenarioConfig(
      name = "baseline",
      profile = GeneratorProfile.NORMAL,
      intervalMs = intervalMs,
    )
  }
}
