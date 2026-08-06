package com.paypulse.projection.metrics

import com.paypulse.projection.adapter.persistence.AccountBalanceRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

interface BalanceProjectionLagMonitor {
  fun refresh()
}

@Component
class DefaultBalanceProjectionLagMonitor(
  private val accountBalanceRepository: AccountBalanceRepository,
  meterRegistry: MeterRegistry,
) : BalanceProjectionLagMonitor {
  private val lagSeconds = AtomicReference(0.0)

  init {
    Gauge.builder("paypulse_balance_projection_lag_seconds") { lagSeconds.get() }
      .register(meterRegistry)
  }

  @Scheduled(fixedDelayString = "\${paypulse.metrics.projection-lag-interval-ms:30000}")
  override fun refresh() {
    val lastOccurredAt = accountBalanceRepository.findMaxLastOccurredAt().block()
    val lag = if (lastOccurredAt == null) {
      0.0
    } else {
      Duration.between(lastOccurredAt, OffsetDateTime.now()).seconds.toDouble().coerceAtLeast(0.0)
    }
    lagSeconds.set(lag)
  }
}
