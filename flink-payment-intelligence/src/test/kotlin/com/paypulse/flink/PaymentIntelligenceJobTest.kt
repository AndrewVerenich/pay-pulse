package com.paypulse.flink

import com.paypulse.flink.model.FraudAlert
import com.paypulse.flink.model.FraudRule
import com.paypulse.flink.model.PaymentEvent
import com.paypulse.flink.model.UserRiskProfile
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * Лёгкий MiniCluster-тест графа детекции на bounded-источнике (`fromElements`),
 * без Kafka. Проверяет, что серия мелких платежей даёт хотя бы один structuring-алерт.
 */
class PaymentIntelligenceJobTest {

  class CollectSink : SinkFunction<FraudAlert> {
    override fun invoke(value: FraudAlert, context: SinkFunction.Context) {
      VALUES.add(value)
    }

    companion object {
      val VALUES: MutableList<FraudAlert> = Collections.synchronizedList(mutableListOf())
    }
  }

  @Test
  fun `structuring burst produces a fraud alert`() {
    CollectSink.VALUES.clear()

    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.parallelism = 1

    val base = 1_700_000_000_000L
    val payments = env.fromElements(
      PaymentEvent("e1", "p1", "acc-1", 9000.0, "USD", "m1", base + 1_000),
      PaymentEvent("e2", "p2", "acc-1", 9100.0, "USD", "m1", base + 2_000),
      PaymentEvent("e3", "p3", "acc-1", 9200.0, "USD", "m1", base + 3_000),
      PaymentEvent("e4", "p4", "acc-1", 9300.0, "USD", "m1", base + 4_000),
    ).assignTimestampsAndWatermarks(
      WatermarkStrategy.forMonotonousTimestamps<PaymentEvent>()
        .withTimestampAssigner { e, _ -> e.occurredAtEpochMs },
    )

    val rules = env.fromElements(FraudRule.DEFAULT)
    val profiles = env.fromElements(UserRiskProfile("acc-1", 0.1))

    val outputs = PaymentIntelligenceJob.buildDetection(payments, rules, profiles)
    outputs.alerts.addSink(CollectSink())

    env.execute("test-payment-intelligence")

    assertTrue(CollectSink.VALUES.isNotEmpty(), "expected at least one fraud alert")
    assertTrue(
      CollectSink.VALUES.any { it.reasons.contains("structuring") },
      "expected a structuring alert, got ${CollectSink.VALUES.map { it.reasons }}",
    )
  }
}
