package com.paypulse.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "paypulse.jwt")
data class JwtProperties(
  val secret: String,
  val accessExpiration: Duration = Duration.ofMinutes(15),
  val refreshExpiration: Duration = Duration.ofDays(30),
  val issuer: String = "paypulse-auth-gateway",
)
