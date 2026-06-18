pluginManagement {
  plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
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
)
