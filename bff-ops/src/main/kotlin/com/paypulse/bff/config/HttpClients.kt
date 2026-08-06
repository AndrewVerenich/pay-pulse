package com.paypulse.bff.config

import com.paypulse.bff.properties.BffProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class HttpClients(private val properties: BffProperties) {

  @Bean
  fun paymentCommandClient(builder: WebClient.Builder): WebClient =
    builder.baseUrl(properties.paymentCommandUri).build()

  @Bean
  fun accountQueryClient(builder: WebClient.Builder): WebClient =
    builder.baseUrl(properties.accountQueryUri).build()

  @Bean
  fun sagaOrchestratorClient(builder: WebClient.Builder): WebClient =
    builder.baseUrl(properties.sagaOrchestratorUri).build()
}
