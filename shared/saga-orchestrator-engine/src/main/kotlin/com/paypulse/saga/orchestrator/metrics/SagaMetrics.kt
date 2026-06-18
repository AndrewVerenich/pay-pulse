package com.paypulse.saga.orchestrator.metrics

import com.paypulse.saga.model.SagaStatus
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@Component
class SagaMetrics(
  private val registry: MeterRegistry
) {
  private val activeGauges = mutableMapOf<String, AtomicInteger>()
  private val compensatingGauges = mutableMapOf<String, AtomicInteger>()

  fun recordSagaStarted(sagaType: String) {
    Counter.builder("saga.started.total")
      .tag("saga_type", sagaType)
      .register(registry)
      .increment()

    getActiveGauge(sagaType).incrementAndGet()
  }

  fun recordSagaCompleted(sagaType: String, status: SagaStatus) {
    val outcome = when (status) {
      SagaStatus.COMPLETED -> "completed"
      SagaStatus.COMPENSATED -> "compensated"
      SagaStatus.FAILED -> "failed"
      else -> "unknown"
    }

    Counter.builder("saga.completed.total")
      .tag("saga_type", sagaType)
      .tag("outcome", outcome)
      .register(registry)
      .increment()

    getActiveGauge(sagaType).decrementAndGet()
    if (status == SagaStatus.COMPENSATED) {
      getCompensatingGauge(sagaType).decrementAndGet()
    }
  }

  fun recordCompensationTriggered(sagaType: String) {
    getCompensatingGauge(sagaType).incrementAndGet()
  }

  fun recordStepDuration(sagaType: String, step: SagaStepEntity) {
    if (step.startedAt != null && step.completedAt != null) {
      val duration = Duration.between(step.startedAt, step.completedAt ?: LocalDateTime.now())
      Timer.builder("saga.step.duration.seconds")
        .tag("saga_type", sagaType)
        .tag("step_name", step.stepName)
        .tag("step_type", step.stepType)
        .register(registry)
        .record(duration)
    }
  }

  fun recordStepRetry(sagaType: String, stepName: String) {
    Counter.builder("saga.step.retries.total")
      .tag("saga_type", sagaType)
      .tag("step_name", stepName)
      .register(registry)
      .increment()
  }

  fun recordStepFailure(sagaType: String, stepName: String) {
    Counter.builder("saga.step.failures.total")
      .tag("saga_type", sagaType)
      .tag("step_name", stepName)
      .register(registry)
      .increment()
  }

  private fun getActiveGauge(sagaType: String): AtomicInteger {
    return activeGauges.computeIfAbsent(sagaType) { type ->
      val gauge = AtomicInteger(0)
      registry.gauge("saga.active.count", listOf(io.micrometer.core.instrument.Tag.of("saga_type", type)), gauge)
      gauge
    }
  }

  private fun getCompensatingGauge(sagaType: String): AtomicInteger {
    return compensatingGauges.computeIfAbsent(sagaType) { type ->
      val gauge = AtomicInteger(0)
      registry.gauge("saga.compensating.count", listOf(io.micrometer.core.instrument.Tag.of("saga_type", type)), gauge)
      gauge
    }
  }
}
