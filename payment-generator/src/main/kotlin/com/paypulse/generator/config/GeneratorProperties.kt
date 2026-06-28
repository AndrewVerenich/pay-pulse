package com.paypulse.generator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.generator")
data class GeneratorProperties(
  val enabled: Boolean = true,
  val scenariosEnabled: Boolean = true,
  val baseUrl: String = "http://localhost:8090",
  val accessToken: String = "",
  val loginUsername: String = "admin",
  val loginPassword: String = "admin",
  val intervalMs: Long = 3000L,
)
