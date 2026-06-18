package com.paypulse.gateway.config

import com.paypulse.gateway.auth.TokenBlacklistService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityBeansConfiguration {

  @Bean
  fun reactiveJwtDecoder(
    properties: JwtProperties,
    blacklist: ObjectProvider<TokenBlacklistService>,
  ): ReactiveJwtDecoder {
    val key = SecretKeySpec(properties.secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    val nimbus = NimbusReactiveJwtDecoder.withSecretKey(key)
      .macAlgorithm(MacAlgorithm.HS256)
      .build()
    val bl = blacklist.ifAvailable ?: return nimbus
    return BlacklistingReactiveJwtDecoder(nimbus, bl)
  }
}
