package com.paypulse.generator

import com.paypulse.common.model.CreatePaymentRequest
import com.paypulse.generator.scenario.ScenarioConfig
import java.math.BigDecimal
import kotlin.random.Random

enum class GeneratorProfile {
  NORMAL,
  RISKY,
  FRAUD,
}

object GeneratorProfiles {

  val structuringThreshold: BigDecimal = BigDecimal("9900.00")

  fun buildPayment(scenario: ScenarioConfig, random: Random = Random.Default): CreatePaymentRequest {
    val accountId = scenario.fixedAccountId ?: "acc-${random.nextInt(1, 1000)}"
    val merchantId = scenario.merchantId ?: defaultMerchant(scenario.profile, random)
    val amount = when {
      scenario.amountMin != null && scenario.amountMax != null ->
        randomAmount(scenario.amountMin, scenario.amountMax, random)

      scenario.profile == GeneratorProfile.NORMAL ->
        randomAmount(BigDecimal("10.00"), BigDecimal("500.00"), random)

      scenario.profile == GeneratorProfile.RISKY ->
        randomAmount(BigDecimal("9700.00"), structuringThreshold - BigDecimal("0.01"), random)

      scenario.profile == GeneratorProfile.FRAUD ->
        randomAmount(BigDecimal("50.00"), BigDecimal("250.00"), random)

      else -> randomAmount(BigDecimal("10.00"), BigDecimal("500.00"), random)
    }
    return CreatePaymentRequest(
      accountId = accountId,
      amount = amount,
      currency = "USD",
      merchantId = merchantId,
    )
  }

  private fun defaultMerchant(profile: GeneratorProfile, random: Random): String =
    when (profile) {
      GeneratorProfile.NORMAL -> "demo-merchant"
      GeneratorProfile.RISKY -> "demo-merchant"
      GeneratorProfile.FRAUD -> "burst-merchant-${random.nextInt(1, 5)}"
    }

  private fun randomAmount(min: BigDecimal, max: BigDecimal, random: Random): BigDecimal {
    val minCents = min.movePointRight(2).toLong()
    val maxCents = max.movePointRight(2).toLong()
    val cents = random.nextLong(minCents, maxCents + 1)
    return BigDecimal.valueOf(cents).movePointLeft(2)
  }
}
