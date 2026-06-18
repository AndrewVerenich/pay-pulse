package com.paypulse.gateway.route

import com.paypulse.gateway.config.GatewayRoutingProperties
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GatewayRouteConfiguration {

  @Bean
  fun payPulseRoutes(builder: RouteLocatorBuilder, props: GatewayRoutingProperties): RouteLocator =
    builder.routes()
      .route("account-query") { spec ->
        spec.path("/api/v1/accounts/**")
          .uri(props.accountQueryUri)
      }
      .route("payment-command") { spec ->
        spec.path("/api/v1/payments/**")
          .uri(props.paymentCommandUri)
      }
      .route("saga-orchestrator") { spec ->
        spec.path("/api/v1/sagas/**", "/api/live/sagas/**")
          .uri(props.sagaOrchestratorUri)
      }
      .build()
}
