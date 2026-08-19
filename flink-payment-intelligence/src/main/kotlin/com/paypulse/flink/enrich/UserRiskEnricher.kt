package com.paypulse.flink.enrich

import com.paypulse.flink.model.EnrichedPayment
import com.paypulse.flink.model.PaymentEvent
import com.paypulse.flink.model.UserRiskProfile
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.util.Collector

/**
 * Обогащает поток платежей baseline-риском из broadcast-состояния `user_risk_profiles`.
 * Если профиля нет — дефолтный baseline 0.1 (см. S4 R3).
 */
class UserRiskEnricher : BroadcastProcessFunction<PaymentEvent, UserRiskProfile, EnrichedPayment>() {

  override fun processElement(
    value: PaymentEvent,
    ctx: ReadOnlyContext,
    out: Collector<EnrichedPayment>,
  ) {
    val profile = ctx.getBroadcastState(PROFILE_STATE).get(value.accountId)
    out.collect(EnrichedPayment(value, profile?.baselineRisk ?: 0.1))
  }

  override fun processBroadcastElement(
    value: UserRiskProfile,
    ctx: Context,
    out: Collector<EnrichedPayment>,
  ) {
    if (value.userId.isNotBlank()) {
      ctx.getBroadcastState(PROFILE_STATE).put(value.userId, value)
    }
  }

  companion object {
    val PROFILE_STATE: MapStateDescriptor<String, UserRiskProfile> = MapStateDescriptor(
      "user-risk-profiles",
      TypeInformation.of(String::class.java),
      TypeInformation.of(UserRiskProfile::class.java),
    )
  }
}
