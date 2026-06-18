package com.paypulse.gateway.auth

import com.paypulse.gateway.auth.entity.RefreshToken
import com.paypulse.gateway.auth.entity.RefreshTokenStatus
import com.paypulse.gateway.auth.entity.User
import com.paypulse.gateway.auth.repository.RefreshTokenRepository
import com.paypulse.gateway.auth.repository.UserRepository
import com.paypulse.gateway.config.JwtProperties
import io.jsonwebtoken.Claims
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class AuthService(
  private val userRepository: UserRepository,
  private val refreshTokenRepository: RefreshTokenRepository,
  private val passwordEncoder: PasswordEncoder,
  private val jwtService: JwtService,
  private val jwtProperties: JwtProperties,
  private val tx: TransactionalOperator,
  private val tokenBlacklist: ObjectProvider<TokenBlacklistService>,
) {

  private val log = LoggerFactory.getLogger(AuthService::class.java)

  fun register(username: String, password: String, email: String): Mono<User> =
    userRepository.existsByUsername(username)
      .flatMap { exists ->
        if (exists) Mono.error(UsernameAlreadyTakenException(username))
        else userRepository.existsByEmail(email)
      }
      .flatMap { existsEmail ->
        if (existsEmail) Mono.error(EmailAlreadyTakenException(email))
        else userRepository.save(
          User(
            username = username,
            password = passwordEncoder.encode(password),
            email = email,
            roles = "OPS",
          ),
        )
      }
      .`as`(tx::transactional)

  fun login(username: String, password: String, fingerprint: String): Mono<TokenPair> =
    userRepository.findByUsername(username)
      .switchIfEmpty(Mono.error(InvalidCredentialsException()))
      .flatMap { user ->
        if (!passwordEncoder.matches(password, user.password)) {
          Mono.error(InvalidCredentialsException())
        } else {
          val family = UUID.randomUUID().toString()
          issueTokens(user, family, fingerprint)
        }
      }

  fun refresh(refreshTokenValue: String, fingerprint: String): Mono<TokenPair> =
    refreshTokenRepository.findByToken(refreshTokenValue)
      .switchIfEmpty(Mono.error(InvalidRefreshTokenException("token not found")))
      .flatMap { rt ->
        when {
          rt.expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC)) -> {
            Mono.error(InvalidRefreshTokenException("expired"))
          }

          rt.status == RefreshTokenStatus.REVOKED.name -> {
            log.warn("Refresh attempted on revoked token; user_id={}", rt.userId)
            Mono.error(InvalidRefreshTokenException("revoked"))
          }

          rt.status == RefreshTokenStatus.USED.name -> {
            log.warn(
              "Refresh attempted on already-used token; revoking entire family user_id={} family={}",
              rt.userId,
              rt.family,
            )
            refreshTokenRepository.revokeFamily(rt.userId, rt.family)
              .then(Mono.error(InvalidRefreshTokenException("token reuse detected")))
          }

          rt.fingerprint != fingerprint -> {
            log.warn(
              "Fingerprint mismatch on refresh; revoking family user_id={} family={}",
              rt.userId,
              rt.family,
            )
            refreshTokenRepository.revokeFamily(rt.userId, rt.family)
              .then(Mono.error(InvalidRefreshTokenException("fingerprint mismatch")))
          }

          else -> {
            userRepository.findById(rt.userId)
              .switchIfEmpty(Mono.error(InvalidRefreshTokenException("user gone")))
              .flatMap { user ->
                refreshTokenRepository
                  .updateStatus(rt.id!!, RefreshTokenStatus.USED.name)
                  .then(issueTokens(user, rt.family, fingerprint))
              }
              .`as`(tx::transactional)
          }
        }
      }

  fun logout(refreshTokenValue: String): Mono<Void> =
    refreshTokenRepository.findByToken(refreshTokenValue)
      .flatMap { rt ->
        refreshTokenRepository.updateStatus(rt.id!!, RefreshTokenStatus.REVOKED.name)
      }
      .then()

  fun logoutAll(accessToken: String): Mono<Void> =
    Mono.fromCallable { jwtService.parse(accessToken) }
      .flatMap { claims ->
        val userId = claims.subject.toLong()
        val jti = claims.id ?: claims["jti"]?.toString()
        val exp = claims.expiration
        refreshTokenRepository.revokeAllForUser(userId).flatMap {
          val bl = tokenBlacklist.ifAvailable
          if (bl != null && jti != null && exp != null) {
            val ttlMs = exp.time - System.currentTimeMillis()
            if (ttlMs > 0) bl.block(jti, Duration.ofMillis(ttlMs)) else Mono.just(false)
          } else {
            Mono.just(false)
          }
        }
      }
      .then()

  fun validate(accessToken: String): Mono<Claims> =
    Mono.fromCallable { jwtService.parse(accessToken) }
      .flatMap { claims ->
        val jti = claims.id ?: claims["jti"]?.toString()
        val bl = tokenBlacklist.ifAvailable
        if (jti == null || bl == null) Mono.just(claims)
        else bl.isBlocked(jti).flatMap { blocked ->
          if (blocked) Mono.error(IllegalArgumentException("Token is blacklisted"))
          else Mono.just(claims)
        }
      }

  private fun issueTokens(user: User, family: String, fingerprint: String): Mono<TokenPair> {
    val roles = user.roles.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val accessToken = jwtService.issueAccessToken(user.id!!, user.username, roles)
    val refreshTokenValue = jwtService.newRefreshToken()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val rt = RefreshToken(
      token = refreshTokenValue,
      userId = user.id,
      fingerprint = fingerprint,
      family = family,
      status = RefreshTokenStatus.ACTIVE.name,
      expiresAt = now.plus(jwtProperties.refreshExpiration),
    )
    return refreshTokenRepository.save(rt).map {
      TokenPair(
        accessToken = accessToken,
        refreshToken = refreshTokenValue,
        expiresIn = jwtProperties.accessExpiration.seconds,
      )
    }
  }
}

class UsernameAlreadyTakenException(username: String) : RuntimeException("Username '$username' already taken")
class EmailAlreadyTakenException(email: String) : RuntimeException("Email '$email' already registered")
class InvalidCredentialsException : RuntimeException("Invalid credentials")
class InvalidRefreshTokenException(reason: String) : RuntimeException("Invalid refresh token: $reason")
