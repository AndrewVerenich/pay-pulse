package com.paypulse.accountquery

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
class BalanceApiIntegrationTest {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:15-alpine")
        .withInitScript("schema-account-query.sql")
        .withDatabaseName("paypulse")
        .withUsername("postgres")
        .withPassword("postgres")
  }

  @Autowired
  private lateinit var webClient: WebTestClient

  @Test
  fun `current balance`() {
    webClient.get()
      .uri("/api/v1/accounts/acc-test/balance?currency=USD")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(15)
      .jsonPath("$.currency").isEqualTo("USD")
      .jsonPath("$.accountId").isEqualTo("acc-test")
  }

  @Test
  fun `balance at timestamp returns latest row before at`() {
    webClient.get()
      .uri("/api/v1/accounts/acc-test/balance?currency=USD&at=2026-01-10T12:00:00Z")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(10)
  }
}
