package com.paypulse.accountquery.config

import com.paypulse.accountquery.adapter.`in`.BalanceQueryHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RequestPredicates.GET
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse

@Configuration
class RouterConfiguration {

  @Bean
  fun accountRoutes(handler: BalanceQueryHandler): RouterFunction<ServerResponse> =
    route(
      GET("/api/v1/accounts/{accountId}/balance"),
      handler::getBalance,
    )
}
