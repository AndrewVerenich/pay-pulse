package com.paypulse.flink.metrics

import com.paypulse.flink.model.PaymentEvent
import com.paypulse.flink.model.PaymentMetrics
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/**
 * Агрегирует платежи за event-time окно (по валюте) в [PaymentMetrics].
 * Объём демо-нагрузки невелик, поэтому итерируем элементы окна напрямую.
 */
class HourlyMetricsFunction : ProcessWindowFunction<PaymentEvent, PaymentMetrics, String, TimeWindow>() {
  override fun process(
    key: String,
    context: Context,
    elements: Iterable<PaymentEvent>,
    out: Collector<PaymentMetrics>,
  ) {
    var count = 0L
    var total = 0.0
    for (e in elements) {
      count++
      total += e.amount
    }
    out.collect(
      PaymentMetrics(
        windowStartEpochMs = context.window().start,
        windowEndEpochMs = context.window().end,
        currency = key,
        count = count,
        totalAmount = total,
      ),
    )
  }
}
