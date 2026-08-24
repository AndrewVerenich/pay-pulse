pluginManagement {
  plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("io.gatling.gradle") version "3.13.5"
  }
}

rootProject.name = "pay-pulse"

include(
  ":shared:common-model",
  ":shared:metrics-starter",
  ":shared:outbox-publisher-starter",
  ":shared:saga-model",
  ":shared:saga-orchestrator-engine",
  ":shared:saga-participant-starter",
  ":payment-command-service",
  ":account-query-service",
  ":projection-balance",
  ":auth-gateway",
  ":payment-generator",
  ":payment-saga-orchestrator",
  ":participant-fraud-check",
  ":participant-risk-scoring",
  ":participant-ledger-apply",
  ":participant-notification",
  ":bff-ops",
  ":flink-payment-intelligence",
  ":rule-management-service",
  ":kstreams-saga-events-agg",
  ":ops-ui-server",
  ":load-test",
)
