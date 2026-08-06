package com.paypulse.bff.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Flux
import javax.crypto.spec.SecretKeySpec

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
        it.pathMatchers("/actuator/**", "/api/health/**").permitAll()
          .anyExchange().authenticated()
      }
      .oauth2ResourceServer { oauth2 ->
        oauth2.jwt { jwt ->
          jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        }
      }
      .build()

  @Bean
  fun reactiveJwtDecoder(@Value("\${paypulse.jwt.secret}") secret: String): ReactiveJwtDecoder {
    val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    return NimbusReactiveJwtDecoder.withSecretKey(key)
      .macAlgorithm(MacAlgorithm.HS256)
      .build()
  }

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
