package com.paypulse.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(MeterRegistry::class)
class MetricsStarterAutoConfiguration {

  @Bean
  fun payPulseCommonTags(
    @Value("\${spring.application.name:unknown}") applicationName: String,
  ): MeterRegistryCustomizer<MeterRegistry> = MeterRegistryCustomizer { registry ->
    registry.config().commonTags(
      "service",
      applicationName,
      "env",
      System.getenv().getOrDefault("PAYPULSE_ENV", "local"),
    )
  }
}
