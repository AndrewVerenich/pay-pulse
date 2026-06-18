package com.paypulse.gateway.auth

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
class AuthController(
  private val authService: AuthService,
) {
  private val log = LoggerFactory.getLogger(AuthController::class.java)

  @PostMapping("/register", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun register(@RequestBody @Valid request: RegisterRequest): Mono<ResponseEntity<MessageResponse>> =
    authService.register(request.username, request.password, request.email)
      .map {
        ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse("user registered"))
      }
      .onErrorResume(UsernameAlreadyTakenException::class.java) {
        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(MessageResponse(it.message ?: "conflict")))
      }
      .onErrorResume(EmailAlreadyTakenException::class.java) {
        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(MessageResponse(it.message ?: "conflict")))
      }

  @PostMapping("/login", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun login(
    @RequestBody @Valid request: LoginRequest,
    exchange: ServerWebExchange,
  ): Mono<ResponseEntity<TokenPair>> =
    authService.login(request.username, request.password, fingerprintOf(exchange.request))
      .map { ResponseEntity.ok(it) }
      .onErrorResume(InvalidCredentialsException::class.java) {
        log.info("Login failed for user='{}'", request.username)
        Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"))
      }

  @PostMapping("/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun refresh(
    @RequestBody @Valid request: RefreshRequest,
    exchange: ServerWebExchange,
  ): Mono<ResponseEntity<TokenPair>> =
    authService.refresh(request.refreshToken, fingerprintOf(exchange.request))
      .map { ResponseEntity.ok(it) }
      .onErrorResume(InvalidRefreshTokenException::class.java) {
        log.info("Refresh rejected: {}", it.message)
        Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, it.message))
      }

  @PostMapping("/logout", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun logout(@RequestBody @Valid request: LogoutRequest): Mono<ResponseEntity<MessageResponse>> =
    authService.logout(request.refreshToken)
      .thenReturn(ResponseEntity.ok(MessageResponse("logged out")))

  @PostMapping("/logout-all")
  fun logoutAll(@RequestHeader("Authorization") authHeader: String): Mono<ResponseEntity<MessageResponse>> {
    val token = authHeader.removePrefix("Bearer ").trim()
    return authService.logoutAll(token)
      .thenReturn(ResponseEntity.ok(MessageResponse("logged out from all devices")))
      .onErrorResume {
        Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"))
      }
  }

  @PostMapping("/validate")
  fun validate(@RequestHeader("Authorization") authHeader: String): Mono<ResponseEntity<ValidateResponse>> {
    val token = authHeader.removePrefix("Bearer ").trim()
    return authService.validate(token)
      .map { claims ->
        @Suppress("UNCHECKED_CAST")
        val roles = (claims["roles"] as? List<String>) ?: emptyList()
        ResponseEntity.ok(
          ValidateResponse(
            valid = true,
            userId = claims.subject?.toLongOrNull(),
            username = claims["username"] as? String,
            roles = roles,
          ),
        )
      }
      .onErrorResume {
        Mono.just(ResponseEntity.ok(ValidateResponse(valid = false)))
      }
  }

  private fun fingerprintOf(request: ServerHttpRequest): String {
    val provided = request.headers.getFirst("X-Client-Fingerprint")
    if (!provided.isNullOrBlank()) return provided
    val ua = request.headers.getFirst("User-Agent") ?: "unknown-ua"
    val ip = request.remoteAddress?.address?.hostAddress ?: "unknown-ip"
    return "$ua|$ip"
  }
}
