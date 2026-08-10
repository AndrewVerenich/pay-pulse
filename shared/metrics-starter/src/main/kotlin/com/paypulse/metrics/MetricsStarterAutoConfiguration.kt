package com.paypulse.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(MeterRegistry::class)
@EnableConfigurationProperties(MetricsProperties::class)
class MetricsStarterAutoConfiguration {

  @Bean
  fun payPulseCommonTags(
    @Value("\${spring.application.name:unknown}") applicationName: String,
    properties: MetricsProperties,
  ): MeterRegistryCustomizer<MeterRegistry> = MeterRegistryCustomizer { registry ->
    registry.config().commonTags(
      "service",
      applicationName,
      "env",
      System.getenv().getOrDefault("PAYPULSE_ENV", "local"),
      "version",
      properties.version,
    )
    properties.tags.forEach { (key, value) ->
      registry.config().commonTags(key, value)
    }
  }

  @Bean
  @ConditionalOnMissingBean
  fun jvmMemoryMetrics(): JvmMemoryMetrics = JvmMemoryMetrics()

  @Bean
  @ConditionalOnMissingBean
  fun jvmGcMetrics(): JvmGcMetrics = JvmGcMetrics()

  @Bean
  @ConditionalOnMissingBean
  fun processorMetrics(): ProcessorMetrics = ProcessorMetrics()

  @Bean
  @ConditionalOnMissingBean
  fun uptimeMetrics(): UptimeMetrics = UptimeMetrics()
}
