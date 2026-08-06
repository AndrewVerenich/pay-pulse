package com.paypulse.bff.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.bff")
data class BffProperties(
  val paymentCommandUri: String = "http://localhost:8086",
  val accountQueryUri: String = "http://localhost:8082",
  val sagaOrchestratorUri: String = "http://localhost:8083",
  val paymentEventsTopic: String = "payment.events",
  val sagaEventsTopic: String = "saga.events",
  val fraudRulesTopic: String = "fraud_rules",
  val fraudAlertsTopic: String = "fraud_alerts",
  val recentBufferSize: Int = 200,
)
