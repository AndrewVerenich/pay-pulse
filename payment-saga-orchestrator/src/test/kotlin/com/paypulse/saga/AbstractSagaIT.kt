package com.paypulse.saga

import com.paypulse.saga.model.SagaStatus
import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.repository.SagaInstanceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

/**
 * Общая инфраструктура для интеграционных тестов саги: Postgres со схемой `saga`
 * и Kafka. Конкретные тесты помечаются `@Testcontainers(disabledWithoutDocker = true)`,
 * поэтому без Docker они пропускаются, а не падают.
 */
abstract class AbstractSagaIT {

  @Autowired
  protected lateinit var instanceRepository: SagaInstanceRepository

  companion object {
    @Container
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:15-alpine")
        .withInitScript("schema-saga.sql")
        .withDatabaseName("paypulse")
        .withUsername("postgres")
        .withPassword("postgres")

    @Container
    @JvmStatic
    val kafka: KafkaContainer =
      KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    @DynamicPropertySource
    @JvmStatic
    fun properties(registry: DynamicPropertyRegistry) {
      registry.add("spring.r2dbc.url") {
        "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/paypulse"
      }
      registry.add("spring.r2dbc.username", postgres::getUsername)
      registry.add("spring.r2dbc.password", postgres::getPassword)
      registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
      // Отключаем timeout-scheduler на время тестов, чтобы он не «дофейливал» шаги.
      registry.add("saga.timeout.check-seconds") { "3600" }
      registry.add("saga.timeout.check-interval-ms") { "600000" }
    }
  }

  protected fun awaitTerminal(
    sagaId: java.util.UUID,
    timeout: Duration = Duration.ofSeconds(30),
  ): SagaInstanceEntity {
    val deadline = Instant.now().plus(timeout)
    var last: SagaInstanceEntity? = null
    while (Instant.now().isBefore(deadline)) {
      last = instanceRepository.findBySagaId(sagaId).block()
      val status = last?.status
      if (status == SagaStatus.COMPLETED.name ||
        status == SagaStatus.COMPENSATED.name ||
        status == SagaStatus.FAILED.name
      ) {
        return last!!
      }
      Thread.sleep(300)
    }
    throw AssertionError("Saga $sagaId did not reach terminal state within $timeout, last=$last")
  }
}
