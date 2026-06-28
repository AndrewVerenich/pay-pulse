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
        spec.path("/api/v1/sagas/**")
          .uri(props.sagaOrchestratorUri)
      }
      .route("rule-management") { spec ->
        spec.path("/api/v1/fraud-rules/**")
          .uri(props.ruleManagementUri)
      }
      .route("payment-generator") { spec ->
        spec.path("/api/generator/**")
          .filters { f ->
            f.rewritePath("/api/generator/(?<remaining>.*)", "/generator/\${remaining}")
          }
          .uri(props.paymentGeneratorUri)
      }
      .route("bff-ops-health") { spec ->
        spec.path("/api/health/**")
          .uri(props.bffOpsUri)
      }
      .route("bff-ops-live") { spec ->
        spec.path("/api/live/payments/**", "/api/live/sagas/**", "/api/live/rules/**", "/api/live/alerts/**")
          .uri(props.bffOpsUri)
      }
      .route("bff-ops-aggregator") { spec ->
        spec.path("/api/payments/recent", "/api/payments/*/full", "/api/sagas/**")
          .uri(props.bffOpsUri)
      }
      .build()
}
