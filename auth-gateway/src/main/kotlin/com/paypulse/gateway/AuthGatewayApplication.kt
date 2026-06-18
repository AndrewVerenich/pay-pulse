package com.paypulse.gateway

import com.paypulse.gateway.config.GatewayRoutingProperties
import com.paypulse.gateway.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, GatewayRoutingProperties::class)
class AuthGatewayApplication

fun main(args: Array<String>) {
  runApplication<AuthGatewayApplication>(*args)
}
