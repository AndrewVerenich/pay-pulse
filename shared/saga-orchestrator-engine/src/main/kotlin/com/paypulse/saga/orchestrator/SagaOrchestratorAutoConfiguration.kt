package com.paypulse.saga.orchestrator

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@AutoConfiguration
@ComponentScan(basePackages = ["com.paypulse.saga.orchestrator"])
@EnableR2dbcRepositories(basePackages = ["com.paypulse.saga.orchestrator.repository"])
@EnableScheduling
@EnableKafka
class SagaOrchestratorAutoConfiguration
