package com.paypulse.gateway.auth

import com.paypulse.gateway.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Jwts.SIG
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
  private val properties: JwtProperties,
) {

  private val secretKey: SecretKey =
    Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))

  fun issueAccessToken(userId: Long, username: String, roles: List<String>): String {
    val now = Instant.now()
    val exp = now.plusMillis(properties.accessExpiration.toMillis())
    return Jwts.builder()
      .id(UUID.randomUUID().toString())
      .issuer(properties.issuer)
      .subject(userId.toString())
      .claim("username", username)
      .claim("roles", roles)
      .claim("type", "access")
      .issuedAt(Date.from(now))
      .expiration(Date.from(exp))
      .signWith(secretKey, SIG.HS256)
      .compact()
  }

  fun parse(token: String): Claims =
    Jwts.parser()
      .verifyWith(secretKey)
      .build()
      .parseSignedClaims(token)
      .payload

  fun newRefreshToken(): String = UUID.randomUUID().toString().replace("-", "") +
    UUID.randomUUID().toString().replace("-", "")
}
