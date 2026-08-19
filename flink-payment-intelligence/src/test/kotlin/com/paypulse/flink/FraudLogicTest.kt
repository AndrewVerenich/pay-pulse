package com.paypulse.flink

import com.paypulse.flink.aml.StructuringDetector
import com.paypulse.flink.io.PaymentJson
import com.paypulse.flink.model.FraudRule
import com.paypulse.flink.rules.GeoAnomalyDetector
import com.paypulse.flink.rules.VelocityChecker
import com.paypulse.flink.scoring.FraudScorer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FraudLogicTest {

  private val rule = FraudRule.DEFAULT

  @Test
  fun `geo anomaly detects foreign merchants`() {
    assertTrue(GeoAnomalyDetector.isForeign("merchant-xyz:foreign"))
    assertFalse(GeoAnomalyDetector.isForeign("merchant-xyz"))
    assertFalse(GeoAnomalyDetector.isForeign(null))
  }

  @Test
  fun `velocity window filters by time and breach respects max count`() {
    val now = 1_000_000L
    val timestamps = listOf(now - 4_000_000L, now - 100L, now - 50L, now)
    val inWindow = VelocityChecker.withinWindow(timestamps, now, rule.velocityWindowMs)
    assertEquals(3, inWindow.size)
    assertFalse(VelocityChecker.exceeds(inWindow.size, rule))
    assertTrue(VelocityChecker.exceeds(rule.velocityMaxCount + 1, rule))
  }

  @Test
  fun `structuring detected when many small payments sum over threshold`() {
    val now = 10_000_000L
    val records = (1..4).map { StructuringDetector.AmountAt(9000.0, now - it * 1000L) }
    val inWindow = StructuringDetector.withinWindow(records, now, rule.structuringWindowHours)
    assertTrue(StructuringDetector.isStructuring(inWindow, rule))
  }

  @Test
  fun `structuring not detected for single large payment`() {
    val now = 10_000_000L
    val records = listOf(StructuringDetector.AmountAt(15000.0, now))
    assertFalse(StructuringDetector.isStructuring(records, rule))
  }

  @Test
  fun `scorer combines signals and clamps to one`() {
    val result = FraudScorer.score(
      FraudScorer.Signals(
        amountOverMax = true,
        velocityBreached = true,
        geoAnomaly = true,
        structuring = true,
        baselineRisk = 1.0,
      ),
    )
    assertEquals(1.0, result.score)
    assertTrue(result.reasons.containsAll(listOf("amount_over_max", "velocity", "geo_anomaly", "structuring")))
  }

  @Test
  fun `scorer returns no reasons for clean payment`() {
    val result = FraudScorer.score(
      FraudScorer.Signals(false, false, false, false, baselineRisk = 0.1),
    )
    assertTrue(result.reasons.isEmpty())
    assertTrue(result.score < 0.05)
  }

  @Test
  fun `parses a valid payment event`() {
    val json = """{"eventId":"e1","paymentId":"p1","accountId":"acc-1","amount":99.5,"currency":"USD","merchantId":"m1","occurredAt":"2026-05-10T12:00:00Z"}"""
    val event = PaymentJson.parsePayment(json)
    assertEquals("p1", event.paymentId)
    assertEquals("acc-1", event.accountId)
    assertEquals(99.5, event.amount)
    assertTrue(event.occurredAtEpochMs > 0)
  }

  @Test
  fun `throws on malformed payment so caller can dead-letter`() {
    assertThrows(Exception::class.java) { PaymentJson.parsePayment("""{"paymentId":"p1"}""") }
    assertThrows(Exception::class.java) { PaymentJson.parsePayment("not-json") }
  }
}
