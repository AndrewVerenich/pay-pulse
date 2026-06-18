package com.paypulse.gateway.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
  @field:NotBlank
  @field:Size(min = 3, max = 64)
  val username: String,
  @field:NotBlank
  @field:Size(min = 6, max = 100)
  val password: String,
  @field:NotBlank
  @field:Email
  val email: String,
)

data class LoginRequest(
  @field:NotBlank val username: String,
  @field:NotBlank val password: String,
)

data class RefreshRequest(
  @field:NotBlank val refreshToken: String,
)

data class LogoutRequest(
  @field:NotBlank val refreshToken: String,
)

data class TokenPair(
  val accessToken: String,
  val refreshToken: String,
  val tokenType: String = "Bearer",
  val expiresIn: Long,
)

data class MessageResponse(
  val message: String,
)

data class ValidateResponse(
  val valid: Boolean,
  val userId: Long? = null,
  val username: String? = null,
  val roles: List<String>? = null,
)
