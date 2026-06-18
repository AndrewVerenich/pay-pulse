package com.paypulse.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.gateway")
data class GatewayRoutingProperties(
  val paymentCommandUri: String = "http://localhost:8080",
  val accountQueryUri: String = "http://localhost:8082",
  val sagaOrchestratorUri: String = "http://localhost:8083",
)
