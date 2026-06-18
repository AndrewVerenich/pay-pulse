package com.paypulse.gateway.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration

@Service
@ConditionalOnProperty(prefix = "paypulse.auth", name = ["token-blacklist-enabled"], havingValue = "true")
class TokenBlacklistService(
  private val redis: ReactiveStringRedisTemplate,
) {
  private val log = LoggerFactory.getLogger(TokenBlacklistService::class.java)

  fun block(jti: String, ttl: Duration): Mono<Boolean> {
    if (ttl.isZero || ttl.isNegative) return Mono.just(false)
    return redis.opsForValue()
      .set("$BLACKLIST_PREFIX$jti", "1", ttl)
      .doOnSuccess { ok -> log.debug("Blacklist set jti={} ttl={} ok={}", jti, ttl, ok) }
  }

  fun isBlocked(jti: String): Mono<Boolean> =
    redis.hasKey("$BLACKLIST_PREFIX$jti").defaultIfEmpty(false)

  private companion object {
    const val BLACKLIST_PREFIX = "blacklist:"
  }
}
