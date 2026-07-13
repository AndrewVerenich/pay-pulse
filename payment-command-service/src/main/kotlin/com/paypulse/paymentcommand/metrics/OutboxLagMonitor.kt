package com.paypulse.paymentcommand.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

private const val QUERY = """
      SELECT COALESCE(
        EXTRACT(EPOCH FROM (timezone('utc', now()) - max(created_at))),
        0
      ) AS lag_seconds
      FROM payment_command.outbox
      """

@Component
class OutboxLagMonitor(
  private val databaseClient: DatabaseClient,
  meterRegistry: MeterRegistry,
) {
  private val lagSeconds = AtomicReference(0.0)

  init {
    Gauge.builder("paypulse_outbox_lag_seconds") { lagSeconds.get() }
      .tag("schema", "payment_command")
      .register(meterRegistry)
  }

  @Scheduled(fixedDelayString = "\${paypulse.metrics.outbox-lag-interval-ms:30000}")
  fun refresh() {
    databaseClient.sql(QUERY)
      .map { row, _ -> row.get("lag_seconds", java.lang.Double::class.java)?.toDouble() ?: 0.0 }
      .one()
      .doOnNext { lagSeconds.set(it) }
      .subscribe()
  }
}
