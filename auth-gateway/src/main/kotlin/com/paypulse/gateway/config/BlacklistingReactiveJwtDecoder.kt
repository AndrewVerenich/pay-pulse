package com.paypulse.gateway.config

import com.paypulse.gateway.auth.TokenBlacklistService
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono

/**
 * Wraps the delegate decoder and rejects JWTs whose `jti` was stored in Redis by logout-all.
 */
class BlacklistingReactiveJwtDecoder(
  private val delegate: ReactiveJwtDecoder,
  private val blacklist: TokenBlacklistService,
) : ReactiveJwtDecoder {

  override fun decode(token: String): Mono<Jwt> =
    delegate.decode(token).flatMap { jwt ->
      val jti = jwt.id ?: return@flatMap Mono.just(jwt)
      blacklist.isBlocked(jti).flatMap { blocked ->
        if (blocked) {
          Mono.error(BadJwtException("Token revoked"))
        } else {
          Mono.just(jwt)
        }
      }
    }
}
