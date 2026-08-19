package com.paypulse.flink.rules

import com.paypulse.flink.model.FraudRule

/**
 * Чистая логика velocity: сколько платежей попало в скользящее окно и пробит ли порог.
 * Хранение timestamps — на стороне Flink keyed state; здесь только вычисление.
 */
object VelocityChecker {

  /** Возвращает timestamps в пределах окна `[nowMs - windowMs, nowMs]`. */
  fun withinWindow(timestamps: List<Long>, nowMs: Long, windowMs: Long): List<Long> {
    val lowerBound = nowMs - windowMs
    return timestamps.filter { it in lowerBound..nowMs }
  }

  /** Velocity пробит, если число платежей в окне (включая текущий) превышает лимит. */
  fun exceeds(countInWindow: Int, rule: FraudRule): Boolean =
    countInWindow > rule.velocityMaxCount
}
