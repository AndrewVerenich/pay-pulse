package com.paypulse.flink.io

import com.paypulse.flink.model.DeadLetter
import com.paypulse.flink.model.PaymentEvent
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector

/**
 * Парсит сырой JSON `payment.events` в [PaymentEvent]. Некорректные записи отправляет
 * в dead-letter side output ([DEAD_LETTER_TAG]) вместо падения job'ы.
 */
class EventParser : ProcessFunction<String, PaymentEvent>() {

  override fun processElement(
    value: String,
    ctx: Context,
    out: Collector<PaymentEvent>,
  ) {
    try {
      out.collect(PaymentJson.parsePayment(value))
    } catch (e: Exception) {
      ctx.output(
        DEAD_LETTER_TAG,
        DeadLetter(
          raw = value.take(2000),
          error = e.message ?: e.javaClass.simpleName,
          occurredAtEpochMs = System.currentTimeMillis(),
        ),
      )
    }
  }
}
