package com.paypulse.bff.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.bff.health")
data class HealthProbeProperties(
  val probes: List<HealthProbeTarget> = emptyList(),
)

data class HealthProbeTarget(
  val id: String,
  val name: String,
  val url: String,
  val link: String? = null,
)
