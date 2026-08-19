package com.paypulse.flink.scoring

import com.paypulse.flink.aml.StructuringDetector
import com.paypulse.flink.model.EnrichedPayment
import com.paypulse.flink.model.FraudAlert
import com.paypulse.flink.model.FraudRule
import com.paypulse.flink.model.UserFraudScore
import com.paypulse.flink.rules.GeoAnomalyDetector
import com.paypulse.flink.rules.VelocityChecker
import org.apache.flink.api.common.state.ListState
import org.apache.flink.api.common.state.ListStateDescriptor
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.util.OutputTag
import java.util.UUID

/**
 * Ядро детекции: keyed по `accountId`, читает активное правило из broadcast-состояния,
 * накапливает keyed-state для velocity (timestamps) и structuring (суммы за окно),
 * считает score и эмитит [FraudAlert] (main) + [UserFraudScore] (side output, compact-топик).
 */
class FraudDetectionFunction :
  KeyedBroadcastProcessFunction<String, EnrichedPayment, FraudRule, FraudAlert>() {

  @Transient private lateinit var velocityState: ListState<Long>
  @Transient private lateinit var structuringState: ListState<StructuringDetector.AmountAt>

  override fun open(parameters: Configuration) {
    velocityState = runtimeContext.getListState(
      ListStateDescriptor("velocity-timestamps", TypeInformation.of(Long::class.javaObjectType)),
    )
    structuringState = runtimeContext.getListState(
      ListStateDescriptor("structuring-records", TypeInformation.of(StructuringDetector.AmountAt::class.java)),
    )
  }

  override fun processElement(
    value: EnrichedPayment,
    ctx: ReadOnlyContext,
    out: Collector<FraudAlert>,
  ) {
    val event = value.event
    val rule = ctx.getBroadcastState(RULES_STATE).get(RULE_KEY)
      ?.takeIf { it.enabled } ?: FraudRule.DEFAULT
    val now = event.occurredAtEpochMs

    val velocityTimestamps = VelocityChecker
      .withinWindow(velocityState.get().toList() + now, now, rule.velocityWindowMs)
    velocityState.update(velocityTimestamps)

    val structuringRecords = StructuringDetector.withinWindow(
      structuringState.get().toList() + StructuringDetector.AmountAt(event.amount, now),
      now,
      rule.structuringWindowHours,
    )
    structuringState.update(structuringRecords)

    val signals = FraudScorer.Signals(
      amountOverMax = event.amount > rule.maxAmount,
      velocityBreached = VelocityChecker.exceeds(velocityTimestamps.size, rule),
      geoAnomaly = GeoAnomalyDetector.isForeign(event.merchantId),
      structuring = StructuringDetector.isStructuring(structuringRecords, rule),
      baselineRisk = value.baselineRisk,
    )
    val result = FraudScorer.score(signals)

    ctx.output(
      USER_SCORE_TAG,
      UserFraudScore(
        userId = event.accountId,
        score = result.score,
        lastPaymentId = event.paymentId,
        updatedAtEpochMs = now,
      ),
    )

    if (result.reasons.isNotEmpty()) {
      out.collect(
        FraudAlert(
          alertId = UUID.randomUUID().toString(),
          userId = event.accountId,
          paymentId = event.paymentId,
          score = result.score,
          reasons = result.reasons,
          ruleId = rule.ruleId,
          occurredAtEpochMs = now,
        ),
      )
    }
  }

  override fun processBroadcastElement(
    value: FraudRule,
    ctx: Context,
    out: Collector<FraudAlert>,
  ) {
    ctx.getBroadcastState(RULES_STATE).put(RULE_KEY, value)
  }

  companion object {
    const val RULE_KEY = "rule"

    val RULES_STATE: MapStateDescriptor<String, FraudRule> = MapStateDescriptor(
      "fraud-rules",
      TypeInformation.of(String::class.java),
      TypeInformation.of(FraudRule::class.java),
    )

    val USER_SCORE_TAG: OutputTag<UserFraudScore> = object : OutputTag<UserFraudScore>("user-fraud-score") {}
  }
}
