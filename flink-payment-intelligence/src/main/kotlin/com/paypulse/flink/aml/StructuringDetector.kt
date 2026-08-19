package com.paypulse.flink.aml

import com.paypulse.flink.model.FraudRule

/**
 * AML-паттерн structuring (smurfing): много мелких платежей, каждый ниже порога отчётности,
 * но суммарно превышающих его, в пределах окна (по умолчанию 24h).
 *
 * Правило (см. план §12.3): в окне есть >= `structuringMinPayments` платежей, каждый
 * `< structuringThreshold`, и их сумма `> structuringThreshold`.
 */
object StructuringDetector {

  data class AmountAt(val amount: Double, val tsMs: Long)

  fun withinWindow(records: List<AmountAt>, nowMs: Long, windowHours: Int): List<AmountAt> {
    val windowMs = windowHours.toLong() * 3_600_000L
    val lowerBound = nowMs - windowMs
    return records.filter { it.tsMs in lowerBound..nowMs }
  }

  fun isStructuring(recordsInWindow: List<AmountAt>, rule: FraudRule): Boolean {
    val small = recordsInWindow.filter { it.amount < rule.structuringThreshold }
    if (small.size < rule.structuringMinPayments) return false
    val sum = small.sumOf { it.amount }
    return sum > rule.structuringThreshold
  }
}
