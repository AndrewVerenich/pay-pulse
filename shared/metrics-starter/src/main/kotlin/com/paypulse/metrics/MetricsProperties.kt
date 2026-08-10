package com.paypulse.metrics

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.metrics")
data class MetricsProperties(
  val version: String = "0.1.0",
  val tags: Map<String, String> = emptyMap(),
)
