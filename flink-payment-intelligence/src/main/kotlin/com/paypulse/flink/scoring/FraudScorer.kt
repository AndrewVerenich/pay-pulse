package com.paypulse.flink.scoring

/**
 * Взвешенный rule-based скоринг. Складывает сигналы в score ∈ [0,1] и собирает причины.
 * Baseline-риск профиля пользователя добавляет небольшую базовую составляющую.
 */
object FraudScorer {

  data class Signals(
    val amountOverMax: Boolean,
    val velocityBreached: Boolean,
    val geoAnomaly: Boolean,
    val structuring: Boolean,
    val baselineRisk: Double,
  )

  data class Result(val score: Double, val reasons: List<String>)

  private const val W_AMOUNT = 0.35
  private const val W_VELOCITY = 0.30
  private const val W_GEO = 0.15
  private const val W_STRUCTURING = 0.45
  private const val W_BASELINE = 0.20

  fun score(signals: Signals): Result {
    var raw = 0.0
    val reasons = mutableListOf<String>()

    if (signals.amountOverMax) {
      raw += W_AMOUNT
      reasons += "amount_over_max"
    }
    if (signals.velocityBreached) {
      raw += W_VELOCITY
      reasons += "velocity"
    }
    if (signals.geoAnomaly) {
      raw += W_GEO
      reasons += "geo_anomaly"
    }
    if (signals.structuring) {
      raw += W_STRUCTURING
      reasons += "structuring"
    }
    raw += W_BASELINE * signals.baselineRisk.coerceIn(0.0, 1.0)

    return Result(score = raw.coerceIn(0.0, 1.0), reasons = reasons)
  }
}
