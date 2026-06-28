package com.paypulse.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.gateway")
data class GatewayRoutingProperties(
  val paymentCommandUri: String = "http://localhost:8080",
  val accountQueryUri: String = "http://localhost:8082",
  val sagaOrchestratorUri: String = "http://localhost:8083",
  val bffOpsUri: String = "http://localhost:8084",
  val ruleManagementUri: String = "http://localhost:8085",
  val paymentGeneratorUri: String = "http://localhost:8097",
)
