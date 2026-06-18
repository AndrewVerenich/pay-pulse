package com.paypulse.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Flux

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration {

  @Bean
  fun springSecurityFilterChain(
    http: ServerHttpSecurity,
    jwtAuthenticationConverter: ReactiveJwtAuthenticationConverter,
  ): SecurityWebFilterChain =
    http
      .csrf { it.disable() }
      .authorizeExchange {
        it.pathMatchers("/auth/**", "/actuator/**").permitAll()
          .anyExchange().authenticated()
      }
      .oauth2ResourceServer { oauth2 ->
        oauth2.jwt { jwt ->
          jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        }
      }
      .build()

  @Bean
  fun jwtAuthenticationConverter(): ReactiveJwtAuthenticationConverter {
    val converter = ReactiveJwtAuthenticationConverter()
    converter.setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
      val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
      Flux.fromIterable(roles.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") })
    }
    return converter
  }
}
